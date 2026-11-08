package com.example.myapplication

/**
 * 차 안에서 계산하는 판정. 크레딧의 최종 값은 서버(web/lib/rules.ts)가 정하고, 여기서는 주행 중 위젯의 예상치만 낸다.
 * 타일 크레딧 값은 서버 RULES와 맞춘다.
 */
object Rules {
    const val TILE_CREDIT = 2
    const val ASSIST_BONUS = 0.5

    /** 안전 점수: 100에서 급제동 15, 안전 경고 10, 깜빡이 없는 차선 변경 5씩 뺀다. */
    fun safetyScore(hardBrakes: Int, warnings: Int, unsignaledLaneChanges: Int) =
        (100 - 15 * hardBrakes - 10 * warnings - 5 * unsignaledLaneChanges).coerceIn(0, 100)

    fun tileCredit(seg: TileSegment): Double =
        if (seg.hardBrake || seg.warning) 0.0 else TILE_CREDIT * (if (seg.assisted) 1 + ASSIST_BONUS else 1.0)

    /** 기어 P이거나 주차 브레이크가 걸려 있으면 정차(전체 게임 화면). */
    fun isParked(gearPark: Boolean, parkingBrake: Boolean) = gearPark || parkingBrake
}
