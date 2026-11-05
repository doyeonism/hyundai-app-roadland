package com.example.myapplication

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** 화면 하나. 정차(P 또는 주차 브레이크) 중에만 보이고, 주행이 시작되면 [onLeave]가 불린 뒤 사라진다. */
abstract class Screen(val host: MainActivity) {
    val ui get() = host.ui
    abstract fun build(): View
    /** 다른 화면에서 돌아오거나 주차해서 다시 보일 때. */
    open fun onReturn() {}
    open fun onLeave() {}
}

/**
 * 차량 앱의 유일한 Activity. 화면 전환과 정차 게이트를 맡는다.
 * 기어 P 또는 주차 브레이크일 때만 게임 화면을 보여 주고, 그 밖에는 조작 요소가 없는 주행 위젯만 보여 준다.
 */
class MainActivity : Activity() {
    lateinit var ui: Ui
        private set
    val main = Handler(Looper.getMainLooper())
    private val vehicleLazy = lazy { Vehicle(applicationContext).also { it.initialize() } }
    val vehicle: Vehicle by vehicleLazy
    private lateinit var root: FrameLayout
    private val stack = ArrayDeque<Screen>()
    private var gearPark: Boolean? = null
    private var parkingBrake = false
    private var parked: Boolean? = null
    private var shown: Any? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var widgetTiles: TextView? = null
    private var widgetCredits: TextView? = null
    private val onWidget: (Widget?) -> Unit = { updateWidget(it) }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) { main.post { onGear(gear) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "gear listener failed", e) }
    }
    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(info: Telltales?) {
            info ?: return
            val on = info.telltales.any {
                (it.type == TelltaleType.PARKING_BRAKE || it.type == TelltaleType.ELECTRONIC_PARKING_BRAKE) && it.state != TelltaleState.OFF
            }
            main.post { parkingBrake = on; updateGate() }
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "telltale listener failed", e) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CarApi.init(this)
        ui = Ui(this)
        window.decorView.setBackgroundColor(Palette.BG)
        root = FrameLayout(this).apply { setBackgroundColor(Palette.BG) }
        setContentView(root)
        // 처음이면 온보딩에서 권한 설명을 읽은 뒤 묻는다. 그 전에도 받을 수 있는 신호(기어 등)는 모은다.
        if (Settings.onboarded(this)) ensurePermissions() else DriveService.start(this)
        stack.addLast(if (Settings.onboarded(this)) HomeScreen(this) else OnboardingScreen(this))
        render()
        TripEvents.listenWidget(onWidget)
        runCatching { vehicle.getGear().registerGearSelection(gearListener) }.onFailure { Log.w(TAG, "gear unavailable", it) }
        runCatching { vehicle.getSafety().registerTelltales(telltaleListener) }.onFailure { Log.w(TAG, "telltales unavailable", it) }
    }

    /**
     * 리스너 이벤트를 놓쳐도 게이트가 멈추지 않게 보이는 동안 기어를 주기적으로 다시 읽는다.
     * (에뮬레이터에서 기어 이벤트가 끊긴 적이 있다.)
     */
    private val gearPoll = object : Runnable {
        override fun run() {
            runCatching {
                vehicle.getGear().getCurrentGearSelectionState({ g -> main.post { onGear(g) } }, { Log.w(TAG, "gear read failed", it) })
            }
            main.postDelayed(this, 1500)
        }
    }

    override fun onResume() {
        super.onResume()
        main.removeCallbacks(gearPoll)
        main.post(gearPoll)
        // 못 보낸 주행이 남아 있으면 다시 보내 본다.
        if (Settings.onboarded(this)) DriveService.start(this)
    }

    override fun onPause() {
        main.removeCallbacks(gearPoll)
        super.onPause()
    }

    fun ensurePermissions() {
        val missing = RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) DriveService.start(this) else requestPermissions(missing.toTypedArray(), 1)
    }

    /** 권한을 받은 뒤 수집 서비스를 (다시) 연결한다. 거부해도 받을 수 있는 신호만으로 기록한다. */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        DriveService.reconnect(this)
    }

    private fun onGear(gear: VehicleGear?) {
        gear ?: return
        gearPark = gear == VehicleGear.GEAR_PARK
        updateGate()
    }

    private fun updateGate() {
        val gp = gearPark ?: return
        val nowParked = Rules.isParked(gp, parkingBrake)
        if (nowParked == parked) return
        parked = nowParked
        if (!nowParked) {
            // 주행 시작: 열려 있던 화면을 모두 닫고 첫 화면으로 돌아간다.
            while (stack.size > 1) stack.removeLast().onLeave()
            tts?.stop()
        }
        render()
        if (nowParked) stack.last().onReturn()
    }

    fun navigate(screen: Screen) {
        stack.addLast(screen)
        render()
    }

    fun replaceRoot(screen: Screen) {
        while (stack.isNotEmpty()) stack.removeLast().onLeave()
        stack.addLast(screen)
        render()
    }

    fun back() {
        if (stack.size > 1) {
            stack.removeLast().onLeave()
            render()
            stack.last().onReturn()
        } else finish()
    }

    @Deprecated("Activity back handling")
    override fun onBackPressed() = back()

    /** 바뀐 것이 있을 때만 다시 그린다(같은 화면을 반복해서 만들면 깜빡인다). */
    private fun render() {
        val target: Any = if (parked == true) stack.last() else if (parked == false) DRIVING else WAITING
        if (target === shown) return
        shown = target
        root.removeAllViews()
        widgetTiles = null; widgetCredits = null
        val view = when (target) {
            is Screen -> target.build()
            DRIVING -> drivingWidget()
            else -> waiting()
        }
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    /** 이미 보이는 화면을 새 데이터로 다시 그린다. */
    fun refresh(screen: Screen) {
        if (shown !== screen) return
        shown = null
        render()
    }

    fun isShowing(screen: Screen) = shown === screen

    private fun waiting(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(ui.text("차량 상태를 확인하고 있어요", 30f, Palette.INK, bold = true).apply { gravity = Gravity.CENTER })
    }

    /**
     * 주행 위젯: 숫자 두 개만 크게. 버튼·알림·소리 없음. 정차 중(신호 대기)에도 그대로 둔다.
     * 숫자는 제자리에서 바꿔 다시 그리지 않는다(깜빡임 방지).
     */
    private fun drivingWidget(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Palette.BG)
        val row = ui.row(60).apply { gravity = Gravity.CENTER }
        fun number(label: String): TextView {
            val value = ui.text("0", 96f, Palette.ACCENT, bold = true).apply { gravity = Gravity.CENTER }
            row.addView(ui.column(0).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(value)
                addView(ui.text(label, 26f, Palette.MUTED).apply { gravity = Gravity.CENTER })
            })
            return value
        }
        widgetTiles = number("오늘 지나간 구간")
        widgetCredits = number("누적 크레딧")
        addView(row)
        addView(ui.text("안전하게 달릴수록 크레딧이 커져요. 주차(P)하면 땅을 살 수 있어요.", 22f, Palette.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, ui.dp(32), 0, 0)
        })
        updateWidget(TripEvents.widget)
    }

    private fun updateWidget(w: Widget?) {
        w ?: return
        widgetTiles?.text = String.format(Locale.KOREA, "%,d", w.todayTiles)
        widgetCredits?.text = String.format(Locale.KOREA, "%,d", w.credits)
    }

    /** 정차 중에만 읽는다(음성 안내를 켠 사람만 부른다). */
    fun speak(line: String) {
        if (parked != true) return
        val engine = tts
        if (engine != null) {
            if (ttsReady) engine.speak(line, true)
            return
        }
        runCatching {
            TextToSpeech(applicationContext, Mode.ON_DEVICE).also { created ->
                tts = created
                created.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        main.post {
                            ttsReady = true
                            created.setLocale(Locale.KOREA)
                            if (parked == true) created.speak(line, true)
                        }
                    }
                    override fun onFailure(errorType: ai.pleos.playground.tts.constant.ErrorType) { Log.w(TAG, "tts init failed: $errorType") }
                })
            }
        }.onFailure { Log.w(TAG, "tts unavailable", it) }
    }

    override fun onDestroy() {
        while (stack.isNotEmpty()) stack.removeLast().onLeave()
        TripEvents.unlistenWidget(onWidget)
        if (vehicleLazy.isInitialized()) {
            runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
            runCatching { vehicle.getSafety().unregisterTelltales(telltaleListener) }
            runCatching { vehicle.release() }
        }
        runCatching { tts?.release() }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Roadland"
        val DRIVING = Any()
        val WAITING = Any()
        val RUNTIME_PERMISSIONS = listOf("android.car.permission.CAR_SPEED", "pleos.car.permission.FUSED_LOCATION")
    }
}
