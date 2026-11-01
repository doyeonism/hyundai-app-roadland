package com.example.myapplication

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class TripSummary(
    val startedAt: Long, val endedAt: Long, val km: Double, val hardBrakes: Int,
    val passengers: Int, val points: List<Pair<Double, Double>>,
)

/**
 * 기어가 P를 벗어나면 시작하고 P로 돌아오면 끝나는 한 번의 주행을 모은다. 안드로이드 의존성이 없어 단위 테스트한다.
 *
 * 급제동: 브레이크를 밟은 앞뒤 2초 안에 초당 7.5km/h 이상 감속(교통안전공단 급감속 기준, 6km/h 이상에서).
 */
class TripRecorder(
    private val hardDecelKphPerSec: Double = 7.5,
    private val minSpeedKph: Double = 6.0,
    private val brakeWindowMs: Long = 2000,
    private val minPointMeters: Double = 80.0,
) {
    var active = false
        private set
    private var startedAt = 0L
    private var km = 0.0
    private var hardBrakes = 0
    private val points = mutableListOf<Pair<Double, Double>>()
    private var lastKph: Double? = null
    private var lastKphAt = 0L
    private var brakeAt = Long.MIN_VALUE / 2
    private var bigDecelAt = Long.MIN_VALUE / 2
    private var countedBrakeAt = Long.MIN_VALUE

    fun start(now: Long) {
        active = true
        startedAt = now
        km = 0.0
        hardBrakes = 0
        points.clear()
        lastKph = null
        countedBrakeAt = Long.MIN_VALUE
    }

    fun onSpeed(kph: Double, now: Long) {
        if (!active) return
        val prev = lastKph
        if (prev != null) {
            val dt = (now - lastKphAt) / 1000.0
            if (dt > 0 && dt <= 10) {
                km += (prev + kph) / 2 * dt / 3600
                if (prev >= minSpeedKph && (prev - kph) / dt >= hardDecelKphPerSec) {
                    bigDecelAt = now
                    countIfPaired()
                }
            }
        }
        lastKph = kph
        lastKphAt = now
    }

    fun onBrake(pressed: Boolean, now: Long) {
        if (!active || !pressed) return
        brakeAt = now
        countIfPaired()
    }

    fun onLocation(lat: Double, lng: Double) {
        if (!active) return
        val last = points.lastOrNull()
        if (last == null || meters(last.first, last.second, lat, lng) >= minPointMeters) points.add(lat to lng)
    }

    /** 끝난 주행. 너무 짧으면(100m 미만이고 이동 지점도 없으면) null. */
    fun finish(now: Long, passengers: Int): TripSummary? {
        if (!active) return null
        active = false
        if (km < 0.1 && points.size < 2) return null
        return TripSummary(startedAt, now, km, hardBrakes, passengers, points.toList())
    }

    private fun countIfPaired() {
        if (kotlin.math.abs(bigDecelAt - brakeAt) <= brakeWindowMs && countedBrakeAt != brakeAt) {
            hardBrakes++
            countedBrakeAt = brakeAt
        }
    }

    companion object {
        fun meters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }
    }
}
