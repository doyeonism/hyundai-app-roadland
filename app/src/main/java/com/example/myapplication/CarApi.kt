package com.example.myapplication

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class Trip(
    val id: String, val km: Double, val safetyScore: Int, val hardBrakes: Int, val warnings: Int,
    val laneChanges: Int, val signaledLaneChanges: Int, val tiles: List<TileSegment>, val newTiles: Int,
    val credits: Int, val clean: Int, val assistBonus: Int, val mannerBonus: Int, val excluded: Int,
    val capReached: Boolean, val rejected: Boolean,
)

data class Today(val trips: Int, val km: Double, val tilesPassed: Int, val tileIds: Set<String>, val driveCredits: Int, val driveCap: Int)

data class Home(
    val credits: Int, val pendingIncome: Int, val incomePerHour: Int, val idleCapHours: Int,
    val ownedTiles: Int, val buyableTiles: Int, val today: Today, val latestTrip: Trip?, val safeStreak: Int,
    val incomePerDay: Int = incomePerHour * 24,
    val personalLevel: Int = 1, val personalUpgradeCost: Int? = null,
    val incomePerTilePerDay: Int = 0, val nextIncomePerTilePerDay: Int? = null,
)

/**
 * 땅 한 칸. 한 칸에는 주인이 하나뿐이고, 다른 차의 땅([ownedByOther])도 [buyCost]를 내면 빼앗을 수 있다.
 * 주인이 있는 땅은 마지막 거래 뒤에 다시 지나가야 살 수 있다([mustRevisit]).
 * [price]는 마지막 거래가, [goodTrips]/[badTrips]는 내가 이 칸을 지나간 주행 중 80점을 넘은 수와 아닌 수(수익에 쓰인다).
 */
data class LandTile(
    val id: String, val level: Int, val owned: Boolean, val ownedByOther: Boolean, val price: Int?, val mustRevisit: Boolean, val visited: Boolean,
    val homeRoute: Boolean, val goodTrips: Int, val badTrips: Int,
    val incomePerHour: Int, val pending: Int, val buyCost: Int?, val upgradeCost: Int?,
    val incomePerDay: Int = incomePerHour * 24, val nextLevelIncomePerDay: Int? = null,
)

/** 내 순위. 순위표에 없으면 null. */
data class Ranking(val landRank: Int?, val landScore: Int, val weeklyRank: Int?, val weeklyCredits: Int)

data class Records(
    val safeStreak: Int, val bestSafeStreak: Int, val recentScores: List<Int>, val ownedTiles: Int,
)

/**
 * 영토 서버(hyundai-server 의 NestJS `v1/car/...`).
 * 처음 부를 때 기기를 등록해 토큰을 받아 두고, 이후 요청마다 `Authorization: Bearer` 로 보낸다.
 * 서버 응답은 `{ result, message, data }` 봉투이고, 실패하면 message 에 안내 문구가 온다.
 */
object CarApi {
    /** 빌드 때 정한다(gradle -ProadlandServer=...). 기본은 에뮬레이터에서 본 호스트 PC의 dev 서버. */
    val BASE: String = BuildConfig.ROADLAND_SERVER

    private const val TOKEN = "device_token"
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var context: Context? = null
    private val tokenLock = Any()

    /** 앱·서비스가 켜질 때 부른다. 토큰을 저장할 곳. */
    fun init(context: Context) {
        this.context = context.applicationContext
    }

    fun consent(callback: (Result<Unit>) -> Unit) = async(callback) {
        request("POST", "/privacy/consent", JSONObject().put("version", "2026-11-14"))
        Unit
    }

    fun deleteProfile(callback: (Result<Unit>) -> Unit) = async(callback) { TripQueue.exclusive {
        val c = context ?: error("CarApi.init 먼저")
        // Do not auto-register a fresh account while deleting an old identity.
        val saved = prefs().getString(TOKEN, null)
        if (saved != null) {
            val (code, json) = send("DELETE", "/profile", null, saved)
            if (code != 401) unwrap(code, json)
        }
        TripQueue.clear(c)
        prefs().edit().clear().commit()
        TripEvents.publishWidget(null)
        Unit
    } }

    fun home(callback: (Result<Home>) -> Unit) = async(callback) { homeBlocking() }

    fun upgradePersonal(level: Int, callback: (Result<Home>) -> Unit) = async(callback) {
        parseHome(request("POST", "/profile/upgrade", JSONObject().put("expectedLevel", level)))
    }

    fun homeBlocking() = parseHome(request("GET", "/home", null))

    fun territory(callback: (Result<List<LandTile>>) -> Unit) = async(callback) {
        val a = request("GET", "/territory", null).getJSONArray("tiles")
        (0 until a.length()).map { parseTile(a.getJSONObject(it)) }
    }

    fun buy(id: String, callback: (Result<LandTile>) -> Unit) = async(callback) { parseTile(request("POST", "/tiles/$id/buy", null)) }

    fun upgrade(id: String, callback: (Result<LandTile>) -> Unit) = async(callback) { parseTile(request("POST", "/tiles/$id/upgrade", null)) }

    /** 받은 방치 수익. */
    fun collect(callback: (Result<Int>) -> Unit) = async(callback) { request("POST", "/collect", null).getInt("collected") }

    fun records(callback: (Result<Records>) -> Unit) = async(callback) {
        val o = request("GET", "/records", null)
        val scores = o.getJSONArray("recentScores")
        Records(
            o.getInt("safeStreak"), o.getInt("bestSafeStreak"), (0 until scores.length()).map { scores.getInt(it) }, o.getInt("ownedTiles"),
        )
    }

    fun ranking(callback: (Result<Ranking>) -> Unit) = async(callback) {
        val o = request("GET", "/ranking", null)
        val land = o.getJSONObject("land").optJSONObject("mine")
        val weekly = o.getJSONObject("weekly").optJSONObject("mine")
        Ranking(land?.getInt("rank"), land?.getInt("score") ?: 0, weekly?.getInt("rank"), weekly?.getInt("score") ?: 0)
    }

    /** 수집 서비스의 작업 스레드에서 부른다. 큐에 저장해 둔 JSON을 그대로 보낸다. */
    fun postTripBlocking(body: JSONObject): Trip = parseTrip(request("POST", "/trips", body))

    /** 큐에 저장할 주행 본문. clientTripId 가 있어 응답을 못 받고 다시 보내도 서버가 한 번만 정산한다. */
    fun tripBody(s: TripSummary): JSONObject = JSONObject()
        .put("clientTripId", java.util.UUID.randomUUID().toString())
        .put("startedAt", iso(s.startedAt)).put("endedAt", iso(s.endedAt))
        .put("km", s.km).put("safetyScore", s.safetyScore).put("hardBrakes", s.hardBrakes).put("warnings", s.warnings)
        .put("laneChanges", s.laneChanges).put("signaledLaneChanges", s.signaledLaneChanges)
        .put("tiles", JSONArray().apply { s.tiles.forEach { put(segmentJson(it)) } })

    /** 서버가 요청을 받아들이지 않았다(4xx). 다시 보내도 결과가 같다. message 는 사용자에게 보여 줄 문구. */
    class GameException(message: String) : Exception(message)

    private fun <T> async(callback: (Result<T>) -> Unit, work: () -> T) {
        io.execute {
            val result = runCatching(work)
            main.post { callback(result) }
        }
    }

    /** 응답 봉투의 data. 토큰이 서버에 없으면(서버 DB 초기화 등) 다시 등록하고 한 번 더 보낸다. */
    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val (code, json) = send(method, path, body, token())
        if (code != 401) return unwrap(code, json)
        clearToken()
        val (retryCode, retryJson) = send(method, path, body, token())
        return unwrap(retryCode, retryJson)
    }

    private fun unwrap(code: Int, json: JSONObject): JSONObject {
        val message = json.optString("message").ifBlank { "요청을 처리하지 못했어요" }
        if (code in 400..499 && code != 408 && code != 409 && code != 429) throw GameException(message)
        if (code !in 200..299) throw java.io.IOException("HTTP $code $message")
        return json.optJSONObject("data") ?: JSONObject()
    }

    private fun send(method: String, path: String, body: JSONObject?, token: String?): Pair<Int, JSONObject> {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null || method == "POST") {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to (runCatching { JSONObject(text) }.getOrNull() ?: JSONObject())
        } finally {
            conn.disconnect()
        }
    }

    /** 저장해 둔 기기 토큰. 없으면 서버에 등록해서 받는다(작업 스레드에서만 부른다). */
    private fun token(): String = synchronized(tokenLock) {
        val prefs = prefs()
        prefs.getString(TOKEN, null)?.let { return it }
        val (code, json) = send("POST", "/devices", null, null)
        val token = unwrap(code, json).getString("token")
        prefs.edit().putString(TOKEN, token).commit()
        token
    }

    private fun clearToken() = synchronized(tokenLock) { prefs().edit().remove(TOKEN).commit() }

    private fun prefs() = (context ?: error("CarApi.init 먼저")).getSharedPreferences("roadland", Context.MODE_PRIVATE)

    private fun iso(millis: Long) = java.time.Instant.ofEpochMilli(millis).toString()

    private fun segmentJson(t: TileSegment) =
        JSONObject().put("id", t.id).put("hardBrake", t.hardBrake).put("warning", t.warning).put("assisted", t.assisted)

    private fun parseHome(o: JSONObject): Home {
        val t = o.getJSONObject("today")
        return Home(
            credits = o.getInt("credits"), pendingIncome = o.getInt("pendingIncome"), incomePerHour = o.getInt("incomePerHour"),
            idleCapHours = o.getInt("idleCapHours"), ownedTiles = o.getInt("ownedTiles"), buyableTiles = o.getInt("buyableTiles"),
            today = Today(
                t.getInt("trips"), t.getDouble("km"), t.getInt("tilesPassed"),
                t.getJSONArray("tileIds").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
                t.getInt("driveCredits"), t.getInt("driveCap"),
            ),
            latestTrip = if (o.isNull("latestTrip")) null else parseTrip(o.getJSONObject("latestTrip")),
            safeStreak = o.getInt("safeStreak"),
            incomePerDay = o.optInt("incomePerDay", o.getInt("incomePerHour") * 24),
            personalLevel = o.optInt("personalLevel", 1),
            personalUpgradeCost = if (o.isNull("personalUpgradeCost")) null else o.getInt("personalUpgradeCost"),
            incomePerTilePerDay = o.optInt("incomePerTilePerDay", 0),
            nextIncomePerTilePerDay = if (o.isNull("nextIncomePerTilePerDay")) null else o.getInt("nextIncomePerTilePerDay"),
        )
    }

    private fun parseTrip(o: JSONObject): Trip {
        val b = o.getJSONObject("breakdown")
        val tiles = o.getJSONArray("tiles")
        return Trip(
            id = o.getString("id"), km = o.getDouble("km"), safetyScore = o.getInt("safetyScore"), hardBrakes = o.getInt("hardBrakes"),
            warnings = o.getInt("warnings"), laneChanges = o.getInt("laneChanges"), signaledLaneChanges = o.getInt("signaledLaneChanges"),
            tiles = (0 until tiles.length()).map { i ->
                tiles.getJSONObject(i).run { TileSegment(getString("id"), getBoolean("hardBrake"), getBoolean("warning"), getBoolean("assisted")) }
            },
            newTiles = o.getInt("newTiles"), credits = o.getInt("credits"),
            clean = b.getInt("clean"), assistBonus = b.getInt("assistBonus"), mannerBonus = b.getInt("mannerBonus"), excluded = b.getInt("excluded"),
            capReached = o.getBoolean("capReached"), rejected = o.getBoolean("rejected"),
        )
    }

    private fun parseTile(o: JSONObject) = LandTile(
        id = o.getString("id"), level = o.getInt("level"), owned = o.getBoolean("owned"),
        ownedByOther = o.getBoolean("ownedByOther"), price = if (o.isNull("price")) null else o.getInt("price"),
        mustRevisit = o.getBoolean("mustRevisit"),
        visited = o.getJSONArray("visitDays").length() > 0, homeRoute = o.getBoolean("homeRoute"),
        goodTrips = o.getInt("goodTrips"), badTrips = o.getInt("badTrips"),
        incomePerHour = o.getInt("incomePerHour"), pending = o.getInt("pending"),
        buyCost = if (o.isNull("buyCost")) null else o.getInt("buyCost"),
        upgradeCost = if (o.isNull("upgradeCost")) null else o.getInt("upgradeCost"),
        incomePerDay = o.optInt("incomePerDay", o.getInt("incomePerHour") * 24),
        nextLevelIncomePerDay = if (o.isNull("nextLevelIncomePerDay")) null else o.getInt("nextLevelIncomePerDay"),
    )
}
