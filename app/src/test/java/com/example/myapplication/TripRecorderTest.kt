package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecorderTest {
    private val home = 37.5446 to 127.0559
    private val far = 37.5480 to 127.0559 // 약 380m 북쪽, 다른 타일

    /** 60km/h로 20초(약 0.33km) 달린다. 마지막 시각을 돌려준다. */
    private fun cruise(r: TripRecorder, from: Long = 0): Long {
        for (s in 0..20) r.onSpeed(60.0, from + s * 1000L)
        return from + 20_000
    }

    @Test fun integratesDistanceFromSpeed() {
        val r = TripRecorder().apply { start(0) }
        for (s in 0..60) r.onSpeed(60.0, s * 1000L) // 60km/h로 60초 = 1km
        val trip = r.finish(61_000)!!
        assertEquals(1.0, trip.km, 0.01)
        assertEquals(0, trip.hardBrakes)
        assertEquals(100, trip.safetyScore)
    }

    @Test fun hardBrakeNeedsBrakeAndSharpDecelerationAndMarksTheTile() {
        val r = TripRecorder().apply { start(0) }
        r.onLocation(home.first, home.second)
        val t = cruise(r)
        r.onSpeed(55.0, t + 1000) // 완만한 감속: 급제동 아님
        r.onBrake(true, t + 1500)
        r.onSpeed(20.0, t + 2000) // 35km/h/s 감속 + 브레이크
        r.onSpeed(5.0, t + 3000) // 같은 브레이크는 한 번만 센다
        r.onLocation(far.first, far.second)
        r.onSpeed(40.0, t + 10_000)
        r.onSpeed(10.0, t + 11_000) // 브레이크 없이 급감속: 세지 않음
        val trip = r.finish(t + 12_000)!!
        assertEquals(1, trip.hardBrakes)
        assertTrue(trip.tiles[0].hardBrake)
        assertFalse(trip.tiles[1].hardBrake)
        assertEquals(85, trip.safetyScore)
    }

    @Test fun warningExcludesTilesWhileOn() {
        val r = TripRecorder().apply { start(0) }
        r.onLocation(home.first, home.second)
        r.onWarning(true)
        r.onWarning(true) // 켜져 있는 동안은 한 번
        r.onWarning(false)
        r.onLocation(far.first, far.second)
        cruise(r)
        val trip = r.finish(30_000)!!
        assertEquals(1, trip.warnings)
        assertTrue(trip.tiles[0].warning)
        assertFalse(trip.tiles[1].warning)
    }

    @Test fun assistedTilesFollowEcoOrCruise() {
        val r = TripRecorder().apply { start(0) }
        r.onAssist(true)
        r.onLocation(home.first, home.second)
        r.onAssist(false)
        r.onLocation(far.first, far.second)
        cruise(r)
        val tiles = r.finish(30_000)!!.tiles
        assertTrue(tiles[0].assisted)
        assertFalse(tiles[1].assisted)
        assertEquals(25, TripRecorder().run { start(0); onAssist(true); onLocation(home.first, home.second); onAssist(false); onLocation(far.first, far.second); live().credits })
    }

    @Test fun laneChangesCountSignalsBeforeOrShortlyAfter() {
        val r = TripRecorder().apply { start(0) }
        val t = cruise(r) // 60km/h
        r.onTurnSignal(true, t); r.onTurnSignal(false, t + 1000)
        r.onSteering(10.0, t + 2000) // 깜빡이 뒤 1초: 사용
        r.onSteering(10.0, t + 3000) // 쿨다운 안: 세지 않음
        r.onSteering(-10.0, t + 10_000) // 깜빡이 없음
        r.onSpeed(60.0, t + 10_500)
        r.onSteering(9.0, t + 20_000) // 깜빡이를 1초 늦게 켬: 사용
        r.onTurnSignal(true, t + 21_000)
        r.onSteering(2.0, t + 30_000) // 작은 핸들 조작: 차선 변경 아님
        val trip = r.finish(t + 40_000)!!
        assertEquals(3, trip.laneChanges)
        assertEquals(2, trip.signaledLaneChanges)
        assertEquals(95, trip.safetyScore)
    }

    @Test fun slowSteeringIsNotALaneChange() {
        val r = TripRecorder().apply { start(0) }
        for (s in 0..60) {
            r.onSpeed(20.0, s * 1000L)
            if (s == 5) r.onSteering(30.0, 5500) // 골목 회전
        }
        assertEquals(0, r.finish(60_000)!!.laneChanges)
    }

    @Test fun ignoresTinyTrips() {
        val r = TripRecorder().apply { start(0) }
        r.onSpeed(0.0, 0); r.onSpeed(0.0, 5000)
        assertNull(r.finish(5000))
    }
}
