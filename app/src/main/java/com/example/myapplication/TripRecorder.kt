package com.example.myapplication

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 한 타일 안에서의 주행 결과. 서버로는 이것만 간다(원본 좌표는 차 밖으로 보내지 않는다). */
data class TileSegment(val id: String, var hardBrake: Boolean = false, var warning: Boolean = false, var assisted: Boolean = false)

data class TripSummary(
    val startedAt: Long, val endedAt: Long, val km: Double, val safetyScore: Int,
    val hardBrakes: Int, val warnings: Int, val laneChanges: Int, val signaledLaneChanges: Int,
    val tiles: List<TileSegment>,
)

/** 주행 중 위젯에 보여 줄 값. */
data class LiveStats(val tiles: Int, val credits: Int, val safetyScore: Int)

/**
 * 정차(P)를 벗어나면 시작하고 다시 정차하면 끝나는 한 번의 주행을 모은다. 안드로이드 의존성이 없어 단위 테스트한다.
 *
 * - 급제동: 브레이크를 밟은 앞뒤 2초 안에 초당 7.5km/h 이상 감속(교통안전공단 급감속 기준, 6km/h 이상에서). 그 타일은 보상 제외.
 * - 안전 경고: 경고가 켜져 있는 동안 지나간 타일은 보상 제외. 켜질 때마다 한 번 센다.
 * - 에코 모드·크루즈: 켜진 채 지나간 타일은 보너스.
 * - 차선 변경: 30km/h 이상에서 핸들을 6도 넘게 꺾으면 한 번(4초에 한 번까지). 앞뒤 3초 안에 깜빡이가 켜져 있었으면 깜빡이 사용.
 * - 위치 튐: 직전 위치에서 시속 250km 넘게 떨어진 위치는 믿지 않는다. 주행 시작 직후라면 직전 위치가 오래된 것이라 보고
 *   그때까지의 타일을 버리고 새 위치부터 다시 센다.
 */
class TripRecorder(
    private val hardDecelKphPerSec: Double = 7.5,
    private val minSpeedKph: Double = 6.0,
    private val brakeWindowMs: Long = 2000,
    private val laneChangeDeg: Double = 6.0,
    private val laneChangeMinKph: Double = 30.0,
    private val laneChangeCooldownMs: Long = 4000,
    private val signalWindowMs: Long = 3000,
    private val maxJumpMps: Double = 70.0,
    private val minJumpMeters: Double = 300.0,
) {
    var active = false
        private set
    private var startedAt = 0L
    private var km = 0.0
    private var hardBrakes = 0
    private var warnings = 0
    private var laneChanges = 0
    private var signaledLaneChanges = 0
    private val tiles = LinkedHashMap<String, TileSegment>()
    private var current: TileSegment? = null
    var lastKph: Double? = null
        private set
    private var lastKphAt = 0L
    private var brakeAt = Long.MIN_VALUE / 2
    private var bigDecelAt = Long.MIN_VALUE / 2
    private var countedBrakeAt = Long.MIN_VALUE
    private var warningOn = false
    private var assistedOn = false
    private var signalOn = false
    private var signalSeenAt = Long.MIN_VALUE / 2
    private var laneChangeAt = Long.MIN_VALUE / 2
    private var pendingLaneChangeAt: Long? = null
    private var lastFix: Triple<Double, Double, Long>? = null
    private var fixes = 0

    fun start(now: Long) {
        active = true
        startedAt = now
        km = 0.0
        hardBrakes = 0; warnings = 0; laneChanges = 0; signaledLaneChanges = 0
        tiles.clear()
        current = null
        lastKph = null
        countedBrakeAt = Long.MIN_VALUE
        pendingLaneChangeAt = null
        lastFix = null
        fixes = 0
        if (warningOn) warnings++
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
        settleLaneChange(now)
    }

    fun onBrake(pressed: Boolean, now: Long) {
        if (!active || !pressed) return
        brakeAt = now
        countIfPaired()
    }

    fun onLocation(lat: Double, lng: Double, now: Long) {
        if (!active) return
        val prev = lastFix
        if (prev != null) {
            val m = meters(prev.first, prev.second, lat, lng)
            val sec = maxOf(1.0, (now - prev.third) / 1000.0)
            if (m >= minJumpMeters && m / sec > maxJumpMps) {
                if (fixes > 2) return // 주행 중 한 번 튄 위치: 버린다
                tiles.clear() // 시작 때 받은 오래된 위치였다: 새 위치부터 다시
                current = null
                fixes = 0
            }
        }
        lastFix = Triple(lat, lng, now)
        fixes++
        val id = HexGrid.tileFor(lat, lng)
        val seg = tiles.getOrPut(id) { TileSegment(id) }
        current = seg
        if (warningOn) seg.warning = true
        if (assistedOn) seg.assisted = true
    }

    /** 차량 안전 경고(마스터 경고, ABS, ESC 등)가 켜졌는지. */
    fun onWarning(on: Boolean) {
        if (on && !warningOn && active) warnings++
        warningOn = on
        if (on) current?.warning = true
    }

    /** 에코 모드 또는 크루즈가 켜졌는지. */
    fun onAssist(on: Boolean) {
        assistedOn = on
        if (on) current?.assisted = true
    }

    fun onTurnSignal(on: Boolean, now: Long) {
        if (on || signalOn) signalSeenAt = now
        signalOn = on
        settleLaneChange(now)
    }

    fun onSteering(deg: Double, now: Long) {
        if (!active) return
        val kph = lastKph ?: return
        if (kph < laneChangeMinKph || abs(deg) < laneChangeDeg || now - laneChangeAt < laneChangeCooldownMs) return
        laneChangeAt = now
        laneChanges++
        if (signalOn || now - signalSeenAt <= signalWindowMs) signaledLaneChanges++ else pendingLaneChangeAt = now
        settleLaneChange(now)
    }

    /** 깜빡이를 핸들보다 조금 늦게 켜도 3초 안이면 사용으로 친다. */
    private fun settleLaneChange(now: Long) {
        val at = pendingLaneChangeAt ?: return
        if (signalOn || (signalSeenAt >= at && signalSeenAt - at <= signalWindowMs)) {
            signaledLaneChanges++
            pendingLaneChangeAt = null
        } else if (now - at > signalWindowMs) pendingLaneChangeAt = null
    }

    fun tileIds(): Set<String> = tiles.keys.toSet()

    fun live() = LiveStats(tiles.size, tiles.values.sumOf { Rules.tileCredit(it) }.toInt(), score())

    private fun score() = Rules.safetyScore(hardBrakes, warnings, laneChanges - signaledLaneChanges)

    /** 끝난 주행. 지나간 타일이 없거나(위치를 못 받음) 너무 짧으면(100m 미만이고 타일 두 칸 미만) null. */
    fun finish(now: Long): TripSummary? {
        if (!active) return null
        settleLaneChange(Long.MAX_VALUE / 2)
        active = false
        if (tiles.isEmpty() || (km < 0.1 && tiles.size < 2)) return null
        return TripSummary(startedAt, now, km, score(), hardBrakes, warnings, laneChanges, signaledLaneChanges, tiles.values.map { it.copy() })
    }

    private fun countIfPaired() {
        if (abs(bigDecelAt - brakeAt) <= brakeWindowMs && countedBrakeAt != brakeAt) {
            hardBrakes++
            countedBrakeAt = brakeAt
            current?.hardBrake = true
        }
    }

    companion object {
        fun meters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * 6_371_000.0 * asin(sqrt(a))
        }
    }
}
