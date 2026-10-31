package com.example.myapplication

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Measures how often steering and brake updates arrive, for the steering-wheel game idea. Logs only, never fails. */
@RunWith(AndroidJUnit4::class)
class InputRateProbeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val seconds = InstrumentationRegistry.getArguments().getString("probeSeconds")?.toLongOrNull() ?: 40

    @Test fun measureSteeringAndBrakeRates() {
        val vehicle = Vehicle(context).also { it.initialize() }
        val steering = Collections.synchronizedList(mutableListOf<Pair<Long, Float?>>())
        val brake = Collections.synchronizedList(mutableListOf<Pair<Long, Boolean?>>())
        val steeringListener = object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(angle: Float?) { steering += SystemClock.elapsedRealtime() to angle }
            override fun onFailed(e: Exception) { Log.i(TAG, "steering listener failed: ${e.message}") }
        }
        val brakeListener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(pressed: Boolean?) { brake += SystemClock.elapsedRealtime() to pressed }
            override fun onFailed(e: Exception) { Log.i(TAG, "brake listener failed: ${e.message}") }
        }
        runCatching { vehicle.getSteeringWheel().registerSteeringWheelAngle(steeringListener) }
            .onFailure { Log.i(TAG, "steering register throw ${it.javaClass.simpleName}: ${it.message}") }
        runCatching { vehicle.getBrake().registerBrakePedalStatus(brakeListener) }
            .onFailure { Log.i(TAG, "brake register throw ${it.javaClass.simpleName}: ${it.message}") }
        Log.i(TAG, "listening for $seconds s — move the steering wheel and brake in the Virtual Console now")

        // Polling in a tight loop shows how fast a game could sample the value if events are sparse.
        val polls = mutableListOf<Long>()
        val pollChanges = mutableListOf<Pair<Long, Float?>>()
        val deadline = SystemClock.elapsedRealtime() + seconds * 1000
        while (SystemClock.elapsedRealtime() < deadline) {
            val start = SystemClock.elapsedRealtime()
            val done = CountDownLatch(1)
            var value: Float? = null
            vehicle.getSteeringWheel().getCurrentSteeringWheelAngle({ value = it; done.countDown() }, { done.countDown() })
            if (done.await(1, TimeUnit.SECONDS)) {
                polls += SystemClock.elapsedRealtime() - start
                if (pollChanges.lastOrNull()?.second != value) pollChanges += start to value
            }
        }
        runCatching { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
        runCatching { vehicle.getBrake().unregisterBrakePedalStatus(brakeListener) }
        runCatching { vehicle.release() }

        report("steering", steering.toList())
        report("brake", brake.toList())
        report("polledSteeringChanges", pollChanges)
        val sorted = polls.sorted()
        Log.i(TAG, "poll getCurrentSteeringWheelAngle: n=${polls.size} median=${sorted.getOrNull(sorted.size / 2)}ms max=${sorted.lastOrNull()}ms")
    }

    private fun <T> report(name: String, events: List<Pair<Long, T>>) {
        val gaps = events.zipWithNext { a, b -> b.first - a.first }.sorted()
        val first = events.firstOrNull()?.first ?: 0
        Log.i(TAG, "$name: events=${events.size} distinctValues=${events.map { it.second }.distinct().size} " +
            "gap min=${gaps.firstOrNull()}ms median=${gaps.getOrNull(gaps.size / 2)}ms max=${gaps.lastOrNull()}ms")
        events.take(400).forEach { (t, v) -> Log.i(TAG, "$name +${t - first}ms $v") }
    }

    private companion object { const val TAG = "InputRate" }
}
