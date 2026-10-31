package com.example.myapplication

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.graphics.Color
import android.widget.*
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.stt.SpeechToText
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads real service responses. No sample values or vehicle writes are used here. */
class DiagnosticsActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val results = linkedMapOf<String, String>()
    private val generations = mutableMapOf<String, Int>()
    private val pending = mutableSetOf<String>()
    private lateinit var output: TextView
    private var vehicle: Vehicle? = null
    private var fused: FusedLocation? = null
    private var navi: NaviHelper? = null
    private var naviReady = false
    private var naviCompletion: ((String) -> Unit)? = null
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null
    private var ttsReady = false
    private var sttReady = false
    private var ttsCompletion: ((String) -> Unit)? = null
    private var sttCompletion: ((String) -> Unit)? = null
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var monitoring = false
    private var closed = false
    private lateinit var monitorButton: Button
    private val monitor = object : Runnable {
        override fun run() {
            if (!monitoring || closed) return
            speed(); gear()
            main.postDelayed(this, 3000)
        }
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStateInfo(info: RouteStateInfo) {
            naviCompletion?.invoke("응답 수신: $info")
        }
        override fun onError(error: NaviErrorCode) {
            naviCompletion?.invoke("서비스 오류: $error")
        }
    }
    private val ttsListener = object : ai.pleos.playground.tts.listener.EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { ttsCompletion?.invoke("음성 재생 완료 응답 수신") }
        override fun onStop() { ttsCompletion?.invoke("음성 재생 중지") }
        override fun onError(errorType: ai.pleos.playground.tts.constant.ErrorType) {
            ttsCompletion?.invoke("음성 합성 오류: $errorType")
        }
    }
    private val sttListener = object : ai.pleos.playground.stt.listener.ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (isFinal) sttCompletion?.invoke("인식 결과: $text")
        }
        override fun onUpdatedEpdData(start: Long, end: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(errorType: ai.pleos.playground.stt.constant.ErrorType) {
            sttCompletion?.invoke("음성 인식 오류: $errorType")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
            setBackgroundColor(Color.rgb(13, 23, 36))
        }
        scroll.addView(layout)
        fun text(value: String, size: Float = 18f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(8)); layout.addView(this)
        }
        fun button(title: String, id: Int = 0, action: () -> Unit): Button = Button(this).apply {
            text = title; textSize = 18f; minHeight = dp(56)
            if (id != 0) this.id = id
            layout.addView(this, LinearLayout.LayoutParams(-1, -2))
            setOnClickListener { action() }
        }
        text("차량 · SDK 진단", 30f)
        text("실제 서비스 응답만 표시합니다. 연결 실패·미지원·값 없음은 성공과 구분됩니다.")
        output = text("", 18f).apply {
            id = R.id.diagnostics_results
            setTextIsSelectable(true)
        }
        button("권한 확인 및 요청") { permissions() }
        button("전체 상태 조회 (음성 녹음 없음)", R.id.query_all) { queryAll() }
        monitorButton = button("실시간 차량 조회 시작 (3초 간격)") {
            monitoring = !monitoring
            monitorButton.text = if (monitoring) "실시간 조회 중지" else "실시간 차량 조회 시작 (3초 간격)"
            main.removeCallbacks(monitor)
            if (monitoring) monitor.run()
        }
        button("표준 AAOS · 현재 속도 조회") { speed() }
        button("Vehicle · 현재 기어 조회") { gear() }
        button("Vehicle · 앞 왼쪽 도어 조회") { door() }
        button("Vehicle · 배터리 잔량 조회") { battery() }
        button("FusedLocation · 지원 및 위치 조회") { location() }
        button("NaviHelper · 경로 상태 조회") { navigation() }
        button("TextToSpeech · 연결 상태 조회") { initTts() }
        button("TextToSpeech · 테스트 문장 읽기") {
            if (!ttsReady) record("음성 재생", "먼저 TextToSpeech 연결 상태를 조회하세요.")
            else probe("음성 재생", 15000) { done ->
                ttsCompletion = done
                tts!!.setLocale(Locale.KOREA) // 엔진은 ko-KR 필요, Locale.KOREAN(ko)은 미지원 오류
                tts!!.speak("차량 앱의 음성 안내 테스트입니다.", true)
            }
        }
        button("SpeechToText · 연결 상태 조회") { initStt() }
        button("SpeechToText · 음성 인식 시작") {
            if (!sttReady) record("음성 인식", "먼저 SpeechToText 연결 상태를 조회하세요.")
            else probe("음성 인식", 15000) { done ->
                sttCompletion = done
                stt!!.setLocale(Locale.KOREA)
                stt!!.request()
                main.postDelayed({ runCatching { stt?.stop() } }, 15000)
            }
        }
        button("음성 인식 / 재생 중지") {
            runCatching { stt?.stop() }; runCatching { tts?.stop() }
            sttCompletion?.invoke("사용자가 음성 인식을 중지했습니다.")
        }
        text("주행 시나리오\nPC의 Virtual Console에서 차량 값을 변경한 뒤 전체 상태 조회 또는 실시간 조회를 사용하세요. 기어 D만으로 이동 중이라고 판단하지 않으며, 실제 속도 값을 확인합니다.\n\n음성 테스트는 ON_DEVICE 모드를 사용합니다. 녹음은 음성 인식 시작 버튼을 눌렀을 때만 요청합니다.")
        button("홈으로 돌아가기") { finish() }
        setContentView(scroll)
        layout.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(dp(28) + insets.systemWindowInsetLeft, dp(24) + insets.systemWindowInsetTop,
                dp(28) + insets.systemWindowInsetRight, dp(24) + insets.systemWindowInsetBottom)
            insets.consumeSystemWindowInsets()
        }
        layout.requestApplyInsets()
        record("환경", android.os.Build.FINGERPRINT)
        record("안내", "조회 버튼을 눌러주세요. 값이 null이면 데이터 없음으로 표시됩니다.")
        try {
            car = Car.createCar(this, main, 0) { connectedCar, ready ->
                if (closed) return@createCar
                properties = if (ready) connectedCar.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager else null
                record("AAOS 연결", if (ready) "차량 서비스 연결됨" else "차량 서비스 연결 끊김")
            }
        } catch (e: Exception) { record("AAOS 연결", error(e)) }
    }

    private fun vehicle(): Vehicle = vehicle ?: Vehicle(applicationContext).also {
        vehicle = it; it.initialize()
    }

    internal fun queryAll() {
        speed(); gear(); door(); battery(); location(); navigation(); initTts(); initStt()
    }

    private fun speed() = probe("현재 속도") { done ->
        val manager = properties ?: error("AAOS 차량 서비스 연결 대기 / 미지원")
        worker.execute {
            try {
                if (!manager.isPropertyAvailable(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)) {
                    done("미지원 또는 현재 속도 데이터 없음")
                } else {
                    val value = manager.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
                    done(String.format(Locale.KOREA, "%.1f km/h · %s", value * 3.6f,
                        if (kotlin.math.abs(value) > 0.1f) "이동 중" else "정지 상태"))
                }
            } catch (e: Exception) { done(error(e)) }
        }
    }
    private fun gear() = probe("기어") { done ->
        vehicle().getGear().getCurrentGearSelectionState({ done(it?.toString() ?: "데이터 없음") }, { done(error(it)) })
    }
    private fun door() = probe("도어") { done ->
        vehicle().getDoor().getCurrentDoorPosition(DoorArea(DoorValue.ROW_1_LEFT),
            { done(it?.let { value -> "앞 왼쪽 도어 원시값: $value" } ?: "데이터 없음") }, { done(error(it)) })
    }
    private fun battery() = probe("배터리") { done ->
        vehicle().getEvBattery().getEvBatteryStateOfCharge(
            { done(it?.let { value -> "$value %" } ?: "데이터 없음") }, { done(error(it)) })
    }
    private fun location() = probe("위치") { done ->
        val sdk = fused ?: FusedLocation(applicationContext).also { fused = it }
        if (!sdk.isSupported()) done("미지원 · 이 기기에 Pleos FusedLocation 서비스가 없습니다.")
        else {
            sdk.initialize()
            sdk.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    done(location?.let { "위도 ${it.latitude}, 경도 ${it.longitude}\n측정 시각: ${Date(it.timestampMillis)}" }
                        ?: "연결 응답 수신 · 아직 위치 데이터 없음")
                }
                override fun onError(exception: Exception) { done(error(exception)) }
            })
        }
    }
    private fun navigation() = probe("내비게이션") { done ->
        naviCompletion = done
        if (naviReady) navi!!.getRouteStateInfo()
        else {
            navi?.release()
            val sdk = NaviHelper(applicationContext)
            navi = sdk
            sdk.initialize({
                main.post {
                    if (!closed) {
                        naviReady = true
                        sdk.addListener(naviListener)
                        runCatching { sdk.getRouteStateInfo() }.onFailure { done(error(it)) }
                    }
                }
            }, { done(error(it)) })
        }
    }
    private fun initTts() = probe("TTS 연결") { done ->
        if (ttsReady) done("ON_DEVICE 서비스 초기화 완료")
        else {
            tts?.release()
            val sdk = TextToSpeech(applicationContext, ai.pleos.playground.tts.constant.Mode.ON_DEVICE)
            tts = sdk
            sdk.initialize(object : ai.pleos.playground.tts.listener.OnInitializeListener {
                override fun onSuccess() {
                    main.post { if (!closed) {
                        ttsReady = true; sdk.addEventListener(ttsListener)
                        done("ON_DEVICE 서비스 초기화 성공 · 테스트 문장 읽기 가능")
                    } }
                }
                override fun onFailure(errorType: ai.pleos.playground.tts.constant.ErrorType) { done("초기화 실패: $errorType") }
            })
        }
    }
    private fun initStt() = probe("STT 연결") { done ->
        if (sttReady) done("ON_DEVICE 서비스 초기화 완료 · 현재 녹음 안 함")
        else {
            stt?.release()
            val sdk = SpeechToText(applicationContext, ai.pleos.playground.stt.constant.Mode.ON_DEVICE)
            stt = sdk
            sdk.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
                override fun onSuccess() {
                    main.post { if (!closed) {
                        sttReady = true; sdk.addListener(sttListener)
                        done("ON_DEVICE 서비스 초기화 성공 · 현재 녹음 안 함")
                    } }
                }
                override fun onFailure(errorType: ai.pleos.playground.stt.constant.ErrorType) { done("초기화 실패: $errorType") }
            })
        }
    }

    private fun permissions() {
        val names = arrayOf("android.car.permission.CAR_SPEED", "android.car.permission.CAR_POWERTRAIN",
            "pleos.car.permission.CAR_DOORS", "pleos.car.permission.CAR_DRIVE", "pleos.car.permission.CAR_ENERGY",
            "pleos.car.permission.FUSED_LOCATION", "pleos.car.permission.NAVI_ROUTE",
            "pleos.car.permission.TTS_SERVICE", "pleos.car.permission.STT_SERVICE")
        val requestable = mutableListOf<String>()
        val description = names.joinToString("\n") { name ->
            val info = runCatching { packageManager.getPermissionInfo(name, 0) }.getOrNull()
            val granted = checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
            if (!granted && info != null && (info.protectionLevel and 15) == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS) requestable.add(name)
            "${name.substringAfterLast('.')}: ${if (granted) "허용" else if (info == null) "기기에 권한 정의 없음" else "미허용"}"
        }
        record("권한", description)
        if (requestable.isNotEmpty()) requestPermissions(requestable.toTypedArray(), 42)
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        record("권한 요청 결과", permissions.zip(grants.toList()).joinToString("\n") {
            "${it.first.substringAfterLast('.')}: ${if (it.second == PackageManager.PERMISSION_GRANTED) "허용" else "거부"}"
        })
    }

    private fun probe(key: String, timeout: Long = 8000, action: ((String) -> Unit) -> Unit) {
        if (closed || key in pending) return
        val token = (generations[key] ?: 0) + 1
        generations[key] = token; pending.add(key)
        record(key, "조회 중…")
        val finish: (String) -> Unit = { value -> main.post {
            if (!closed && generations[key] == token && pending.remove(key)) record(key, value)
        }; Unit }
        main.postDelayed({
            if (generations[key] == token && key in pending) {
                finish("응답 시간 초과 · 서비스 설치 / 버전 / 권한을 확인하세요.")
            }
        }, timeout)
        try { action(finish) } catch (e: Exception) { finish(error(e)) }
    }
    private fun record(key: String, value: String) {
        if (closed) return
        results[key] = "${SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())} · $value"
        output.text = results.entries.joinToString("\n\n") { "[${it.key}]\n${it.value}" }
        android.util.Log.i("SdkDiagnostics", "$key: $value")
    }
    private fun error(e: Throwable) = "실패 · ${e.javaClass.simpleName}: ${e.message ?: "원인 정보 없음"}"
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStop() {
        monitoring = false; main.removeCallbacks(monitor)
        monitorButton.text = "실시간 차량 조회 시작 (3초 간격)"
        runCatching { stt?.stop() }; runCatching { tts?.stop() }
        super.onStop()
    }
    override fun onDestroy() {
        closed = true; main.removeCallbacksAndMessages(null); worker.shutdownNow()
        runCatching { vehicle?.release() }; runCatching { fused?.release() }
        runCatching { navi?.removeListener(naviListener); navi?.release() }
        runCatching { tts?.removeEventListener(ttsListener); tts?.release() }
        runCatching { stt?.removeListener(sttListener); stt?.release() }
        runCatching { car?.disconnect() }
        super.onDestroy()
    }
}
