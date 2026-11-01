package com.example.myapplication

import android.content.Context
import android.os.Handler
import android.os.Looper

/** 수집 서비스가 정산을 마치면 열린 화면에 알린다. 마지막 정산 ID는 '아직 안 본 정산' 표시에 쓴다. */
object TripEvents {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(Trip) -> Unit>()

    fun listen(l: (Trip) -> Unit) = listeners.add(l)
    fun unlisten(l: (Trip) -> Unit) = listeners.remove(l)

    fun publish(context: Context, trip: Trip) {
        prefs(context).edit().putString(UNSEEN, trip.id).apply()
        main.post { listeners.toList().forEach { it(trip) } }
    }

    fun unseenTripId(context: Context): String? = prefs(context).getString(UNSEEN, null)
    fun markSeen(context: Context) = prefs(context).edit().remove(UNSEEN).apply()

    fun prefs(context: Context) = context.getSharedPreferences("bungbung", Context.MODE_PRIVATE)
    private const val UNSEEN = "unseen_trip"
}
