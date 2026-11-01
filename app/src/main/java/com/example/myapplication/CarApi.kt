package com.example.myapplication

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class Mission(val id: String, val text: String, val progress: Int, val goal: Int, val reward: String) {
    val done get() = progress >= goal
}

data class Trip(
    val id: String, val km: Double, val hardBrakes: Int, val foodEarned: Int, val newTiles: Int,
    val passengers: Int, val gaugeGained: Int, val foodCapReached: Boolean, val line: String,
)

data class Bung(
    val name: String, val level: Int, val xp: Int, val xpToNext: Int, val stage: Int, val mainAxis: String,
    val color: Int, val hat: String?, val deco: String?, val mood: Int, val fullness: Int,
)

data class Wallet(val food: Int, val coins: Int, val gems: Int, val buildGauge: Int)

data class Home(
    val bung: Bung, val wallet: Wallet, val missions: List<Mission>, val latestTrip: Trip?,
    val todayKm: Double, val todayTiles: Int, val todayFood: Int,
)

/** 정산·키우기 서버. 지금은 web/의 더미 백엔드(`/api/car/...`)를 부른다. */
object CarApi {
    /** 에뮬레이터에서 호스트 PC의 dev 서버. 실차·실서버로 갈 때 바꾼다. */
    const val BASE = "http://10.0.2.2:3100/api/car"
    const val VEHICLE_ID = "veh_demo"

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun home(callback: (Result<Home>) -> Unit) = async(callback) { parseHome(request("GET", "/home", null)) }

    fun feed(amount: Int, callback: (Result<Pair<Boolean, Home>>) -> Unit) = async(callback) {
        val res = request("POST", "/feed", JSONObject().put("amount", amount))
        res.getBoolean("evolved") to parseHome(res.getJSONObject("home"))
    }

    /** 돌려주는 값: (기분 증가, 코인 증가). */
    fun minigame(game: String, score: Int, callback: (Result<Pair<Int, Int>>) -> Unit) = async(callback) {
        val res = request("POST", "/minigames", JSONObject().put("game", game).put("score", score))
        res.getInt("mood") to res.getInt("coins")
    }

    /** 주행 수집 서비스의 작업 스레드에서 부른다. */
    fun postTripBlocking(summary: TripSummary): Trip {
        val points = JSONArray().apply { summary.points.forEach { put(JSONArray().put(it.first).put(it.second)) } }
        val body = JSONObject()
            .put("startedAt", iso(summary.startedAt)).put("endedAt", iso(summary.endedAt))
            .put("km", summary.km).put("hardBrakes", summary.hardBrakes)
            .put("passengers", summary.passengers).put("points", points)
        return parseTrip(request("POST", "/trips", body))
    }

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
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw java.io.IOException("HTTP $code $text")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun iso(millis: Long) = java.time.Instant.ofEpochMilli(millis).toString()

    private fun parseHome(o: JSONObject): Home {
        val b = o.getJSONObject("bungbung")
        val eq = b.getJSONObject("equipped")
        val w = o.getJSONObject("wallet")
        val m = o.getJSONArray("missions")
        val t = o.getJSONObject("today")
        return Home(
            bung = Bung(
                name = b.getString("name"), level = b.getInt("level"), xp = b.getInt("xp"), xpToNext = b.getInt("xpToNext"),
                stage = b.getInt("stage"), mainAxis = b.getString("mainAxis"),
                color = runCatching { Color.parseColor(eq.getString("color")) }.getOrDefault(Palette.ACCENT),
                hat = eq.optStringOrNull("hat"), deco = eq.optStringOrNull("deco"),
                mood = b.getInt("mood"), fullness = b.getInt("fullness"),
            ),
            wallet = Wallet(w.getInt("food"), w.getInt("coins"), w.getInt("gems"), w.getInt("buildGauge")),
            missions = (0 until m.length()).map { i ->
                m.getJSONObject(i).run { Mission(getString("id"), getString("text"), getInt("progress"), getInt("goal"), getString("reward")) }
            },
            latestTrip = if (o.isNull("latestTrip")) null else parseTrip(o.getJSONObject("latestTrip")),
            todayKm = t.getDouble("km"), todayTiles = t.getInt("newTiles"), todayFood = t.getInt("food"),
        )
    }

    private fun parseTrip(o: JSONObject) = Trip(
        id = o.getString("id"), km = o.getDouble("km"), hardBrakes = o.getInt("hardBrakes"),
        foodEarned = o.getInt("foodEarned"), newTiles = o.getInt("newTiles"), passengers = o.getInt("passengers"),
        gaugeGained = o.getInt("gaugeGained"), foodCapReached = o.getBoolean("foodCapReached"), line = o.getString("line"),
    )

    private fun JSONObject.optStringOrNull(key: String) = if (isNull(key)) null else optString(key).ifEmpty { null }
}
