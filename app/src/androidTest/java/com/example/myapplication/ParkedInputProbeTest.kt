package com.example.myapplication

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/** Logs turn signal, wiper, light and gear events while parked, for the car-controls mini-games. Logs only, never fails. */
@RunWith(AndroidJUnit4::class)
class ParkedInputProbeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val seconds = InstrumentationRegistry.getArguments().getString("probeSeconds")?.toLongOrNull() ?: 40
    private val start = SystemClock.elapsedRealtime()

    private fun log(name: String, value: Any?) { Log.i(TAG, "$name +${SystemClock.elapsedRealtime() - start}ms $value") }

    @Test fun logParkedInputs() {
        val vehicle = Vehicle(context).also { it.initialize() }
        val front = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val turn = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = log("turnSignal", value)
            override fun onFailed(e: Exception) = log("turnSignal failed", e.message)
        }
        val wiper = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) = log("wiper", state)
            override fun onFailed(e: Exception) = log("wiper failed", e.message)
        }
        val lowBeam = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) = log("lowBeam", state)
            override fun onFailed(e: Exception) = log("lowBeam failed", e.message)
        }
        val highBeam = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) = log("highBeam", state)
            override fun onFailed(e: Exception) = log("highBeam failed", e.message)
        }
        val gear = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) = log("gear", gear)
            override fun onFailed(e: Exception) = log("gear failed", e.message)
        }
        fun attempt(name: String, call: () -> Unit) =
            runCatching(call).onFailure { log("$name throw", "${it.javaClass.simpleName}: ${it.message}") }
        attempt("turnSignal") { vehicle.getTurnSignal().registerTurnSignalState(turn) }
        attempt("wiper") { vehicle.getWiper().registerWiperState(front, wiper) }
        attempt("lowBeam") { vehicle.getLight().registerLowBeamLightState(lowBeam) }
        attempt("highBeam") { vehicle.getLight().registerHighBeamLightState(highBeam) }
        attempt("gear") { vehicle.getGear().registerGearSelection(gear) }
        log("ready", "listening for $seconds s")

        Thread.sleep(seconds * 1000)
        attempt("cleanup") {
            vehicle.getTurnSignal().unregisterTurnSignalState(turn)
            vehicle.getWiper().unregisterWiperState(front, wiper)
            vehicle.getLight().unregisterLowBeamLightState(lowBeam)
            vehicle.getLight().unregisterHighBeamLightState(highBeam)
            vehicle.getGear().unregisterGearSelection(gear)
            vehicle.release()
        }
    }

    private companion object { const val TAG = "ParkedInput" }
}
