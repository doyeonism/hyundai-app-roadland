package com.example.myapplication

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Locale
import kotlin.random.Random

object Onboarding {
    private const val DONE = "onboarded"
    private const val CODE = "link_code"
    fun done(c: Context) = TripEvents.prefs(c).getBoolean(DONE, false)
    fun finish(c: Context) = TripEvents.prefs(c).edit().putBoolean(DONE, true).apply()
    /** 휴대폰 웹과 연결할 6자리 코드. 더미 서버는 아무 6자리나 받는다. */
    fun linkCode(c: Context): String = TripEvents.prefs(c).getString(CODE, null)
        ?: Random.nextInt(100_000, 1_000_000).toString().also { TripEvents.prefs(c).edit().putString(CODE, it).apply() }
}

private fun Screen.page(title: String, showBack: Boolean, body: LinearLayout.() -> Unit): View {
    val u = host.ui
    val content = u.column(16).apply {
        setPadding(u.dp(32), u.dp(24), u.dp(32), u.dp(32))
        addView(u.row().apply {
            if (showBack) addView(u.button("‹ 뒤로", primary = false) { host.back() }, LinearLayout.LayoutParams(u.dp(160), -2))
            addView(u.text(title, 32f, bold = true))
        })
        body()
    }
    return ScrollView(host).apply { addView(content) }
}

/** 설치 후 첫 P: 30초 온보딩. 수집 서비스는 이미 켜져 있다. */
class OnboardingScreen(host: MainActivity) : Screen(host) {
    override fun build() = page("안녕, 나는 붕붕이야!", showBack = false) {
        val code = Onboarding.linkCode(host)
        addView(ui.card().apply {
            addView(ui.text("🚗  운전하면 내가 자라요. 주행 중에는 화면 없이 기록만 해요."))
            addView(ui.text("🅿️  주차(P)하면 오늘의 정산과 미션을 볼 수 있어요."))
            addView(ui.text("📱  건물 짓기와 꾸미기는 휴대폰에서 해요."))
        })
        addView(ui.card().apply {
            addView(ui.text("시작하면 권한 두 개를 물어봐요", 20f, Palette.MUTED))
            addView(ui.text("📍 위치: 지나간 동네를 육각형 땅으로 바꿀 때만 써요. 원래 좌표는 저장하지 않아요.", 20f))
            addView(ui.text("⏱️ 차량 속도(화면에는 '알 수 없는 작업'으로 보일 수 있어요): 거리와 급제동을 셀 때만 써요.", 20f))
        })
        addView(ui.card().apply {
            addView(ui.text("휴대폰 연결 코드", 20f, Palette.MUTED))
            addView(ui.text(code.chunked(3).joinToString(" "), 44f, Palette.ACCENT, bold = true))
            addView(ui.text("휴대폰에서 붕붕이 웹을 열고 이 숫자를 입력하세요. 나중에 첫 화면에서 다시 볼 수 있어요.", 18f, Palette.MUTED))
        })
        addView(ui.button("시작하기") {
            Onboarding.finish(host)
            host.ensurePermissions()
            host.replaceRoot(TodayScreen(host))
        }, ui.matchWidth())
    }
}

/** 차에서 앱을 열면 보이는 첫 화면: 오늘의 정산과 미션. */
class TodayScreen(host: MainActivity) : Screen(host) {
    private var home: Home? = null
    private var error: String? = null
    private var note: String? = null
    private var highlightTripId: String? = null
    private var loading = false
    private var reloadAfter = false
    private val onTrip: (Trip) -> Unit = { load() }

    init {
        TripEvents.listen(onTrip)
        load()
    }

    /** 불러오는 중에 새 정산이 오면 끝난 뒤 한 번 더 불러온다. */
    private fun load() {
        if (loading) { reloadAfter = true; return }
        loading = true
        CarApi.home { result ->
            loading = false
            result.onSuccess { h ->
                if (h.latestTrip?.id != home?.latestTrip?.id) note = null // 새 정산이 오면 이전 말풍선은 지운다.
                home = h; error = null
                val unseen = TripEvents.unseenTripId(host)
                if (unseen != null && h.latestTrip?.id == unseen) {
                    highlightTripId = unseen
                    TripEvents.markSeen(host)
                    host.speak(h.latestTrip.line)
                    // 정산 강조는 5초 뒤 저절로 사라진다.
                    host.main.postDelayed({ if (highlightTripId == unseen) { highlightTripId = null; host.refresh(this) } }, 5000)
                }
            }.onFailure { error = "서버에 연결하지 못했어요" }
            host.refresh(this)
            if (reloadAfter) { reloadAfter = false; load() }
        }
    }

    override fun build(): View {
        val h = home
        if (h == null) {
            return LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(ui.text(error ?: "붕붕이를 깨우는 중…", 28f).apply { gravity = Gravity.CENTER })
                if (error != null) addView(ui.button("다시 시도") { load() }, LinearLayout.LayoutParams(ui.dp(320), -2).apply { topMargin = ui.dp(24) })
            }
        }
        val left = ui.column(14).apply {
            addView(BungbungView(host).apply { bung = h.bung }, LinearLayout.LayoutParams(-1, ui.dp(300)))
            addView(ui.text("${h.bung.name}  Lv.${h.bung.level}", 30f, bold = true))
            addView(ui.text("${h.bung.stageLabel} · ${h.bung.formName}", 20f, Palette.MUTED))
            addView(ui.bar(h.bung.xp, h.bung.xpToNext))
            addView(ui.text(note ?: h.latestTrip?.line ?: "오늘도 부드럽게 달려 보자!", 24f).apply {
                background = ui.rounded(Palette.CARD, 18)
                setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16))
            })
            val food = h.wallet.food
            val canFeed = food > 0 && !h.bung.maxed
            val label = when {
                h.bung.maxed -> "최고 레벨이에요 · 먹이는 그대로 남아요"
                food > 0 -> "먹이 ${food}개 주기"
                else -> "먹이가 없어요 · 운전하면 생겨요"
            }
            addView(ui.button(label) { feed(food) }.apply { isEnabled = canFeed; alpha = if (canFeed) 1f else 0.5f }, ui.matchWidth())
        }

        val right = ui.column(14).apply {
            val trip = h.latestTrip
            val fresh = trip != null && trip.id == highlightTripId
            addView(ui.card().apply {
                if (fresh) background = ui.rounded(Palette.CARD, 20, Palette.ACCENT)
                addView(ui.text(if (fresh) "도착 정산" else "오늘", 22f, if (fresh) Palette.ACCENT else Palette.MUTED, bold = true))
                addView(ui.row(32).apply {
                    // 강조 중에는 방금 끝난 주행, 그 뒤에는 오늘 합계를 보여 준다.
                    if (fresh && trip != null) {
                        addView(stat(String.format(Locale.KOREA, "%.1f", trip.km), "km"))
                        addView(stat("+${trip.newTiles}", "새 타일"))
                        addView(stat("+${trip.foodEarned}", "먹이"))
                        addView(stat("${trip.hardBrakes}", "급제동"))
                    } else {
                        addView(stat(String.format(Locale.KOREA, "%.1f", h.todayKm), "km"))
                        addView(stat("${h.todayTiles}", "새 타일"))
                        addView(stat("${h.todayFood}", "먹이"))
                    }
                })
                if (trip?.foodCapReached == true) addView(ui.text("오늘 먹이 가득! 이후 운전은 도감·탐험에만 반영돼요", 18f, Palette.WARN))
                if (trip != null && trip.passengers > 0) addView(ui.text("동승자 보너스 ×${trip.passengers}", 18f, Palette.GOOD))
            })
            addView(ui.card().apply {
                addView(ui.text("오늘의 미션", 22f, Palette.MUTED, bold = true))
                h.missions.forEach { m ->
                    addView(ui.text("${if (m.done) "✅" else "⬜"}  ${m.text}  (${m.progress}/${m.goal})", 22f, if (m.done) Palette.GOOD else Palette.INK))
                }
            })
            addView(ui.card().apply {
                addView(ui.text("🪙 ${h.wallet.coins}    💎 ${h.wallet.gems}    건물 게이지 ${h.wallet.buildGauge}%", 22f))
                addView(ui.bar(h.wallet.buildGauge, 100, Palette.GOOD))
                if (h.wallet.buildGauge >= 100) addView(ui.text("게이지가 찼어요. 휴대폰에서 건물을 지어요.", 18f, Palette.GOOD))
            })
            addView(ui.row().apply {
                addView(ui.button("미니게임") { host.navigate(GamesScreen(host)) }, ui.weight(1f))
                addView(ui.button("휴대폰 연결", primary = false) { host.navigate(PhoneLinkScreen(host)) }, ui.weight(1f))
            }, ui.matchWidth())
        }

        val body = ui.row(32).apply {
            gravity = Gravity.TOP
            setPadding(ui.dp(32), ui.dp(24), ui.dp(32), ui.dp(24))
            addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            addView(right, LinearLayout.LayoutParams(0, -2, 1.4f))
        }
        return ScrollView(host).apply { addView(body) }
    }

    private fun stat(value: String, label: String) = ui.column(0).apply {
        addView(ui.text(value, 36f, bold = true))
        addView(ui.text(label, 18f, Palette.MUTED))
    }

    private fun feed(amount: Int) {
        CarApi.feed(amount) { r ->
            r.onSuccess { (evolved, h) ->
                home = h
                note = if (evolved) "와! 진화했어! 휴대폰 도감에 새 모습이 생겼어." else "냠냠! 경험치가 올랐어."
                if (evolved) host.speak("와! 진화했어!")
            }.onFailure { note = "지금은 먹이를 줄 수 없어요. 서버 연결을 확인해 주세요." }
            host.refresh(this)
        }
    }

    override fun onReturn() = load()

    override fun onLeave() { TripEvents.unlisten(onTrip) }
}

class PhoneLinkScreen(host: MainActivity) : Screen(host) {
    override fun build() = page("휴대폰 연결", showBack = true) {
        addView(ui.card().apply {
            addView(ui.text("연결 코드", 20f, Palette.MUTED))
            addView(ui.text(Onboarding.linkCode(host).chunked(3).joinToString(" "), 56f, Palette.ACCENT, bold = true))
            addView(ui.text("휴대폰에서 붕붕이 웹 → 내 차와 연결에 입력하세요. 도착 정산 알림, 건물 짓기, 꾸미기, 동승자 초대를 휴대폰에서 할 수 있어요.", 20f, Palette.MUTED))
        })
    }
}

class GamesScreen(host: MainActivity) : Screen(host) {
    private var ev: Boolean? = null

    init {
        runCatching {
            host.vehicle.getEvBattery().getEvBatteryStateOfCharge(
                { host.main.post { ev = true; host.refresh(this) } },
                { host.main.post { ev = false; host.refresh(this) } },
            )
        }.onFailure { ev = false }
    }

    override fun build() = page("미니게임", showBack = true) {
        val played = host.gameSessionStartedAt
        if (played > 0 && System.currentTimeMillis() - played > 5 * 60_000) {
            addView(ui.text("5분 넘게 놀았어요. 잠깐 쉬어 가요 🙂", 22f, Palette.WARN))
        }
        addView(ui.text("정차(P) 중에만 할 수 있어요. 출발하면 바로 닫혀요.", 20f, Palette.MUTED))
        addView(gameCard("🫧 붕붕이 목욕", "와이퍼를 켰다 끄면 거품이 하나씩 닦여요. 30초 안에 모두 닦아 주세요. 보상: 기분", true, null) {
            start(BathScreen(host))
        })
        val runOk = ev == true
        val reason = when (ev) { null -> "차량 확인 중…"; false -> "전기차에서만 열려요(정지 상태 조향·공회전 부담)"; else -> null }
        addView(gameCard("🏁 붕붕이 달리기", "핸들을 조금(±30°)만 기울여 길을 피하고, 브레이크로 점프해요. 40초. 보상: 코인", runOk, reason) {
            start(RunScreen(host))
        })
    }

    private fun start(screen: Screen) {
        if (host.gameSessionStartedAt == 0L) host.gameSessionStartedAt = System.currentTimeMillis()
        host.navigate(screen)
    }

    private fun gameCard(title: String, desc: String, enabled: Boolean, reason: String?, onStart: () -> Unit) = ui.card().apply {
        addView(ui.text(title, 28f, bold = true))
        addView(ui.text(desc, 20f, Palette.MUTED))
        if (reason != null) addView(ui.text(reason, 18f, Palette.WARN))
        addView(ui.button("시작") { onStart() }.apply { isEnabled = enabled; alpha = if (enabled) 1f else 0.4f }, ui.matchWidth())
    }
}

/** 미니게임 공통: 결과를 서버에 보내고 보상을 보여 준다. */
abstract class GameScreen(host: MainActivity, private val game: String) : Screen(host) {
    protected var result: String? = null
    protected var finished = false
    protected val inputs = host.newInputVehicle()
    private var released = false

    protected fun finish(score: Int) {
        if (finished) return
        finished = true
        stopInputs()
        result = "보내는 중…"
        host.refresh(this)
        CarApi.minigame(game, score) { r ->
            result = r.fold(
                { (mood, coins) -> if (coins > 0) "점수 $score · 코인 +$coins" else "닦은 거품 $score · 기분 +$mood" },
                { "결과를 보내지 못했어요" },
            )
            host.refresh(this)
        }
    }

    protected fun resultView(): View = ui.column(16).apply {
        gravity = Gravity.CENTER
        addView(ui.text("끝!", 40f, bold = true).apply { gravity = Gravity.CENTER })
        addView(ui.text(result.orEmpty(), 28f, Palette.ACCENT).apply { gravity = Gravity.CENTER })
        addView(ui.button("돌아가기") { host.back() }, LinearLayout.LayoutParams(ui.dp(360), -2))
    }

    abstract fun stopInputs()

    override fun onLeave() {
        stopInputs()
        if (!released) { released = true; runCatching { inputs.release() } }
    }
}

class BathScreen(host: MainActivity) : GameScreen(host, "bath") {
    private val total = 6
    private var left = total
    private var secondsLeft = 30
    private var lastOn = false
    private var avatar: BungbungView? = null
    private var timerText: android.widget.TextView? = null
    private var home: Home? = null
    private val area = ai.pleos.playground.vehicle.api.model.area.WindowArea(ai.pleos.playground.vehicle.constant.area.WindowValue.FRONT_WINDSHIELD)
    private var touchFallback = false
    private val wiper = object : ai.pleos.playground.vehicle.listener.WiperStateListener {
        override fun onWiperStateUpdated(state: ai.pleos.playground.vehicle.constant.control.WiperState?) {
            host.main.post {
                val on = state == ai.pleos.playground.vehicle.constant.control.WiperState.ON
                if (on && !lastOn) wipe()
                lastOn = on
            }
        }
        override fun onFailed(e: Exception) { host.main.post { touchFallback = true } }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (finished) return
            secondsLeft--
            timerText?.text = "남은 시간 ${secondsLeft}초 · 거품 ${left}개"
            if (secondsLeft <= 0) finish(total - left) else host.main.postDelayed(this, 1000)
        }
    }

    init {
        CarApi.home { r -> r.onSuccess { home = it; avatar?.bung = it.bung } }
        runCatching { inputs.getWiper().registerWiperState(area, wiper) }.onFailure { touchFallback = true }
        host.main.postDelayed(tick, 1000)
    }

    private fun wipe() {
        if (finished || left == 0) return
        left--
        avatar?.bubbles = left
        timerText?.text = "남은 시간 ${secondsLeft}초 · 거품 ${left}개"
        if (left == 0) finish(total)
    }

    override fun build(): View {
        if (finished) return resultView()
        return page("붕붕이 목욕", showBack = true) {
            timerText = ui.text("남은 시간 ${secondsLeft}초 · 거품 ${left}개", 26f, Palette.ACCENT, bold = true)
            addView(timerText)
            addView(ui.text(if (touchFallback) "와이퍼를 읽을 수 없어 화면을 눌러 닦아요" else "와이퍼를 켰다 끄세요", 22f, Palette.MUTED))
            avatar = BungbungView(host).apply {
                bung = home?.bung
                bubbles = left
                if (touchFallback) setOnClickListener { wipe() }
            }
            addView(avatar, LinearLayout.LayoutParams(-1, ui.dp(420)))
        }
    }

    override fun stopInputs() {
        host.main.removeCallbacks(tick)
        runCatching { inputs.getWiper().unregisterWiperState(area, wiper) }
    }
}

class RunScreen(host: MainActivity) : GameScreen(host, "run") {
    private val game = RunGameView(host) { score -> host.main.post { finish(score) } }
    private val brake = object : ai.pleos.playground.vehicle.listener.BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) { if (pressed == true) host.main.post { game.jump() } }
        override fun onFailed(e: Exception) = Unit
    }
    private val poll = object : Runnable {
        override fun run() {
            if (finished) return
            runCatching {
                inputs.getSteeringWheel().getCurrentSteeringWheelAngle({ a -> if (a != null) game.steer(-a) }, {})
            }
            host.main.postDelayed(this, 33)
        }
    }

    init {
        runCatching { inputs.getBrake().registerBrakePedalStatus(brake) }
        host.main.post(poll)
    }

    override fun build(): View {
        if (finished) return resultView()
        return FrameLayout(host).apply {
            (game.parent as? android.view.ViewGroup)?.removeView(game)
            addView(game, FrameLayout.LayoutParams(-1, -1))
            addView(ui.button("그만하기", primary = false) { host.back() }, FrameLayout.LayoutParams(ui.dp(220), -2).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = ui.dp(16); rightMargin = ui.dp(16)
            })
        }
    }

    override fun stopInputs() {
        game.stop()
        host.main.removeCallbacks(poll)
        runCatching { inputs.getBrake().unregisterBrakePedalStatus(brake) }
    }
}
