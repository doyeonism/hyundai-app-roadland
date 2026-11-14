package com.example.myapplication

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Local-only film. Driving uses platform SDK signals from the road replay tool, with labeled economy fixtures. */
@RunWith(AndroidJUnit4::class)
class ShowcaseRecordingTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx = ins.targetContext
    private lateinit var activity: MainActivity
    private lateinit var vehicleId: String
    private lateinit var token: String
    private var started = 0L
    private val chapters = JSONArray()
    private val base = "http://10.0.2.2:3000/api/roadland/v1/car"
    private fun main(work: () -> Unit) = ins.runOnMainSync(work)
    private fun hold(seconds: Long = 4) = Thread.sleep(seconds * 1000)
    private fun chapter(title: String, note: String) {
        chapters.put(JSONObject().put("start", (SystemClock.elapsedRealtime() - started) / 1000.0).put("title", title).put("note", note))
        android.util.Log.i("RoadlandShowcase", title)
    }
    private fun api(path: String, body: JSONObject? = null, method: String = if (body == null) "GET" else "POST", bearer: String? = token): JSONObject {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 10000; c.readTimeout = 15000
            bearer?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (method == "POST") {
                c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
            check(code in 200..299) { "$path HTTP $code" }
            return JSONObject(text).getJSONObject("data")
        } finally { c.disconnect() }
    }
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun click(prefix: String) {
        var target: Button? = null
        main {
            target = views(activity.window.decorView).filterIsInstance<Button>().firstOrNull { it.text.toString().startsWith(prefix) }
            check(target != null) { "Missing button $prefix" }
            check(target!!.isEnabled) { "Disabled button $prefix" }
            target!!.requestRectangleOnScreen(Rect(0, 0, target!!.width, target!!.height), false)
        }
        Thread.sleep(700)
        main { target!!.performClick() }
        hold(3)
    }
    private fun root(screen: Screen) { main { activity.replaceRoot(screen) }; hold(3) }
    private fun home() = root(HomeScreen(activity))
    private fun territory(): TerritoryScreen {
        val screen = TerritoryScreen(activity)
        root(screen)
        return screen
    }
    private fun map(screen: TerritoryScreen): HexMapView {
        return TerritoryScreen::class.java.getDeclaredField("map").apply { isAccessible = true }.get(screen) as HexMapView
    }
    private fun select(screen: TerritoryScreen, id: String) {
        main {
            val m = map(screen)
            m.fit(listOf(id), 30f, 18f)
            m.onSelect?.invoke(id)
        }
        hold(3)
    }
    private fun tripBody(offsetMin: Long, unsafe: Boolean, lat: Double, count: Int = 100): JSONObject {
        val rec = TripRecorder()
        val start = System.currentTimeMillis() - offsetMin * 60_000
        rec.start(start)
        rec.onAssist(true)
        val coords = JSONObject(ins.context.assets.open("road-route.json").bufferedReader().use { it.readText() }).getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        var segment = 0
        var remaining = 0.0
        fun len(index: Int): Double {
            val a=coords.getJSONArray(index); val b=coords.getJSONArray(index+1)
            return kotlin.math.hypot((b.getDouble(0)-a.getDouble(0))*88400, (b.getDouble(1)-a.getDouble(1))*111320)
        }
        for (i in 0..count) {
            val now = start + i * 1000L
            rec.onSpeed(if (unsafe && i in 17..19) 20.0 else 50.0, now)
            if(i>0) remaining += 50.0/3.6
            while(segment<coords.length()-2 && remaining>len(segment)) { remaining-=len(segment); segment++ }
            val a=coords.getJSONArray(segment);val b=coords.getJSONArray(segment+1)
            val f=(remaining/maxOf(.001,len(segment))).coerceIn(0.0,1.0)
            rec.onLocation(a.getDouble(1)+(b.getDouble(1)-a.getDouble(1))*f,a.getDouble(0)+(b.getDouble(0)-a.getDouble(0))*f,now)
            if (i == 17 && unsafe) rec.onBrake(true, now)
            if (i == 27 && unsafe) rec.onWarning(true)
            if (i == 30) rec.onWarning(false)
            if (i == 35) { rec.onTurnSignal(true, now); rec.onSteering(8.0, now) }
        }
        val summary = checkNotNull(rec.finish(start + (count + 1) * 1000L))
        return CarApi.tripBody(summary)
    }
    private fun driveOnRoad(offset: Int, unsafe: Boolean) {
        Settings.collectionPaused = false
        DriveService.start(ctx)
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        val last = CarApi.homeBlocking().latestTrip?.id
        ins.uiAutomation.executeShellCommand("am start -n com.doyeonism.mappreview/.MainActivity").close()
        hold(5)
        Thread {
            try {
                val c = URL("http://10.0.2.2:8090/replay?seconds=105&offset=$offset&unsafe=$unsafe").openConnection() as HttpURLConnection
                c.readTimeout = 180000
                check(c.responseCode == 200)
                c.inputStream.use { it.readBytes() }
                c.disconnect()
            } catch (t: Throwable) { failure=t } finally { done.countDown() }
        }.start()
        hold(27)
        ins.uiAutomation.executeShellCommand("am start -n ${ctx.packageName}/com.example.myapplication.MainActivity").close()
        check(done.await(140,TimeUnit.SECONDS)) { "Road replay timed out" }
        failure?.let { throw it }
        hold(10)
        check(CarApi.homeBlocking().latestTrip?.id != last) { "Real SDK trip did not settle" }
        hold(5)
    }

    @Test fun recordAllContent() {
        check(ctx.packageName.endsWith(".showcase")) { "Use isolated showcase package" }
        check(CarApi.BASE == base) { "Recording must use local server" }
        CarApi.init(ctx)
        val registered = api("/devices", JSONObject(), bearer = null)
        token = registered.getString("token"); vehicleId = registered.getString("vehicleId")
        TripEvents.prefs(ctx).edit().putString("device_token", token).commit()
        api("/privacy/consent", JSONObject().put("version", "2026-11-14"))
        Settings.finishOnboarding(ctx)
        Settings.setVoice(ctx, false)
        Settings.collectionPaused = false
        val currentUser = ins.uiAutomation.executeShellCommand("am get-current-user").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim() }
        for (permission in listOf("android.car.permission.CAR_SPEED", "pleos.car.permission.FUSED_LOCATION")) {
            ins.uiAutomation.executeShellCommand("pm grant --user $currentUser ${ctx.packageName} $permission").close()
        }
        activity = ins.startActivitySync(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        hold(5)
        val recording = ins.uiAutomation.executeShellCommand("screenrecord --size 1920x1080 --bit-rate 6000000 --time-limit 0 /sdcard/Download/Roadland-showcase-raw.mp4")
        started = SystemClock.elapsedRealtime()
        try {
            chapter("01 시작과 홈", "별도 시연 계정 · 실제 앱 화면 · 로컬 API")
            root(OnboardingScreen(activity)); hold(5)
            home(); hold(5)

            chapter("02 내 영토 지도 탐색", "시연용 방문 기록 · 전국에서 개별 땅까지 확대")
            // Build fixtures through the same settlement and purchase APIs, without touching existing accounts.
            for (day in 3 downTo 1) api("/trips", tripBody(day * 1440L + 20, false, 37.5522))
            val visited = api("/territory").getJSONArray("tiles")
            val vacant = (0 until visited.length()).map { visited.getJSONObject(it) }.filter { !it.getBoolean("ownedByOther") && !it.isNull("buyCost") }
            check(vacant.size >= 5)
            for (tile in vacant.take(4)) api("/tiles/${tile.getString("id")}/buy", JSONObject())
            api("/dev/vehicles/$vehicleId/advance", JSONObject().put("hours", 5))
            home(); hold(3)
            var land = territory()
            val all = api("/territory").getJSONArray("tiles")
            val ownId = (0 until all.length()).map { all.getJSONObject(it) }.first { it.getBoolean("owned") }.getString("id")
            val emptyId = (0 until all.length()).map { all.getJSONObject(it) }.first { !it.getBoolean("owned") && !it.getBoolean("ownedByOther") && !it.isNull("buyCost") }.getString("id")
            val stages = listOf(0.15f, 1f, 6f, 20f)
            for (size in stages) { main { map(land).fit(listOf(ownId), 1_000_000f / size, size) }; hold(3) }
            select(land, ownId); hold(3)
            click("전국 보기")

            chapter("03 수익 수령과 땅 구매", "기존 레벨의 누적 수익 수령 · 방문한 빈 땅 구매")
            home(); click("수익 받기"); hold(4)
            land = territory(); select(land, emptyId); click("이 땅 사기"); hold(5)

            chapter("04 개인 레벨업", "크레딧 결제 · 모든 소유지의 하루 수익이 각각 1 증가")
            home(); click("개인 레벨 올리기"); hold(6)

            chapter("05 도로 주행과 신호 대기", "실제 도로 GPS 재생 · 차량 D/속도 신호 · 중화역 출발 · Mapbox 지도 GPS 추적 · D 정차와 재출발")
            driveOnRoad(0, false)
            click("닫기")

            chapter("06 급제동 주행과 주차", "도로 경로 재생 · 실제 브레이크/안전 경고 신호 · P 주차 후 SDK 주행 정산")
            driveOnRoad(1800, true)
            click("닫기")

            chapter("07 다른 차량의 땅과 재방문", "별도 가상 상대 계정 · 소유권 변경 · 다시 지나간 뒤 구매")
            val rival = api("/devices", JSONObject(), bearer = null)
            val rivalToken = rival.getString("token")
            api("/privacy/consent", JSONObject().put("version", "2026-11-14"), bearer = rivalToken)
            api("/dev/vehicles/${rival.getString("vehicleId")}/advance", JSONObject().put("hours", 5))
            val route = JSONArray().put(JSONObject().put("id", emptyId).put("hardBrake", false).put("warning", false).put("assisted", false))
            val now = System.currentTimeMillis()
            val revisit = JSONObject().put("clientTripId", java.util.UUID.randomUUID().toString()).put("startedAt", java.time.Instant.ofEpochMilli(now - 90_000).toString()).put("endedAt", java.time.Instant.ofEpochMilli(now).toString()).put("km", 0.3).put("safetyScore", 100).put("hardBrakes",0).put("warnings",0).put("laneChanges",0).put("signaledLaneChanges",0).put("tiles",route)
            api("/trips", revisit, bearer = rivalToken)
            api("/tiles/$emptyId/buy", JSONObject(), bearer = rivalToken)
            land=territory(); select(land, emptyId); hold(5)
            Thread.sleep(1500)
            val newer = System.currentTimeMillis()
            revisit.put("clientTripId", java.util.UUID.randomUUID().toString()).put("startedAt", java.time.Instant.ofEpochMilli(now+1).toString()).put("endedAt", java.time.Instant.ofEpochMilli(newer).toString()).put("km",0.02)
            api("/trips",revisit)
            land=territory(); select(land,emptyId); click("빼앗기"); hold(5)

            chapter("08 하루 수익과 보상 한도", "시연 시계 24시간 가속 · 수익 수령 · 한도 확인용 사전 기록")
            api("/dev/vehicles/$vehicleId/advance", JSONObject().put("hours",24))
            home(); hold(5); click("수익 받기"); hold(4)
            api("/trips", tripBody(40, false, 37.5540, 1200))
            home(); hold(5)

            chapter("09 주행 기록과 순위", "최근 안전 점수 · 안전 연속 기록 · 보유 영토 순위")
            root(RecordsScreen(activity)); hold(7)

            chapter("10 설정과 삭제 안내", "음성 안내 켜기 · 삭제 확인창만 표시하며 삭제하지 않음")
            root(SettingsScreen(activity)); click("음성 안내"); hold(3)
            click("내 데이터 전체 삭제"); hold(5)
            main { activity.window.decorView.findViewById<View>(android.R.id.content)?.let {} }
            ins.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            hold(2)
            home(); hold(5)
        } finally {
            chapters.put(JSONObject().put("start", (SystemClock.elapsedRealtime()-started)/1000.0).put("title","END").put("note",""))
            File(ctx.getExternalFilesDir(null),"chapters.json").writeText(chapters.toString(2))
            ins.uiAutomation.executeShellCommand("pkill -2 screenrecord").close()
            hold(3); recording.close()
        }
    }
}

