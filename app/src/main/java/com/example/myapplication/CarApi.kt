package com.example.myapplication

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
)

data class LandTile(
    val id: String, val level: Int, val owned: Boolean, val visited: Boolean, val homeRoute: Boolean,
    val incomePerHour: Int, val pending: Int, val buyCost: Int?, val upgradeCost: Int?, val special: String?,
)

data class Special(val name: String, val collected: Boolean)

data class Records(
    val safeStreak: Int, val bestSafeStreak: Int, val recentScores: List<Int>, val ownedTiles: Int, val specials: List<Special>,
)

/** 영토 서버. 지금은 web/의 더미 백엔드(`/api/car/...`)를 부른다. */
object CarApi {
    /** 에뮬레이터에서 호스트 PC의 dev 서버. 실차·실서버로 갈 때 바꾼다. */
    const val BASE = "http://10.0.2.2:3100/api/car"
    const val VEHICLE_ID = "veh_demo"

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun home(callback: (Result<Home>) -> Unit) = async(callback) { homeBlocking() }

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
        val specials = o.getJSONArray("specials")
        Records(
            o.getInt("safeStreak"), o.getInt("bestSafeStreak"), (0 until scores.length()).map { scores.getInt(it) }, o.getInt("ownedTiles"),
            (0 until specials.length()).map { specials.getJSONObject(it).run { Special(getString("name"), getBoolean("collected")) } },
        )
    }

    /** 수집 서비스의 작업 스레드에서 부른다. 큐에 저장해 둔 JSON을 그대로 보낸다. */
    fun postTripBlocking(body: JSONObject): Trip = parseTrip(request("POST", "/trips", body))

    fun tripBody(s: TripSummary): JSONObject = JSONObject()
        .put("startedAt", iso(s.startedAt)).put("endedAt", iso(s.endedAt))
        .put("km", s.km).put("safetyScore", s.safetyScore).put("hardBrakes", s.hardBrakes).put("warnings", s.warnings)
        .put("laneChanges", s.laneChanges).put("signaledLaneChanges", s.signaledLaneChanges)
        .put("tiles", JSONArray().apply { s.tiles.forEach { put(segmentJson(it)) } })

    /** 서버가 400으로 돌려준 안내 문구. */
    class GameException(message: String) : Exception(message)

    private fun <T> async(callback: (Result<T>) -> Unit, work: () -> T) {
        io.execute {
            val result = runCatching(work)
            main.post { callback(result) }
        }
    }

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.setRequestProperty("x-vehicle-id", VEHICLE_ID)
            conn.setRequestProperty("Accept", "application/json")
            if (body != null || method == "POST") {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 400) throw GameException(runCatching { JSONObject(text).getString("error") }.getOrDefault("요청을 처리하지 못했어요"))
            if (code !in 200..299) throw java.io.IOException("HTTP $code $text")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

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
        visited = o.getJSONArray("visitDays").length() > 0, homeRoute = o.getBoolean("homeRoute"),
        incomePerHour = o.getInt("incomePerHour"), pending = o.getInt("pending"),
        buyCost = if (o.isNull("buyCost")) null else o.getInt("buyCost"),
        upgradeCost = if (o.isNull("upgradeCost")) null else o.getInt("upgradeCost"),
        special = if (o.isNull("special")) null else o.getString("special"),
    )
}
