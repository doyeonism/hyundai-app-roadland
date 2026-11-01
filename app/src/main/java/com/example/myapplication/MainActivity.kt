package com.example.myapplication

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
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
import java.util.Locale

/** 화면 하나. 기어가 P일 때만 보이고, 주행이 시작되면 [onLeave]가 불린 뒤 사라진다. */
abstract class Screen(val host: MainActivity) {
    protected val ui get() = host.ui
    abstract fun build(): View
    /** 다른 화면에서 돌아오거나 주차해서 다시 보일 때. */
    open fun onReturn() {}
    open fun onLeave() {}
}

/**
 * 차량 앱의 유일한 Activity. 화면 전환과 기어 게이트를 맡는다.
 * 기어가 P가 아니거나 아직 모르면 어떤 게임 화면도 보여 주지 않는다.
 */
class MainActivity : Activity() {
    lateinit var ui: Ui
        private set
    val main = Handler(Looper.getMainLooper())
    private val vehicleLazy = lazy { Vehicle(applicationContext).also { it.initialize() } }
    val vehicle: Vehicle by vehicleLazy
    private lateinit var root: FrameLayout
    private val stack = ArrayDeque<Screen>()
    private var parked: Boolean? = null
    private var shown: Any? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    /** 이번 정차에서 미니게임을 처음 시작한 시각. 5분이 지나면 휴식을 권한다. */
    var gameSessionStartedAt = 0L

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) { main.post { onGear(gear) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "gear listener failed", e) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        window.decorView.setBackgroundColor(Palette.BG)
        root = FrameLayout(this).apply { setBackgroundColor(Palette.BG) }
        setContentView(root)
        // 처음이면 온보딩에서 권한 설명을 읽은 뒤 묻는다. 그 전에도 받을 수 있는 신호(기어 등)는 모은다.
        if (Onboarding.done(this)) ensurePermissions() else DriveService.start(this)
        stack.addLast(if (Onboarding.done(this)) TodayScreen(this) else OnboardingScreen(this))
        render()
        runCatching { vehicle.getGear().registerGearSelection(gearListener) }.onFailure { Log.w(TAG, "gear unavailable", it) }
    }

    /**
     * 리스너 이벤트를 놓쳐도 게이트가 멈추지 않게 보이는 동안 기어를 주기적으로 다시 읽는다.
     * (에뮬레이터에서 같은 Vehicle의 다른 리스너를 해제한 뒤 기어 이벤트가 끊긴 적이 있다.)
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
    }

    override fun onPause() {
        main.removeCallbacks(gearPoll)
        super.onPause()
    }

    /** 미니게임 입력용 Vehicle. 기어 게이트와 리스너를 섞지 않도록 게임마다 따로 만들고 끝나면 release 한다. */
    fun newInputVehicle(): Vehicle = Vehicle(applicationContext).also { it.initialize() }

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
        val nowParked = gear == VehicleGear.GEAR_PARK
        if (nowParked == parked) return
        parked = nowParked
        if (!nowParked) {
            // 주행 시작: 열려 있던 화면을 모두 닫고 첫 화면으로 돌아간다.
            while (stack.size > 1) stack.removeLast().onLeave()
            gameSessionStartedAt = 0L
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
        val target: Any = if (parked == true) stack.last() else DRIVING
        if (target === shown) return
        shown = target
        root.removeAllViews()
        root.addView(if (target is Screen) target.build() else drivingCover(), FrameLayout.LayoutParams(-1, -1))
    }

    /** 이미 보이는 화면을 새 데이터로 다시 그린다. */
    fun refresh(screen: Screen) {
        if (shown !== screen) return
        shown = null
        render()
    }

    fun isShowing(screen: Screen) = shown === screen

    private fun drivingCover(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Palette.BG)
        val waiting = parked == null
        addView(ui.text(if (waiting) "차량 상태를 확인하고 있어요" else "주행 중에는 화면을 숨겨요", 30f, Palette.INK, bold = true).apply { gravity = Gravity.CENTER })
        addView(ui.text(if (waiting) "기어가 P일 때 열려요" else "붕붕이는 조용히 기록하고 있어요. 주차(P)하면 다시 열려요.", 22f, Palette.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, ui.dp(16), 0, 0)
        })
    }

    /** 정차 중에만 붕붕이 목소리로 읽는다. */
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
        if (vehicleLazy.isInitialized()) {
            runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
            runCatching { vehicle.release() }
        }
        runCatching { tts?.release() }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Bungbung"
        val DRIVING = Any()
        val RUNTIME_PERMISSIONS = listOf("android.car.permission.CAR_SPEED", "pleos.car.permission.FUSED_LOCATION")
    }
}
