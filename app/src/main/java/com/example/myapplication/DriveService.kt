package com.example.myapplication

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 주행 중 화면·소리 없이 데이터만 모은다. 기어가 P를 벗어나면 주행을 시작하고, P로 돌아오면 요약을 서버에 보낸다.
 * 원본 위치는 요약에 실린 일부 지점만 남기고 버린다.
 */
class DriveService : Service() {
    private val thread = HandlerThread("drive").apply { start() }
    private val worker = Handler(thread.looper)
    private val recorder = TripRecorder()
    private var vehicle: Vehicle? = null
    private var fused: FusedLocation? = null
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var started = false

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) { worker.post { onGear(gear) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "gear listener failed", e) }
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
            val now = System.currentTimeMillis()
            worker.post { recorder.onBrake(pressed == true, now) }
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "brake listener failed", e) }
    }
    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            worker.post { recorder.onLocation(location.latitude, location.longitude) }
        }
        override fun onError(exception: Exception) { Log.w(TAG, "location failed", exception) }
    }
    private val speedPoll = object : Runnable {
        override fun run() {
            if (!recorder.active) return
            val mps = runCatching { properties?.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0) }.getOrNull()
            if (mps != null) recorder.onSpeed(kotlin.math.abs(mps) * 3.6, System.currentTimeMillis())
            worker.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_MIN))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("붕붕이가 주행을 기록하고 있어요").build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (!started) {
            started = true
            worker.post { connect() }
        } else if (intent?.action == ACTION_RECONNECT) {
            worker.post { connectLocation() }
        }
        return START_STICKY
    }

    private fun connect() {
        val v = Vehicle(this).also { it.initialize(); vehicle = it }
        runCatching { v.getGear().registerGearSelection(gearListener) }.onFailure { Log.w(TAG, "gear register", it) }
        runCatching { v.getBrake().registerBrakePedalStatus(brakeListener) }.onFailure { Log.w(TAG, "brake register", it) }
        runCatching {
            car = Car.createCar(this, worker, 0) { c, ready ->
                properties = if (ready) c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager else null
            }
        }.onFailure { Log.w(TAG, "car connect", it) }
        connectLocation()
        Log.i(TAG, "collector ready")
    }

    /** 위치 권한을 나중에 받으면 다시 부른다. */
    private fun connectLocation() {
        if (fused != null) return
        runCatching {
            FusedLocation(applicationContext).takeIf { it.isSupported() }?.also {
                it.initialize()
                it.registerFusedLocationCallback(locationListener)
                fused = it
            }
        }.onFailure { Log.w(TAG, "location register", it) }
    }

    private fun onGear(gear: VehicleGear?) {
        val now = System.currentTimeMillis()
        if (gear != null && gear != VehicleGear.GEAR_PARK && !recorder.active) {
            recorder.start(now)
            worker.post(speedPoll)
            Log.i(TAG, "trip started ($gear)")
        } else if (gear == VehicleGear.GEAR_PARK && recorder.active) {
            val summary = recorder.finish(now, countPassengers())
            Log.i(TAG, "trip finished: $summary")
            if (summary != null) send(summary)
        }
    }

    private fun send(summary: TripSummary, attempt: Int = 1) {
        runCatching { CarApi.postTripBlocking(summary) }
            .onSuccess { trip -> Log.i(TAG, "trip settled: $trip"); TripEvents.publish(this, trip) }
            .onFailure {
                Log.w(TAG, "trip upload failed (attempt $attempt)", it)
                if (attempt < 5) worker.postDelayed({ send(summary, attempt + 1) }, attempt * 10_000L)
            }
    }

    /**
     * 운전석을 뺀 동승자 수. 좌석 점유와 안전벨트가 둘 다 참인 자리만 센다
     * (에뮬레이터에서 빈 좌석도 점유로 읽혀, 점유만으로는 동승자 보너스가 잘못 붙었다). 읽지 못하면 0.
     */
    private fun countPassengers(): Int {
        val seats = vehicle?.getSeat() ?: return 0
        val areas = listOf(SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)
        val occupied = BooleanArray(areas.size)
        val buckled = BooleanArray(areas.size)
        val latch = CountDownLatch(areas.size * 2)
        areas.forEachIndexed { i, area ->
            runCatching {
                seats.getCurrentSeatOccupancy(SeatArea(area), { occupied[i] = it == true; latch.countDown() }, { latch.countDown() })
            }.onFailure { latch.countDown() }
            runCatching {
                seats.getCurrentSeatBeltBuckledState(SeatArea(area), { buckled[i] = it == true; latch.countDown() }, { latch.countDown() })
            }.onFailure { latch.countDown() }
        }
        latch.await(2, TimeUnit.SECONDS)
        Log.i(TAG, "seats occupied=${occupied.toList()} buckled=${buckled.toList()}")
        return areas.indices.count { occupied[it] && buckled[it] }
    }

    override fun onDestroy() {
        runCatching { vehicle?.getGear()?.unregisterGearSelection(gearListener) }
        runCatching { vehicle?.getBrake()?.unregisterBrakePedalStatus(brakeListener) }
        runCatching { vehicle?.release() }
        runCatching { fused?.unregisterFusedLocationCallback(locationListener); fused?.release() }
        runCatching { car?.disconnect() }
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BungDrive"
        private const val CHANNEL = "drive"
        private const val ACTION_RECONNECT = "reconnect"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, DriveService::class.java)) }
                .onFailure { Log.w(TAG, "could not start collector", it) }
        }

        /** 권한을 새로 받은 뒤 위치 수집을 다시 연결한다. */
        fun reconnect(context: Context) {
            runCatching { context.startForegroundService(Intent(context, DriveService::class.java).setAction(ACTION_RECONNECT)) }
                .onFailure { Log.w(TAG, "could not reconnect collector", it) }
        }
    }
}
