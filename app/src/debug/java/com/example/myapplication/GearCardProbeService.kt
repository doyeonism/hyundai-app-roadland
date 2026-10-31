package com.example.myapplication

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log

/** Debug probe: when the gear changes to P, tries to bring DiagnosticsActivity to the screen from the background. */
class GearCardProbeService : Service() {
    private var vehicle: Vehicle? = null
    private var lastGear: VehicleGear? = null
    private val listener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            Log.i(TAG, "gear=$gear (was $lastGear)")
            if (gear == VehicleGear.GEAR_PARK && lastGear != null && lastGear != gear) showCard()
            lastGear = gear
        }
        override fun onFailed(e: Exception) { Log.i(TAG, "gear listener failed: ${e.message}") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = NotificationChannel(TAG, "Gear probe", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = Notification.Builder(this, TAG).setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Gear probe").build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (vehicle == null) {
            vehicle = Vehicle(this).also { it.initialize() }
            runCatching { vehicle!!.getGear().registerGearSelection(listener) }
                .onFailure { Log.i(TAG, "register throw ${it.javaClass.simpleName}: ${it.message}") }
            Log.i(TAG, "listening for gear changes")
        }
        return START_STICKY
    }

    private fun showCard() {
        runCatching {
            startActivity(Intent(this, DiagnosticsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onSuccess { Log.i(TAG, "startActivity returned without error") }
            .onFailure { Log.i(TAG, "startActivity throw ${it.javaClass.simpleName}: ${it.message}") }
    }

    override fun onDestroy() {
        runCatching { vehicle?.getGear()?.unregisterGearSelection(listener) }
        runCatching { vehicle?.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object { const val TAG = "GearCardProbe" }
}
