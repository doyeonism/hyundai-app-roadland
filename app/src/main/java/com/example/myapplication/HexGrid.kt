package com.example.myapplication

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 도로를 나누는 육각 타일. 서버의 apps/api/src/roadland/domain/hex.ts와 같은 계산을 해야 한다(둘 다 테스트에 같은 좌표·ID를 쓴다).
 * 웹 메르카토르 평면 위의 뾰족한 위쪽 육각형, 축 좌표 (q, r). ID는 "q:r".
 */
object HexGrid {
    /** 메르카토르 미터. 서울 위도에서 실제 약 10m. */
    const val SIZE_M = 12.5
    private const val R = 6378137.0

    fun tileFor(lat: Double, lng: Double): String {
        val x = R * lng * PI / 180
        val y = R * ln(tan(PI / 4 + lat * PI / 360))
        val fq = (sqrt(3.0) / 3 * x - y / 3) / SIZE_M
        val fr = (2.0 / 3 * y) / SIZE_M
        val fs = -fq - fr
        // JS Math.round와 같게 .5는 올림(Math.round). kotlin.math.round는 짝수 반올림이라 쓰지 않는다.
        var q = Math.round(fq)
        var r = Math.round(fr)
        val s = Math.round(fs)
        val dq = abs(q - fq)
        val dr = abs(r - fr)
        val ds = abs(s - fs)
        if (dq > dr && dq > ds) q = -r - s else if (dr > ds) r = -q - s
        return "$q:$r"
    }

    fun parse(id: String): Pair<Int, Int> = id.split(":").let { it[0].toInt() to it[1].toInt() }
}
