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

/** 메뉴는 고정하고, 각 화면의 스크롤과 콘텐츠는 오른쪽 영역 안에 둔다. */
fun Screen.withSidebar(content: View): View = ui.row(0).apply {
    gravity = Gravity.TOP
    val sidebar = ui.column(12).apply {
        setBackgroundColor(Palette.CARD)
        setPadding(ui.dp(20), ui.dp(32), ui.dp(20), ui.dp(24))
        addView(ui.text("로드랜드", 28f, Palette.ACCENT, bold = true))
        addView(ui.text("안전하게 달린 나의 영토", 16f, Palette.MUTED))
        addView(View(host), LinearLayout.LayoutParams(1, ui.dp(24)))
        fun menu(key: String, title: String, selected: Boolean) {
            addView(ui.button(title, primary = selected) { host.selectMenu(key) }.apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(ui.dp(20), 0, ui.dp(12), 0)
                isSelected = selected
                contentDescription = if (selected) "$title, 선택됨" else title
                background = ui.rounded(if (selected) Palette.ACCENT else Palette.CARD, 16)
            }, ui.matchWidth())
        }
        menu("home", "홈", this@withSidebar is HomeScreen || this@withSidebar is ArrivalScreen)
        menu("territory", "내 영토", this@withSidebar is TerritoryScreen)
        menu("records", "주행 기록", this@withSidebar is RecordsScreen)
        menu("settings", "설정", this@withSidebar is SettingsScreen)
    }
    addView(ScrollView(host).apply { isFillViewport = true; addView(sidebar) }, LinearLayout.LayoutParams(ui.dp(240), -1))
    addView(View(host).apply { setBackgroundColor(Palette.EDGE) }, LinearLayout.LayoutParams(ui.dp(1), -1))
    addView(content, LinearLayout.LayoutParams(0, -1, 1f))
}

private fun Screen.page(title: String, showBack: Boolean, body: LinearLayout.() -> Unit): View {
    val u = host.ui
    val content = u.column(16).apply {
        setPadding(u.dp(32), u.dp(24), u.dp(32), u.dp(32))
        addView(u.row().apply {
            if (showBack) addView(u.button("‹ 뒤로", primary = false) { host.back() }, LinearLayout.LayoutParams(u.dp(160), -2))
            addView(u.text(title, 34f, bold = true))
        })
        body()
    }
    return ScrollView(host).apply { addView(content) }
}

private fun Screen.twoColumns(left: View, right: View, leftWeight: Float = 1f, rightWeight: Float = 1f): View {
    val wide = host.resources.configuration.screenWidthDp - 241 >= 1000
    return (if (wide) ui.row(24) else ui.column(24)).apply {
        gravity = Gravity.TOP
        addView(left, if (wide) LinearLayout.LayoutParams(0, -2, leftWeight) else ui.matchWidth())
        addView(right, if (wide) LinearLayout.LayoutParams(0, -2, rightWeight) else ui.matchWidth())
    }
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
            addView(ui.label("시작하면 권한 두 개를 물어봐요"))
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
        val trip = h.latestTrip
        fun metric(label: String, value: String, detail: String, accent: Boolean = false) = ui.card(12).apply {
            addView(ui.text(label, 22f, Palette.MUTED, bold = true))
            addView(ui.text(value, 52f, if (accent) Palette.ACCENT else Palette.INK, bold = true).apply {
                setAutoSizeTextTypeUniformWithConfiguration(28, 52, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
                maxLines = 1
            }, ui.matchWidth())
            addView(ui.text(detail, 20f, Palette.MUTED))
        }
        val metrics = listOf(
            metric("보유 크레딧", fmt(h.credits), "땅 구매 · 레벨 올리기에 사용", true),
            metric("내 땅", "${fmt(h.ownedTiles)}칸", "시간당 ${fmt(h.incomePerHour)} 크레딧"),
            metric("오늘 주행 거리", String.format(Locale.KOREA, "%.1f km", h.today.km), "${h.today.trips}회 주행 · ${fmt(h.today.tilesPassed)}구간"),
            metric("최근 안전 점수", trip?.let { "${it.safetyScore}점" } ?: "—", if (trip == null) "첫 주행 후 표시돼요" else "안전 운전 ${h.safeStreak}회 연속")
        )
        val overview = ui.column(16).apply {
            val columns = if (host.resources.configuration.screenWidthDp - 241 >= 1200) 4 else 2
            metrics.chunked(columns).forEach { cards ->
                addView(ui.row(16).apply {
                    gravity = Gravity.TOP
                    cards.forEach { addView(it, ui.weight(1f)) }
                }, ui.matchWidth())
            }
        }
        val income = ui.column(20).apply {
            addView(ui.card(16).apply {
                addView(ui.text("지금 받을 수익", 26f, bold = true))
                addView(ui.text("${fmt(h.pendingIncome)} 크레딧", 48f, Palette.ACCENT, bold = true))
                addView(ui.text("내 땅이 모은 수익이에요. 최대 ${h.idleCapHours}시간까지 쌓여요.", 22f, Palette.MUTED))
                addView(ui.button(if (h.pendingIncome > 0) "수익 받기" else "수익이 쌓이는 중") { collect() }.apply {
                    isEnabled = h.pendingIncome > 0
                    alpha = if (isEnabled) 1f else 0.5f
                }, ui.matchWidth())
                note?.let { addView(ui.text(it, 22f, Palette.GOOD)) }
            })
            addView(ui.card(16).apply {
                addView(ui.text("확장할 수 있는 영토", 26f, bold = true))
                addView(ui.text("${fmt(h.buyableTiles)}칸", 40f, bold = true))
                addView(ui.text("지나온 구간의 위치와 구매 가격을 지도에서 확인하세요.", 22f, Palette.MUTED))
                addView(ui.button("지도에서 확인", primary = false) { host.selectMenu("territory") }, ui.matchWidth())
            })
        }
        val activity = ui.column(20).apply {
            addView(ui.card(16).apply {
                addView(ui.text("오늘 받은 주행 크레딧", 26f, bold = true))
                addView(ui.text("${fmt(h.today.driveCredits)} / ${fmt(h.today.driveCap)}", 40f, bold = true))
                addView(ui.bar(h.today.driveCredits, h.today.driveCap))
                val remaining = (h.today.driveCap - h.today.driveCredits).coerceAtLeast(0)
                addView(ui.text(if (remaining == 0) "오늘의 주행 보상을 모두 받았어요" else "오늘 받을 수 있는 보상 ${fmt(remaining)} 크레딧", 22f, Palette.MUTED))
            })
            addView(ui.card(16).apply {
                addView(ui.text("최근 주행 요약", 26f, bold = true))
                if (trip == null) {
                    addView(ui.text("아직 주행 기록이 없어요", 28f))
                    addView(ui.text("첫 주행을 마치면 거리, 안전 점수와 보상을 여기서 확인할 수 있어요.", 22f, Palette.MUTED))
                } else {
                    addView(ui.text("+${fmt(trip.credits)} 크레딧", 40f, Palette.ACCENT, bold = true))
                    addView(ui.text(String.format(Locale.KOREA, "%.1f km 주행 · 새 구간 %s칸", trip.km, fmt(trip.newTiles)), 22f))
                    if (trip.rejected) addView(ui.text("기록을 확인할 수 없어 보상이 지급되지 않았어요.", 22f, Palette.WARN))
                    addView(ui.button("주행 자세히 보기", primary = false) { host.navigate(ArrivalScreen(host, trip, h)) }, ui.matchWidth())
                }
            })
        }
        return page("한눈에 보는 내 주행과 영토", showBack = false) {
            error?.let {
                addView(ui.text("최신 정보를 불러오지 못했어요. 이전 조회 값을 표시합니다.", 22f, Palette.WARN))
                addView(ui.button("새로고침", primary = false) { load() }, ui.matchWidth())
            }
            addView(overview, ui.matchWidth())
            addView(twoColumns(income, activity), ui.matchWidth())
        }
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
                addView(ui.label("그동안 쌓인 방치 수익"))
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
            v.exploreRegions = true
            v.post { v.showKorea() }
        }
        (mapView.parent as? android.view.ViewGroup)?.removeView(mapView)
        mapView.tiles = all
        mapView.selected = selected
        val panel = ui.column(14).apply {
            addView(ui.card().apply {
                if (tile == null) {
                    addView(ui.text("내 영토 찾아가기", 26f, bold = true))
                    addView(ui.text("전국 → 지역 → 주변 → 개별 땅", 22f, Palette.ACCENT))
                    addView(ui.text("소유한 땅과 내가 지나온 구간을 함께 보여줘요. 숫자를 누르면 가까이 볼 수 있어요.", 22f))
                    addView(ui.text("라임: 내 땅 · 레벨 올리기\n파랑: 지나온 땅 · 구매 가능 여부 확인\n빨강: 다른 차량이 소유한 땅", 20f, Palette.MUTED))
                    addView(ui.text("같은 구간은 한 번만 세며, 내 땅 수와 미소유 방문 구간 수를 구분해 표시해요.", 18f, Palette.MUTED))
                    addView(ui.text("지역 표시는 가까운 땅을 묶은 것으로 행정구역 경계와는 다릅니다.", 18f, Palette.MUTED))
                } else {
                    val title = when {
                        tile.owned -> "내 땅 · 레벨 ${tile.level}"
                        tile.ownedByOther -> "다른 차의 땅"
                        tile.visited -> "지나간 빈 땅"
                        else -> "아직 안 가 본 곳"
                    }
                    addView(ui.row(12).apply {
                        addView(ui.text(title, 28f, bold = true), ui.weight(1f))
                        addView(ui.button("닫기", primary = false) {
                            selected = null; note = null; host.refresh(this@TerritoryScreen)
                        }, LinearLayout.LayoutParams(ui.dp(100), -2))
                    })
                    val owner = when {
                        tile.owned -> "나 · 내 차량"
                        tile.ownedByOther -> "다른 차량"
                        else -> "없음 · 미소유 땅"
                    }
                    addView(ui.text("소유자  $owner", 24f, if (tile.ownedByOther) Palette.RIVAL else Palette.ACCENT, bold = true))
                    if (tile.ownedByOther) addView(ui.text("소유자 이름은 서버에서 제공하지 않아요.", 18f, Palette.MUTED))
                    addView(ui.text("구간 ${tile.id}", 18f, Palette.MUTED))
                    tile.price?.let { addView(ui.label("마지막 거래가 $it")) }
                    if (tile.visited) addView(ui.text("이 칸을 지난 주행: 80점 넘음 ${tile.goodTrips} · 80점 이하 ${tile.badTrips}", 20f, Palette.MUTED))
                    if (tile.owned) {
                        val bonus = tile.incomePerHour / tile.level.coerceAtLeast(1) // 5 ± 주행 수(1~10), 서버가 계산
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
        val details = ScrollView(host).apply {
            addView(panel)
            elevation = ui.dp(12).toFloat()
            // 위젯 내부의 스크롤과 터치가 뒤쪽 지도에 전달되지 않게 한다.
            isClickable = true
        }
        return object : android.widget.FrameLayout(host) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                details.layoutParams = (details.layoutParams as LayoutParams).apply {
                    width = minOf(ui.dp(440), (w * 0.46f).toInt())
                    height = minOf(ui.dp(650), (h - ui.dp(180)).coerceAtLeast(ui.dp(120)))
                }
            }
        }.apply {
            addView(mapView, android.widget.FrameLayout.LayoutParams(-1, -1))
            addView(ui.card(6).apply {
                elevation = ui.dp(8).toFloat()
                addView(ui.text("내 영토", 26f, bold = true))
                addView(ui.text("내 크레딧", 18f, Palette.MUTED))
                addView(ui.text(fmt(credits), 40f, Palette.ACCENT, bold = true))
                addView(ui.button("전국 보기", primary = false) {
                    selected = null; note = null; mapView.showKorea(); host.refresh(this@TerritoryScreen)
                }, ui.matchWidth())
            }, android.widget.FrameLayout.LayoutParams(ui.dp(280), -2, Gravity.START or Gravity.TOP).apply {
                setMargins(ui.dp(24), ui.dp(24), 0, 0)
            })
            addView(details, android.widget.FrameLayout.LayoutParams(ui.dp(440), ui.dp(650), Gravity.END or Gravity.BOTTOM).apply {
                setMargins(0, ui.dp(160), ui.dp(24), ui.dp(40))
            })
            addView(ui.column(10).apply {
                elevation = ui.dp(8).toFloat()
                addView(ui.button("＋", primary = false) { mapView.zoom(1.4f) }, LinearLayout.LayoutParams(ui.dp(80), -2))
                addView(ui.button("－", primary = false) { mapView.zoom(1 / 1.4f) }, LinearLayout.LayoutParams(ui.dp(80), -2))
            }, android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.BOTTOM).apply {
                setMargins(ui.dp(24), 0, 0, ui.dp(40))
            })
        }
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
            addView(ui.label("안전 운전 연속 기록"))
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
        return page("주행 기록", showBack = false) { addView(card) }
    }
}

class SettingsScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = page("설정", showBack = false) {
        val voice = Settings.voice(host)
        addView(ui.card().apply {
            addView(ui.text("음성 안내", 28f, bold = true))
            addView(ui.text("주차 후 도착 요약을 음성으로 알려드려요.", 22f, Palette.MUTED))
            addView(ui.button(if (voice) "음성 안내 켜짐" else "음성 안내 꺼짐", primary = voice) {
                Settings.setVoice(host, !voice)
                host.refresh(this@SettingsScreen)
            }, ui.matchWidth())
        })
    }
}
