package com.example.myapplication

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Locale

object Settings {
    private const val ONBOARDED = "onboarded"
    private const val VOICE = "voice"
    fun onboarded(c: Context) = TripEvents.prefs(c).getBoolean(ONBOARDED, false)
    fun finishOnboarding(c: Context) = TripEvents.prefs(c).edit().putBoolean(ONBOARDED, true).apply()
    /** 음성 안내는 켠 사람만. 기본은 꺼짐. */
    fun voice(c: Context) = TripEvents.prefs(c).getBoolean(VOICE, false)
    fun setVoice(c: Context, on: Boolean) = TripEvents.prefs(c).edit().putBoolean(VOICE, on).apply()
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

private fun Screen.twoColumns(left: View, right: View, leftWeight: Float = 1f, rightWeight: Float = 1f) = ui.row(24).apply {
    gravity = Gravity.TOP
    addView(left, LinearLayout.LayoutParams(0, -2, leftWeight))
    addView(right, LinearLayout.LayoutParams(0, -2, rightWeight))
}

private fun Screen.stat(value: String, label: String, color: Int = Palette.INK) = ui.column(0).apply {
    addView(ui.text(value, 40f, color, bold = true))
    addView(ui.text(label, 18f, Palette.MUTED))
}

private fun fmt(n: Int) = String.format(Locale.KOREA, "%,d", n)

private fun Screen.loading(error: String?, retry: () -> Unit): View = LinearLayout(host).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    addView(ui.text(error ?: "불러오는 중…", 28f).apply { gravity = Gravity.CENTER })
    if (error != null) addView(ui.button("다시 시도") { retry() }, LinearLayout.LayoutParams(ui.dp(320), -2).apply { topMargin = ui.dp(24) })
}

/** 설치 후 첫 P: 게임 방법과 권한 용도를 먼저 보여 주고, 시작할 때 권한을 묻는다. */
class OnboardingScreen(host: MainActivity) : Screen(host) {
    override fun build() = page("로드랜드에 오신 걸 환영해요", showBack = false) {
        addView(ui.text("안전하게 달린 길이 내 땅이 됩니다.", 26f, Palette.ACCENT, bold = true))
        addView(ui.card().apply {
            addView(ui.text("🚗  평소처럼 달리면 지나간 구간마다 크레딧이 쌓여요. 주행 중에는 숫자만 보여요."))
            addView(ui.text("🛑  급제동이나 안전 경고가 있었던 구간은 보상이 없어요. 서두르면 손해예요."))
            addView(ui.text("🅿️  주차(P)하면 지나온 땅을 사고 키울 수 있어요. 땅은 시간이 지나면 수익을 내요."))
        })
        addView(ui.card().apply {
            addView(ui.text("시작하면 권한 두 개를 물어봐요", 20f, Palette.MUTED))
            addView(ui.text("📍 위치: 지나간 길을 육각형 구간으로 바꿀 때만 써요. 원래 좌표는 차 밖으로 보내지 않아요.", 20f))
            addView(ui.text("⏱️ 차량 속도(화면에는 '알 수 없는 작업'으로 보일 수 있어요): 거리와 급제동을 셀 때만 써요.", 20f))
        })
        addView(ui.button("시작하기") {
            Settings.finishOnboarding(host)
            host.ensurePermissions()
            host.replaceRoot(HomeScreen(host))
        }, ui.matchWidth())
    }
}

/** 정차 중 첫 화면: 지갑, 방치 수익, 마지막 주행. 새 주행이 정산되면 도착 요약을 띄운다. */
class HomeScreen(host: MainActivity) : Screen(host) {
    private var home: Home? = null
    private var error: String? = null
    private var note: String? = null
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
                home = h; error = null
                val unseen = TripEvents.unseenTripId(host)
                val trip = h.latestTrip
                if (unseen != null && trip?.id == unseen && host.isShowing(this)) {
                    TripEvents.markSeen(host)
                    host.navigate(ArrivalScreen(host, trip, h))
                    return@home
                }
            }.onFailure { error = "서버에 연결하지 못했어요" }
            host.refresh(this)
            if (reloadAfter) { reloadAfter = false; load() }
        }
    }

    private fun collect() {
        CarApi.collect { r ->
            note = r.fold({ if (it > 0) "방치 수익 ${fmt(it)} 크레딧을 받았어요" else "아직 쌓인 수익이 없어요" }, { "지금은 받을 수 없어요" })
            load()
        }
    }

    override fun build(): View {
        val h = home ?: return loading(error) { load() }
        val left = ui.column(16).apply {
            addView(ui.card().apply {
                addView(ui.text("내 크레딧", 20f, Palette.MUTED))
                addView(ui.text(fmt(h.credits), 56f, Palette.ACCENT, bold = true))
                addView(ui.row(40).apply {
                    addView(stat("${h.ownedTiles}", "내 땅"))
                    addView(stat(fmt(h.incomePerHour), "시간당 수익"))
                    addView(stat("${h.buyableTiles}", "살 수 있는 땅"))
                })
            })
            addView(ui.card().apply {
                addView(ui.text("쌓인 방치 수익", 20f, Palette.MUTED))
                addView(ui.text("${fmt(h.pendingIncome)} 크레딧", 40f, Palette.GOOD, bold = true))
                addView(ui.text("최대 ${h.idleCapHours}시간까지 쌓여요. 탈 때마다 받아 가세요.", 18f, Palette.MUTED))
                addView(ui.button("한 번에 수령") { collect() }.apply { isEnabled = h.pendingIncome > 0; alpha = if (h.pendingIncome > 0) 1f else 0.5f }, ui.matchWidth())
                note?.let { addView(ui.text(it, 20f, Palette.GOOD)) }
            })
        }
        val right = ui.column(16).apply {
            addView(ui.card().apply {
                addView(ui.text("오늘", 20f, Palette.MUTED))
                addView(ui.row(40).apply {
                    addView(stat(String.format(Locale.KOREA, "%.1f", h.today.km), "km"))
                    addView(stat("${h.today.tilesPassed}", "지나간 구간"))
                    addView(stat("${h.today.driveCredits}/${h.today.driveCap}", "주행 크레딧"))
                })
                addView(ui.bar(h.today.driveCredits, h.today.driveCap))
                addView(ui.text("🛡️ 안전 연속 기록 ${h.safeStreak}회", 20f))
            })
            h.latestTrip?.let { t ->
                addView(ui.card().apply {
                    addView(ui.text("마지막 주행", 20f, Palette.MUTED))
                    addView(ui.text("안전 점수 ${t.safetyScore} · +${t.credits} 크레딧 · 새 구간 ${t.newTiles}", 24f))
                    addView(ui.button("도착 요약 다시 보기", primary = false) { host.navigate(ArrivalScreen(host, t, h)) }, ui.matchWidth())
                })
            }
            addView(ui.row().apply {
                addView(ui.button("내 영토 지도") { host.navigate(TerritoryScreen(host)) }, ui.weight(1f))
                addView(ui.button("기록", primary = false) { host.navigate(RecordsScreen(host)) }, ui.weight(1f))
            }, ui.matchWidth())
            val voice = Settings.voice(host)
            addView(ui.button(if (voice) "🔊 음성 안내 켜짐" else "🔈 음성 안내 꺼짐", primary = false) {
                Settings.setVoice(host, !voice)
                host.refresh(this@HomeScreen)
            }, ui.matchWidth())
        }
        return page("로드랜드", showBack = false) { addView(twoColumns(left, right)) }
    }

    override fun onReturn() = load()

    override fun onLeave() { TripEvents.unlisten(onTrip) }
}

/** P단 전환 때 자동으로 뜨는 한 장 요약: 이번 경로, 안전 점수, 얻은 크레딧, 쌓인 방치 수익. */
class ArrivalScreen(host: MainActivity, private val trip: Trip, private var home: Home) : Screen(host) {
    private var territory: List<LandTile> = emptyList()
    private var note: String? = null

    init {
        CarApi.territory { r -> r.onSuccess { territory = it; host.refresh(this) } }
        if (Settings.voice(host)) host.speak("도착했어요. 안전 점수 ${trip.safetyScore}점, ${trip.credits} 크레딧을 얻었어요.")
    }

    private fun collect() {
        CarApi.collect { r ->
            r.onSuccess { got ->
                note = "방치 수익 ${fmt(got)} 크레딧을 받았어요"
                home = home.copy(credits = home.credits + got, pendingIncome = 0)
            }.onFailure { note = "지금은 받을 수 없어요" }
            host.refresh(this)
        }
    }

    override fun build(): View {
        val map = HexMapView(host).apply {
            tiles = territory
            route = trip.tiles
        }
        val left = ui.column(12).apply {
            addView(map, LinearLayout.LayoutParams(-1, ui.dp(420)))
            addView(ui.text("🟩 안전 구간  🟦 에코·크루즈  🟧 급제동·경고(보상 없음)", 18f, Palette.MUTED))
        }
        val scoreColor = when {
            trip.safetyScore >= 90 -> Palette.GOOD
            trip.safetyScore >= 70 -> Palette.ACCENT
            else -> Palette.WARN
        }
        val right = ui.column(14).apply {
            addView(ui.card().apply {
                addView(ui.row(40).apply {
                    addView(stat("${trip.safetyScore}", "안전 점수", scoreColor))
                    addView(stat(String.format(Locale.KOREA, "%.1f", trip.km), "km"))
                    addView(stat("${trip.newTiles}", "새 구간"))
                })
                if (trip.rejected) addView(ui.text("기록을 확인할 수 없어 이번 주행은 보상이 없어요.", 20f, Palette.WARN))
            })
            addView(ui.card(6).apply {
                addView(ui.text("+${fmt(trip.credits)} 크레딧", 40f, Palette.ACCENT, bold = true))
                addView(ui.text("안전하게 지난 구간 ${trip.clean / Rules.TILE_CREDIT}칸  +${trip.clean}", 20f))
                if (trip.assistBonus > 0) addView(ui.text("에코·크루즈 보너스  +${trip.assistBonus}", 20f))
                if (trip.mannerBonus > 0) addView(ui.text("깜빡이 매너 보너스 (${trip.signaledLaneChanges}/${trip.laneChanges})  +${trip.mannerBonus}", 20f))
                if (trip.excluded > 0) addView(ui.text("급제동 ${trip.hardBrakes}회 · 안전 경고 ${trip.warnings}회 → ${trip.excluded}칸 보상 없음", 20f, Palette.WARN))
                if (trip.laneChanges > 0 && trip.mannerBonus == 0) addView(ui.text("깜빡이 없이 차선 변경 ${trip.laneChanges - trip.signaledLaneChanges}회", 20f, Palette.WARN))
                if (trip.capReached) addView(ui.text("오늘 주행 크레딧 상한에 닿았어요", 20f, Palette.MUTED))
            })
            addView(ui.card().apply {
                addView(ui.text("그동안 쌓인 방치 수익", 20f, Palette.MUTED))
                addView(ui.text("${fmt(home.pendingIncome)} 크레딧", 36f, Palette.GOOD, bold = true))
                addView(ui.button("한 번에 수령") { collect() }.apply { isEnabled = home.pendingIncome > 0; alpha = if (home.pendingIncome > 0) 1f else 0.5f }, ui.matchWidth())
                note?.let { addView(ui.text(it, 20f, Palette.GOOD)) }
            })
            addView(ui.row().apply {
                addView(ui.button("땅 사러 가기") { host.back(); host.navigate(TerritoryScreen(host)) }, ui.weight(1f))
                addView(ui.button("닫기", primary = false) { host.back() }, ui.weight(1f))
            }, ui.matchWidth())
        }
        return page("도착 요약", showBack = false) { addView(twoColumns(left, right, 1.1f, 1f)) }
    }
}

/** 내 영토 지도. 지나간 구간을 눌러 사고, 내 땅을 눌러 올린다. */
class TerritoryScreen(host: MainActivity) : Screen(host) {
    private var tiles: List<LandTile>? = null
    private var credits = 0
    private var selected: String? = null
    private var note: String? = null
    private var error: String? = null
    private var map: HexMapView? = null

    init { load() }

    private fun load() {
        CarApi.territory { r ->
            r.onSuccess { tiles = it; error = null }.onFailure { error = "서버에 연결하지 못했어요" }
            CarApi.home { h ->
                h.onSuccess { credits = it.credits }
                host.refresh(this)
            }
        }
    }

    private fun act(call: (String, (Result<LandTile>) -> Unit) -> Unit, done: String) {
        val id = selected ?: return
        call(id) { r ->
            note = r.fold({ done }, { (it as? CarApi.GameException)?.message ?: "지금은 할 수 없어요" })
            load()
        }
    }

    override fun build(): View {
        val all = tiles ?: return loading(error) { load() }
        val tile = all.find { it.id == selected }
        // 다시 그려도 지도를 옮긴 위치가 유지되게 같은 뷰를 다시 붙인다.
        val mapView = map ?: HexMapView(host).also { v ->
            map = v
            v.onSelect = { id -> selected = id; note = null; host.refresh(this) }
            // 내 땅 주변을 먼저 보여 준다. 내 땅이 없으면 지나간 길 전체.
            val owned = all.filter { it.owned }.map { it.id }
            // 타일이 약 10m라 경로 전체를 맞추면 너무 작아진다. 거리 단위로 확대한 채 가운데를 보여 준다.
            v.post {
                val ids = owned.ifEmpty { all.filter { it.visited }.map { it.id } }
                v.fit(ids, minSpan = 24f, minSize = HexMapView.STREET_SIZE)
            }
        }
        (mapView.parent as? android.view.ViewGroup)?.removeView(mapView)
        mapView.tiles = all
        mapView.selected = selected
        val panel = ui.column(14).apply {
            addView(ui.card().apply {
                addView(ui.text("내 크레딧", 20f, Palette.MUTED))
                addView(ui.text(fmt(credits), 44f, Palette.ACCENT, bold = true))
            })
            addView(ui.card().apply {
                if (tile == null) {
                    addView(ui.text("지도에서 구간을 눌러 보세요", 24f))
                    addView(ui.text("파란 테두리: 지나가서 살 수 있는 땅(빈 땅은 5 × (내 땅 수 + 1))\n초록: 내 땅(숫자는 레벨, 최고 60)\n빨강: 다른 차의 땅(거래 뒤 다시 지나가면 거래가 + 5에 빼앗기)\n노란 점: 자주 다니는 길", 20f, Palette.MUTED))
                } else {
                    val title = when {
                        tile.owned -> "내 땅 · 레벨 ${tile.level}"
                        tile.ownedByOther -> "다른 차의 땅"
                        tile.visited -> "지나간 빈 땅"
                        else -> "아직 안 가 본 곳"
                    }
                    addView(ui.text(title, 28f, bold = true))
                    tile.price?.let { addView(ui.text("마지막 거래가 $it", 20f, Palette.MUTED)) }
                    if (tile.visited) addView(ui.text("이 칸을 지난 주행: 80점 넘음 ${tile.goodTrips} · 80점 이하 ${tile.badTrips}", 20f, Palette.MUTED))
                    if (tile.owned) {
                        val bonus = tile.incomePerHour / tile.level // 5 ± 주행 수(1~10), 서버가 계산
                        addView(ui.text("1시간마다 ${fmt(tile.incomePerHour)} 크레딧 (레벨 ${tile.level} × $bonus) · 쌓인 수익 ${fmt(tile.pending)}", 22f))
                        val cost = tile.upgradeCost
                        if (cost == null) addView(ui.text("최고 레벨이에요", 22f, Palette.MUTED))
                        else addView(ui.button("레벨 ${tile.level + 1}로 올리기 · $cost") { act(CarApi::upgrade, "레벨 ${tile.level + 1}이 됐어요") }
                            .apply { isEnabled = credits >= cost; alpha = if (credits >= cost) 1f else 0.5f }, ui.matchWidth())
                    } else {
                        val cost = tile.buyCost
                        if (tile.mustRevisit) addView(ui.text("주인이 바뀐 뒤로 아직 안 지나갔어요. 다시 지나가면 ${(tile.price ?: 0) + 5}에 빼앗을 수 있어요", 22f, Palette.WARN))
                        else if (cost == null) addView(ui.text("직접 지나가야 살 수 있어요", 22f, Palette.MUTED))
                        else if (tile.ownedByOther) {
                            addView(ui.text("빼앗으면 레벨 1부터 다시 시작해요. 낸 크레딧은 누구에게도 가지 않아요.", 20f, Palette.MUTED))
                            addView(ui.button("빼앗기 · $cost") { act(CarApi::buy, "내 땅이 됐어요!") }
                                .apply { isEnabled = credits >= cost; alpha = if (credits >= cost) 1f else 0.5f }, ui.matchWidth())
                        }
                        else addView(ui.button("이 땅 사기 · $cost") { act(CarApi::buy, "내 땅이 됐어요!") }
                            .apply { isEnabled = credits >= cost; alpha = if (credits >= cost) 1f else 0.5f }, ui.matchWidth())
                    }
                }
                note?.let { addView(ui.text(it, 22f, Palette.GOOD)) }
            })
        }
        val left = android.widget.FrameLayout(host).apply {
            addView(mapView, android.widget.FrameLayout.LayoutParams(-1, ui.dp(560)))
            addView(ui.column(10).apply {
                addView(ui.button("＋", primary = false) { mapView.zoom(1.4f) }, LinearLayout.LayoutParams(ui.dp(80), -2))
                addView(ui.button("－", primary = false) { mapView.zoom(1 / 1.4f) }, LinearLayout.LayoutParams(ui.dp(80), -2))
            }, android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.TOP).apply { setMargins(0, ui.dp(12), ui.dp(12), 0) })
        }
        return page("내 영토", showBack = true) { addView(twoColumns(left, panel, 1.6f, 1f)) }
    }
}

/** 안전 운전 연속 기록과 순위. */
class RecordsScreen(host: MainActivity) : Screen(host) {
    private var records: Records? = null
    private var ranking: Ranking? = null
    private var error: String? = null

    init { load() }

    private fun load() {
        CarApi.records { r ->
            r.onSuccess { records = it; error = null }.onFailure { error = "서버에 연결하지 못했어요" }
            host.refresh(this)
        }
        // 순위는 없어도 화면은 보여 준다.
        CarApi.ranking { r -> r.onSuccess { ranking = it; host.refresh(this) } }
    }

    override fun build(): View {
        val rec = records ?: return loading(error) { load() }
        val card = ui.card().apply {
            addView(ui.text("안전 운전 연속 기록", 20f, Palette.MUTED))
            addView(ui.row(40).apply {
                addView(stat("${rec.safeStreak}", "지금", Palette.GOOD))
                addView(stat("${rec.bestSafeStreak}", "최고"))
                addView(stat("${rec.ownedTiles}", "내 땅"))
            })
            addView(ui.text("안전 점수 90점 이상 주행이 이어지면 늘어나요.", 18f, Palette.MUTED))
            addView(ui.text("최근 점수  " + rec.recentScores.takeLast(7).joinToString("  "), 22f))
            ranking?.let { rk ->
                val land = rk.landRank?.let { "땅 점수 ${it}위 (${rk.landScore})" } ?: "땅 점수 순위 없음"
                val weekly = rk.weeklyRank?.let { "이번 주 주행 ${it}위 (${rk.weeklyCredits})" } ?: "이번 주 주행 순위 없음"
                addView(ui.text("$land · $weekly", 22f, Palette.ACCENT))
            }
        }
        return page("기록", showBack = true) { addView(card) }
    }
}
