package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** 기본형 임시 그래픽. 웹의 BungbungAvatar와 같은 모양이고, 최종 아트가 오면 이 클래스만 바꾼다. */
class BungbungView(context: Context) : View(context) {
    var bung: Bung? = null
        set(value) { field = value; contentDescription = value?.let { "${it.name} 레벨 ${it.level}" }; invalidate() }

    /** 목욕 미니게임의 남은 거품 수. */
    var bubbles = 0
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val mark = mapOf("smooth" to "☁️", "explore" to "⛺", "eco" to "🌱", "safe" to "🛡️", "steady" to "⏰", "together" to "🪟")

    override fun onDraw(canvas: Canvas) {
        val b = bung ?: return
        // 200x160 좌표계에 그린 뒤 뷰 크기에 맞춘다.
        val s = minOf(width / 200f, height / 160f)
        canvas.save()
        canvas.translate((width - 200 * s) / 2, (height - 160 * s) / 2)
        canvas.scale(s, s)
        val grow = 0.8f + b.growth * 0.3f
        canvas.save()
        canvas.scale(grow, grow, 100f, 92f)
        paint.color = b.color
        canvas.drawRoundRect(RectF(35f, 60f, 165f, 118f), 26f, 26f, paint)
        canvas.drawRoundRect(RectF(62f, 34f, 138f, 74f), 18f, 18f, paint)
        paint.color = Color.argb(220, 255, 255, 255)
        canvas.drawRoundRect(RectF(72f, 42f, 128f, 66f), 10f, 10f, paint)
        paint.color = Color.rgb(31, 35, 40)
        canvas.drawCircle(88f, 54f, 4f, paint)
        canvas.drawCircle(112f, 54f, 4f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.strokeCap = Paint.Cap.ROUND
        val smile = if (b.mood >= 40) 6f else -4f
        canvas.drawPath(android.graphics.Path().apply { moveTo(92f, 62f); quadTo(100f, 62f + smile, 108f, 62f) }, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(43, 47, 54)
        canvas.drawCircle(68f, 120f, 15f, paint)
        canvas.drawCircle(132f, 120f, 15f, paint)
        canvas.restore()
        emoji.textSize = 26f
        b.mainAxis?.let { mark[it] }?.let { canvas.drawText(it, 160f, 40f, emoji) }
        emoji.textSize = 30f
        b.hat?.let { canvas.drawText(it, 100f, 36f - b.growth * 12, emoji) }
        emoji.textSize = 22f
        b.deco?.let { canvas.drawText(it, 42f, 70f, emoji) }
        emoji.textSize = 24f
        for (i in 0 until bubbles) canvas.drawText("🫧", 45f + (i % 4) * 36f, 80f + (i / 4) * 30f, emoji)
        canvas.restore()
    }
}
