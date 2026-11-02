package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 육각 타일 지도. 영토 화면에서는 끌어서 옮기고, 두 손가락으로 확대하고, 눌러서 고른다. 도착 요약에서는 이번 경로를 색으로 보여 준다.
 * 타일 위치는 축 좌표만으로 그린다(위경도가 필요 없다).
 */
class HexMapView(context: Context) : View(context) {
    /** 영토: 지나간 땅과 내 땅. */
    var tiles: List<LandTile> = emptyList()
        set(value) { field = value; byId = value.associateBy { it.id }; invalidate() }
    /** 이번 주행 경로. 있으면 경로 보기로 그린다. */
    var route: List<TileSegment> = emptyList()
        set(value) { field = value; invalidate() }
    var selected: String? = null
        set(value) { field = value; invalidate() }
    var onSelect: ((String) -> Unit)? = null

    private var byId: Map<String, LandTile> = emptyMap()
    private var size = 28f
    private var centerX = 0f
    private var centerY = 0f
    private var fitted = false
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.WHITE }
    private val hex = Path()

    /** 이 타일들이 한눈에 들어오게 맞춘다. 타일이 몇 칸 안 되면 너무 커지지 않게 최소 [minSpan]칸 넓이로 본다. */
    fun fit(ids: Collection<String>, minSpan: Float = 0f) {
        if (ids.isEmpty() || width == 0) { fitted = false; return }
        val pts = ids.map { axialToWorld(HexGrid.parse(it)) }
        val minX = pts.minOf { it.first }; val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }; val maxY = pts.maxOf { it.second }
        centerX = ((minX + maxX) / 2).toFloat()
        centerY = ((minY + maxY) / 2).toFloat()
        val spanX = maxOf((maxX - minX + 4).toFloat(), minSpan)
        val spanY = maxOf((maxY - minY + 4).toFloat(), minSpan)
        size = minOf(width / spanX, height / spanY).coerceIn(MIN_SIZE, MAX_SIZE)
        fitted = true
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (!fitted) fit(route.map { it.id }.ifEmpty { tiles.map { it.id } })
    }

    /** 축 좌표 → 타일 크기 1 기준 평면 좌표(위가 북쪽). */
    private fun axialToWorld(a: Pair<Int, Int>): Pair<Double, Double> =
        sqrt(3.0) * (a.first + a.second / 2.0) to -1.5 * a.second

    private fun screen(id: String): Pair<Float, Float> {
        val (wx, wy) = axialToWorld(HexGrid.parse(id))
        return width / 2f + (wx.toFloat() - centerX) * size to height / 2f + (wy.toFloat() - centerY) * size
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 26, 34))
        if (route.isNotEmpty()) drawRoute(canvas) else drawTerritory(canvas)
    }

    private fun drawTerritory(canvas: Canvas) {
        for (t in tiles) {
            val tileColor = when {
                t.owned -> OWNED[(t.level - 1).coerceIn(0, OWNED.size - 1)]
                t.visited -> Color.rgb(46, 64, 92)
                else -> Color.rgb(36, 40, 48)
            }
            drawHex(canvas, t.id, tileColor, if (t.buyCost != null) Palette.ACCENT else null)
            val (x, y) = screen(t.id)
            label.textSize = size * 0.55f
            when {
                t.special != null -> canvas.drawText("★", x, y + size * 0.2f, label.apply { color = Color.rgb(255, 210, 90) })
                t.owned -> canvas.drawText("${t.level}", x, y + size * 0.2f, label.apply { color = Color.WHITE })
            }
            if (t.homeRoute) canvas.drawCircle(x + size * 0.45f, y - size * 0.45f, size * 0.12f, fill.apply { color = Color.rgb(255, 210, 90) })
        }
        selected?.let { drawHex(canvas, it, null, Color.WHITE, 4f) }
    }

    private fun drawRoute(canvas: Canvas) {
        for (t in tiles) drawHex(canvas, t.id, if (t.owned) Color.rgb(40, 70, 56) else Color.rgb(34, 40, 50), null)
        for (s in route) {
            val segColor = when {
                s.hardBrake || s.warning -> Palette.WARN
                s.assisted -> Color.rgb(90, 220, 200)
                else -> Palette.GOOD
            }
            drawHex(canvas, s.id, segColor, null)
        }
    }

    private fun drawHex(canvas: Canvas, id: String, color: Int?, outline: Int?, outlineWidth: Float = 2f) {
        val (x, y) = screen(id)
        if (x < -size || y < -size || x > width + size || y > height + size) return
        hex.reset()
        for (i in 0 until 6) {
            val ang = Math.toRadians(60.0 * i - 30)
            val px = x + (size * 0.94f * cos(ang)).toFloat()
            val py = y + (size * 0.94f * sin(ang)).toFloat()
            if (i == 0) hex.moveTo(px, py) else hex.lineTo(px, py)
        }
        hex.close()
        if (color != null) canvas.drawPath(hex, fill.apply { this.color = color })
        if (outline != null) canvas.drawPath(hex, stroke.apply { this.color = outline; strokeWidth = outlineWidth })
    }

    /** 확대(>1)·축소(<1). 화면 가운데를 기준으로 한다. */
    fun zoom(factor: Float) {
        size = (size * factor).coerceIn(MIN_SIZE, MAX_SIZE)
        invalidate()
    }

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean { zoom(d.scaleFactor); return true }
    })

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (route.isNotEmpty()) return false
        scaler.onTouchEvent(e)
        if (scaler.isInProgress || e.pointerCount > 1) { dragging = true; lastX = e.x; lastY = e.y; return true }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(e.x - downX, e.y - downY) > 16) dragging = true
                if (dragging) {
                    centerX -= (e.x - lastX) / size
                    centerY -= (e.y - lastY) / size
                    lastX = e.x; lastY = e.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> if (!dragging) tileAt(e.x, e.y)?.let { selected = it; onSelect?.invoke(it) }
        }
        return true
    }

    /** 화면 좌표 → 타일 ID. 목록에 있는 타일만 고를 수 있다. */
    private fun tileAt(px: Float, py: Float): String? {
        val wx = (px - width / 2f) / size + centerX
        val wy = -((py - height / 2f) / size + centerY)
        val fr = wy / 1.5
        val fq = wx / sqrt(3.0) - fr / 2
        val fs = -fq - fr
        var q = Math.round(fq)
        var r = Math.round(fr)
        val s = Math.round(fs)
        if (abs(q - fq) > abs(r - fr) && abs(q - fq) > abs(s - fs)) q = -r - s else if (abs(r - fr) > abs(s - fs)) r = -q - s
        return "$q:$r".takeIf { it in byId }
    }

    private companion object {
        const val MIN_SIZE = 8f
        const val MAX_SIZE = 90f
        val OWNED = intArrayOf(
            Color.rgb(52, 120, 84), Color.rgb(60, 150, 96), Color.rgb(72, 176, 108), Color.rgb(96, 200, 120), Color.rgb(140, 220, 120),
        )
    }
}
