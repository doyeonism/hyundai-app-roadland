package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripRecorderTest {
    @Test fun integratesDistanceFromSpeed() {
        val r = TripRecorder().apply { start(0) }
        for (s in 0..60) r.onSpeed(60.0, s * 1000L) // 60km/h로 60초 = 1km
        val trip = r.finish(61_000, passengers = 0)!!
        assertEquals(1.0, trip.km, 0.01)
        assertEquals(0, trip.hardBrakes)
    }

    /** 60km/h로 20초(약 0.33km) 달려 짧은 주행 제외 규칙에 걸리지 않게 한다. 마지막 시각을 돌려준다. */
    private fun cruise(r: TripRecorder): Long {
        for (s in 0..20) r.onSpeed(60.0, s * 1000L)
        return 20_000
    }

    @Test fun hardBrakeNeedsBrakeAndSharpDeceleration() {
        val r = TripRecorder().apply { start(0) }
        val t = cruise(r)
        r.onSpeed(55.0, t + 1000) // 완만한 감속: 급제동 아님
        r.onBrake(true, t + 1500)
        r.onSpeed(20.0, t + 2000) // 35km/h/s 감속 + 브레이크
        r.onSpeed(5.0, t + 3000) // 같은 브레이크는 한 번만 센다
        r.onSpeed(40.0, t + 10_000)
        r.onSpeed(10.0, t + 11_000) // 브레이크 없이 급감속: 세지 않음
        assertEquals(1, r.finish(t + 12_000, 0)!!.hardBrakes)
    }

    @Test fun speedSampleBeforeBrakeEventStillCounts() {
        val r = TripRecorder().apply { start(0) }
        val t = cruise(r)
        r.onSpeed(20.0, t + 1000)
        r.onBrake(true, t + 1200)
        assertEquals(1, r.finish(t + 2000, 0)!!.hardBrakes)
    }

    @Test fun keepsLocationPointsAtLeast80mApart() {
        val r = TripRecorder().apply { start(0) }
        r.onLocation(37.5446, 127.0559)
        r.onLocation(37.5447, 127.0559) // 약 11m
        r.onLocation(37.5460, 127.0559) // 약 155m
        r.onSpeed(30.0, 0); r.onSpeed(30.0, 20_000)
        assertEquals(2, r.finish(20_000, 0)!!.points.size)
    }

    @Test fun ignoresTinyTrips() {
        val r = TripRecorder().apply { start(0) }
        r.onSpeed(0.0, 0); r.onSpeed(0.0, 5000)
        assertNull(r.finish(5000, 0))
    }
}
