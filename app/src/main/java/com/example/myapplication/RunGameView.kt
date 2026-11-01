package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.random.Random

/**
 * 붕붕이 달리기. 핸들 각도(±30°)가 좌우 위치, 브레이크가 점프.
 * 위에서 내려오는 콘은 피하고 코인은 먹는다. 40초 뒤 [onEnd]로 점수를 넘긴다.
 */
class RunGameView(context: Context, private val onEnd: (Int) -> Unit) : View(context) {
    private class Thing(var x: Float, var y: Float, val coin: Boolean, var gone: Boolean = false)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 48f }
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val things = mutableListOf<Thing>()
    private var targetX = 0.5f
    private var playerX = 0.5f
    private var jumpUntil = 0L
    private var score = 0
    private var hits = 0
    private var startedAt = 0L
    private var lastFrame = 0L
    private var spawnIn = 0f
    private var running = true

    /** 조향각(도). 오른쪽이 +. ±30°를 길 양 끝에 맞춘다. */
    fun steer(deg: Float) {
        targetX = (0.5f + deg.coerceIn(-30f, 30f) / 60f).coerceIn(0.05f, 0.95f)
    }

    fun jump() {
        val now = SystemClock.uptimeMillis()
        if (now > jumpUntil) jumpUntil = now + 800
    }

    fun stop() { running = false }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        if (startedAt == 0L) { startedAt = now; lastFrame = now }
        val dt = ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now
        val w = width.toFloat()
        val h = height.toFloat()
        val left = SECONDS - (now - startedAt) / 1000

        canvas.drawColor(Color.rgb(34, 46, 38))
        paint.color = Color.rgb(70, 74, 82)
        canvas.drawRect(w * 0.15f, 0f, w * 0.85f, h, paint)
        paint.color = Color.argb(160, 255, 255, 255)
        val dash = (now / 4 % 120).toFloat()
        var y = -120f + dash
        while (y < h) { canvas.drawRect(w * 0.5f - 6, y, w * 0.5f + 6, y + 60, paint); y += 120 }

        if (running && left > 0) {
            playerX += (targetX - playerX) * (dt * 8).coerceAtMost(1f)
            spawnIn -= dt
            if (spawnIn <= 0) {
                things.add(Thing(Random.nextFloat() * 0.6f + 0.2f, -0.1f, coin = Random.nextFloat() < 0.45f))
                spawnIn = 0.55f
            }
            val speed = 0.45f + (now - startedAt) / 1000f * 0.01f
            val jumping = now < jumpUntil
            for (t in things) {
                t.y += speed * dt
                if (!t.gone && t.y in 0.78f..0.9f && kotlin.math.abs(t.x - playerX) < 0.07f) {
                    if (t.coin) { score += 10; t.gone = true } else if (!jumping) { hits++; score = (score - 5).coerceAtLeast(0); t.gone = true }
                }
            }
            things.removeAll { it.y > 1.1f || it.gone }
        } else if (running) {
            running = false
            onEnd(score)
        }

        emoji.textSize = h * 0.07f
        for (t in things) canvas.drawText(if (t.coin) "🪙" else "🚧", t.x * w, t.y * h, emoji)
        val lift = if (now < jumpUntil) h * 0.06f else 0f
        emoji.textSize = h * 0.11f
        canvas.drawText("🚗", playerX * w, h * 0.88f - lift, emoji)

        canvas.drawText("점수 $score", 40f, 80f, label)
        canvas.drawText("남은 시간 ${left.coerceAtLeast(0)}초", 40f, 140f, label)
        label.textSize = 34f
        canvas.drawText("핸들을 살짝 기울여 피하고, 브레이크로 점프!", 40f, h - 40f, label)
        label.textSize = 48f
        if (running) postInvalidateOnAnimation()
    }

    private companion object { const val SECONDS = 40L }
}
