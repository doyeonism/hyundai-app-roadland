package com.example.myapplication

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** 주행 위젯에 보여 줄 값. 주행 시작 때 서버에서 받은 오늘 값에 이번 주행의 예상치를 더한다. */
data class Widget(val todayTiles: Int, val credits: Int, val safetyScore: Int)

/**
 * 수집 서비스와 화면 사이의 소식. 같은 프로세스 안에서만 쓴다.
 * - 정산: 서버가 주행을 받아 처리하면 알린다. 마지막 정산 ID는 '아직 안 본 도착 요약' 표시에 쓴다.
 * - 위젯: 주행 중 숫자가 바뀌면 알린다.
 */
object TripEvents {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(Trip) -> Unit>()
    private val widgetListeners = mutableSetOf<(Widget?) -> Unit>()

    @Volatile var widget: Widget? = null
        private set

    fun listen(l: (Trip) -> Unit) = listeners.add(l)
    fun unlisten(l: (Trip) -> Unit) = listeners.remove(l)
    fun listenWidget(l: (Widget?) -> Unit) = widgetListeners.add(l)
    fun unlistenWidget(l: (Widget?) -> Unit) = widgetListeners.remove(l)

    fun publish(context: Context, trip: Trip) {
        prefs(context).edit().putString(UNSEEN, trip.id).apply()
        main.post { listeners.toList().forEach { it(trip) } }
    }

    fun publishWidget(w: Widget?) {
        widget = w
        main.post { widgetListeners.toList().forEach { it(w) } }
    }

    fun unseenTripId(context: Context): String? = prefs(context).getString(UNSEEN, null)
    fun markSeen(context: Context) = prefs(context).edit().remove(UNSEEN).apply()

    fun prefs(context: Context) = context.getSharedPreferences("roadland", Context.MODE_PRIVATE)
    private const val UNSEEN = "unseen_trip"
}

/**
 * 아직 서버에 못 보낸 주행. 각 주행에는 clientTripId 가 있어, 서버가 처리했는데 응답만 끊긴 경우 다시 보내도 한 번만 정산된다.
 * 통신이 끊겨도 기록을 잃지 않게 파일(SharedPreferences)에 쌓고, 정차하거나 앱을 열 때 순서대로 보낸다.
 */
object TripQueue {
    private const val KEY = "pending_trips"
    private val lock = Any()

    fun add(context: Context, body: JSONObject) = synchronized(lock) {
        val list = load(context).put(body)
        TripEvents.prefs(context).edit().putString(KEY, list.toString()).commit()
    }

    fun size(context: Context) = synchronized(lock) { load(context).length() }

    /** 작업 스레드에서 부른다. 앞에서부터 보내고, 실패하면 그 자리에서 멈춘다. 보낸 개수를 돌려준다. */
    fun flushBlocking(context: Context): Int = synchronized(lock) {
        var sent = 0
        while (true) {
            val list = load(context)
            if (list.length() == 0) break
            val result = runCatching { CarApi.postTripBlocking(list.getJSONObject(0)) }
            // 서버가 받아들이지 않은 기록(형식 오류 등)은 다시 보내도 같으므로 버린다. 뒤의 주행이 막히지 않게.
            val rejected = result.exceptionOrNull() is CarApi.GameException
            if (result.isFailure) Log.w("RoadDrive", "trip upload failed${if (rejected) ", dropped" else ""}, ${list.length()} waiting", result.exceptionOrNull())
            if (rejected) {
                list.remove(0)
                TripEvents.prefs(context).edit().putString(KEY, list.toString()).commit()
                continue
            }
            val trip = result.getOrNull() ?: break
            list.remove(0)
            TripEvents.prefs(context).edit().putString(KEY, list.toString()).commit()
            Log.i("RoadDrive", "trip settled: ${trip.id} credits=${trip.credits} newTiles=${trip.newTiles}")
            TripEvents.publish(context, trip)
            sent++
        }
        sent
    }

    private fun load(context: Context) = runCatching { JSONArray(TripEvents.prefs(context).getString(KEY, "[]")) }.getOrDefault(JSONArray())
}
