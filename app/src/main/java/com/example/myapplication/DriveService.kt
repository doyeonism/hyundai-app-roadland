package com.example.myapplication

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DrivingModeStateListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
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

/**
 * 주행 중 화면 조작 없이 데이터만 모은다. 정차(기어 P 또는 주차 브레이크)를 벗어나면 주행을 시작하고, 다시 정차하면
 * 타일 단위 요약을 큐에 넣고 서버로 보낸다. 원본 위치는 타일 ID로 바꾼 뒤 버린다.
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
    private var gearPark: Boolean? = null
    private var parkingBrake = false
    private var eco = false
    private var cruise = false
    /** 주행 시작 때 서버에서 받은 오늘 값. 위젯에 이번 주행 예상치를 더해 보여 준다. */
    private var base = Widget(0, 0, 100)

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) { worker.post { gear?.let { gearPark = it == VehicleGear.GEAR_PARK; evaluate() } } }
        override fun onFailed(e: Exception) { Log.w(TAG, "gear listener failed", e) }
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
            val now = System.currentTimeMillis()
            worker.post { recorder.onBrake(pressed == true, now); pushWidget() }
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "brake listener failed", e) }
    }
    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(info: Telltales?) { if (info != null) worker.post { onTelltales(info) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "telltale listener failed", e) }
    }
    private val modeListener = object : DrivingModeStateListener {
        override fun onDrivingModeStateUpdated(mode: DrivingModeType?) { worker.post { setAssist(mode == DrivingModeType.ECO, cruise) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "driving mode listener failed", e) }
    }
    private val cruiseListener = object : CruiseControlStateListener {
        override fun onCruiseControlStateUpdated(state: CruiseControl?) {
            worker.post { setAssist(eco, state == CruiseControl.ACTIVATED) }
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "cruise listener failed", e) }
    }
    private val signalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
            val now = System.currentTimeMillis()
            worker.post { recorder.onTurnSignal(value != null && value != TurnSignalValue.OFF, now) }
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "turn signal listener failed", e) }
    }
    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            worker.post { recorder.onLocation(location.latitude, location.longitude); pushWidget() }
        }
        override fun onError(exception: Exception) { Log.w(TAG, "location failed", exception) }
    }
    /**
     * 속도·주행 모드·크루즈는 1초, 핸들은 0.2초 간격으로 읽는다(주행 중에만).
     * 크루즈·주행 모드는 리스너도 있지만 에뮬레이터에서 주행 중 이벤트가 오지 않은 적이 있어 함께 읽는다.
     */
    private val poll = object : Runnable {
        private var tick = 0
        override fun run() {
            if (!recorder.active) return
            val now = System.currentTimeMillis()
            if (tick++ % 5 == 0) {
                val mps = runCatching { properties?.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0) }.getOrNull()
                if (mps != null) recorder.onSpeed(kotlin.math.abs(mps) * 3.6, now)
                runCatching {
                    vehicle?.getDrivingMode()?.getCruiseControlState({ s -> worker.post { setAssist(eco, s == CruiseControl.ACTIVATED) } }, { Log.w(TAG, "cruise read failed", it) })
                    vehicle?.getDrivingMode()?.getCurrentDrivingModeState({ m -> worker.post { setAssist(m == DrivingModeType.ECO, cruise) } }, {})
                }
                pushWidget()
            }
            runCatching {
                vehicle?.getSteeringWheel()?.getCurrentSteeringWheelAngle({ deg -> if (deg != null) worker.post { recorder.onSteering(deg.toDouble(), now) } }, {})
            }
            worker.postDelayed(this, 200)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_MIN))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("로드랜드가 주행 구간을 기록하고 있어요").build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (!started) {
            started = true
            worker.post { connect() }
        } else if (intent?.action == ACTION_RECONNECT) {
            worker.post { connectLocation() }
        }
        worker.post { TripQueue.flushBlocking(this) }
        return START_STICKY
    }

    private fun connect() {
        val v = Vehicle(this).also { it.initialize(); vehicle = it }
        fun attempt(what: String, block: () -> Unit) = runCatching(block).onFailure { Log.w(TAG, "$what unavailable", it) }
        attempt("gear") { v.getGear().registerGearSelection(gearListener) }
        attempt("brake") { v.getBrake().registerBrakePedalStatus(brakeListener) }
        attempt("telltales") { v.getSafety().registerTelltales(telltaleListener) }
        attempt("driving mode") { v.getDrivingMode().registerDrivingModeState(modeListener) }
        attempt("cruise") { v.getDrivingMode().registerCruiseControlState(cruiseListener) }
        attempt("turn signal") { v.getTurnSignal().registerTurnSignalState(signalListener) }
        attempt("car") {
            car = Car.createCar(this, worker, 0) { c, ready ->
                properties = if (ready) c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager else null
            }
        }
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

    private fun setAssist(eco: Boolean, cruise: Boolean) {
        if (eco != this.eco || cruise != this.cruise) Log.i(TAG, "assist eco=$eco cruise=$cruise")
        this.eco = eco
        this.cruise = cruise
        recorder.onAssist(eco || cruise)
    }

    private fun onTelltales(info: Telltales) {
        fun on(vararg types: TelltaleType) = info.telltales.any { it.type in types && it.state != TelltaleState.OFF }
        parkingBrake = on(TelltaleType.PARKING_BRAKE, TelltaleType.ELECTRONIC_PARKING_BRAKE)
        val warning = on(TelltaleType.MASTER_WARNING, TelltaleType.ABS, TelltaleType.ESC, TelltaleType.OVER_SPEED, TelltaleType.ICY_ROAD)
        recorder.onWarning(warning)
        Log.i(TAG, "telltales parkingBrake=$parkingBrake warning=$warning")
        evaluate()
        pushWidget()
    }

    /** 정차 여부가 바뀌면 주행을 시작하거나 끝낸다. 기어를 아직 모르면 아무것도 하지 않는다. */
    private fun evaluate() {
        val gp = gearPark ?: return
        val parked = Rules.isParked(gp, parkingBrake)
        val now = System.currentTimeMillis()
        if (!parked && !recorder.active) {
            recorder.start(now)
            base = Widget(0, 0, 100)
            runCatching { CarApi.homeBlocking() }.onSuccess { base = Widget(it.today.tilesPassed, it.credits, 100) }
            worker.removeCallbacks(poll)
            worker.post(poll)
            pushWidget()
            Log.i(TAG, "trip started")
        } else if (parked && recorder.active) {
            worker.removeCallbacks(poll)
            val summary = recorder.finish(now)
            TripEvents.publishWidget(null)
            Log.i(TAG, "trip finished: ${summary?.let { "km=${"%.2f".format(it.km)} tiles=${it.tiles.size} score=${it.safetyScore} brakes=${it.hardBrakes} warnings=${it.warnings} lanes=${it.signaledLaneChanges}/${it.laneChanges}" }}")
            if (summary != null) {
                TripQueue.add(this, CarApi.tripBody(summary))
                flushWithRetry()
            }
        }
    }

    private fun flushWithRetry(attempt: Int = 1) {
        TripQueue.flushBlocking(this)
        if (TripQueue.size(this) > 0 && attempt < 6) worker.postDelayed({ flushWithRetry(attempt + 1) }, attempt * 10_000L)
    }

    private fun pushWidget() {
        if (!recorder.active) return
        val live = recorder.live()
        TripEvents.publishWidget(Widget(base.todayTiles + live.tiles, base.credits + live.credits, live.safetyScore))
    }

    override fun onDestroy() {
        vehicle?.let { v ->
            runCatching { v.getGear().unregisterGearSelection(gearListener) }
            runCatching { v.getBrake().unregisterBrakePedalStatus(brakeListener) }
            runCatching { v.getSafety().unregisterTelltales(telltaleListener) }
            runCatching { v.getDrivingMode().unregisterDrivingModeState(modeListener) }
            runCatching { v.getDrivingMode().unregisterCruiseControlState(cruiseListener) }
            runCatching { v.getTurnSignal().unregisterTurnSignalState(signalListener) }
            runCatching { v.release() }
        }
        runCatching { fused?.unregisterFusedLocationCallback(locationListener); fused?.release() }
        runCatching { car?.disconnect() }
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "RoadDrive"
        private const val CHANNEL = "drive"
        private const val ACTION_RECONNECT = "reconnect"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, DriveService::class.java)) }
                .onFailure { Log.w(TAG, "could not start collector", it) }
        }

        /** 권한을 새로 받은 뒤 위치 수집을 다시 연결한다. 남은 주행도 다시 보내 본다. */
        fun reconnect(context: Context) {
            runCatching { context.startForegroundService(Intent(context, DriveService::class.java).setAction(ACTION_RECONNECT)) }
                .onFailure { Log.w(TAG, "could not reconnect collector", it) }
        }
    }
}
