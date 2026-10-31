package com.example.myapplication

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Reads the signals the older-driver idea depends on and logs what each one returns. Never fails on a missing value. */
@RunWith(AndroidJUnit4::class)
class SignalProbeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val results = ConcurrentHashMap<String, String>()

    private fun probe(key: String, call: ((String) -> Unit) -> Unit) {
        val latch = CountDownLatch(1)
        runCatching { call { results[key] = it; latch.countDown() } }
            .onFailure { results[key] = "throw ${it.javaClass.simpleName}: ${it.message}"; latch.countDown() }
        if (!latch.await(5, TimeUnit.SECONDS)) results.putIfAbsent(key, "timeout")
    }

    @Test fun logVehicleAndNaviSignals() {
        val vehicle = Vehicle(context).also { it.initialize() }
        fun err(e: Exception) = "error ${e.javaClass.simpleName}: ${e.message}"
        probe("turnSignal") { done -> vehicle.getTurnSignal().getCurrentTurnSignalState({ done("$it") }, { done(err(it)) }) }
        probe("tripDrivingTime") { done -> vehicle.getOdometer().getTripCurrentDrivingTime({ done("$it") }, { done(err(it)) }) }
        probe("tripDrivingDistance") { done -> vehicle.getOdometer().getTripCurrentDrivingDistance({ done("$it") }, { done(err(it)) }) }
        probe("odometer") { done -> vehicle.getOdometer().getCurrentDistanceDriven({ done("$it") }, { done(err(it)) }) }
        probe("lowBeam") { done -> vehicle.getLight().getCurrentLowBeamLightState({ done("$it") }, { done(err(it)) }) }
        probe("cruiseState") { done -> vehicle.getDrivingMode().getCruiseControlState({ done("$it") }, { done(err(it)) }) }
        probe("brakePedal") { done -> vehicle.getBrake().getCurrentBrakePedalStatus({ done("$it") }, { done(err(it)) }) }
        probe("steeringAngle") { done -> vehicle.getSteeringWheel().getCurrentSteeringWheelAngle({ done("$it") }, { done(err(it)) }) }

        val navi = NaviHelper(context)
        val events = ConcurrentHashMap<String, String>()
        val listener = object : NaviHelperEventListener {
            override fun onRouteStateInfo(info: RouteStateInfo) { events["routeState"] = "isRouting=${info.isRouting}" }
            override fun onCurrentLocationInfo(info: CurrentLocationInfo) { events["location"] = "${info.latitude},${info.longitude} ${info.address}" }
            override fun onTBTInfo(info: List<TBTInfo>) { events["tbt"] = info.take(2).joinToString { "${it.type} ${it.distance}m" } }
            override fun onCurrentRoadSpeedLimit(limit: Int) { events["speedLimit"] = "$limit" }
            override fun onReRouteEnded() { events["reRoute"] = "received" }
            override fun onError(error: NaviErrorCode) { events["error"] = "$error" }
        }
        val ready = CountDownLatch(1)
        navi.initialize({ navi.addListener(listener); ready.countDown() }, { results["navi"] = "init failed: $it"; ready.countDown() })
        if (ready.await(8, TimeUnit.SECONDS) && results["navi"] == null) {
            runCatching { navi.getRouteStateInfo(); navi.getCurrentLocationInfo(); navi.getTBTInfo() }
                .onFailure { events["call"] = "throw ${it.javaClass.simpleName}" }
            Thread.sleep(5000)
        }
        events.forEach { (k, v) -> results["navi.$k"] = v }
        results.toSortedMap().forEach { (k, v) -> android.util.Log.i("SignalProbe", "$k = $v") }
        runCatching { navi.removeListener(listener); navi.release() }
        runCatching { vehicle.release() }
    }
}
