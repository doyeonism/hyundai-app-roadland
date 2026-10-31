package com.example.myapplication

import android.content.Intent
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real SDK calls on the selected device, including unsupported services. */
@RunWith(AndroidJUnit4::class)
class DiagnosticsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val keys = listOf("현재 속도", "기어", "도어", "배터리", "위치", "내비게이션", "TTS 연결", "STT 연결")

    @Test fun queriesFinishAndExposeRealResultsWithoutCrashing() {
        val result = runQueryAll()
        for (key in keys) {
            assertTrue("Missing result for $key: $result", result.contains("[$key]"))
        }
        assertFalse("Queries must finish or report a timeout: $result", result.contains("조회 중"))
    }

    /** On a Pleos image every service must answer with a real value, not a failure or timeout. */
    @Test fun pleosDeviceReturnsRealValuesWithRuntimePermissionsGranted() {
        assumeTrue("Pleos vehicle permissions are not defined on this device",
            runCatching { context.packageManager.getPermissionInfo("pleos.car.permission.CAR_DRIVE", 0) }.isSuccess)
        // Runtime permissions are granted for the user running the test (user 10 on AAOS, not the system user).
        for (permission in listOf("android.car.permission.CAR_SPEED", "pleos.car.permission.FUSED_LOCATION")) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        }
        val sections = runQueryAll().split("\n\n").associate { section ->
            section.substringBefore('\n').trim('[', ']') to section.substringAfter('\n')
        }
        val expected = mapOf(
            "현재 속도" to Regex("""\d+\.\d km/h · (이동 중|정지 상태)"""),
            "기어" to Regex("""GEAR_[A-Z_]+"""),
            "도어" to Regex("""앞 왼쪽 도어 원시값: -?\d+"""),
            "배터리" to Regex("""\d+(\.\d+)? %"""),
            "위치" to Regex("""위도 -?\d+(\.\d+)?, 경도 -?\d+(\.\d+)?"""),
            "내비게이션" to Regex("""응답 수신: RouteStateInfo\("""),
            "TTS 연결" to Regex("""초기화 성공"""),
            "STT 연결" to Regex("""초기화 성공"""),
        )
        val failures = expected.filter { (key, pattern) -> sections[key]?.let(pattern::containsMatchIn) != true }
            .map { (key, _) -> "$key → ${sections[key] ?: "결과 없음"}" }
        assertTrue("Pleos services did not return real values:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun runQueryAll(): String {
        val activity = instrumentation.startActivitySync(
            Intent(context, DiagnosticsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as DiagnosticsActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { activity.queryAll() }
            var result = ""
            val deadline = System.currentTimeMillis() + 12000
            do {
                Thread.sleep(250)
                instrumentation.runOnMainSync {
                    result = activity.findViewById<TextView>(R.id.diagnostics_results).text.toString()
                }
            } while (result.contains("조회 중") && System.currentTimeMillis() < deadline)
            assertFalse(activity.isFinishing)
            android.util.Log.i("DiagnosticsTest", result)
            return result
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
