package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 육각 타일 지도. 영토 화면에서는 끌어서 옮기고, 두 손가락으로 확대하고, 눌러서 고른다. 도착 요약에서는 이번 경로를 색으로 보여 준다.
 * 밑에는 실제 지도 타일([MapTiles])을 깔고 그 위에 육각 타일을 반투명하게 쌓는다.
 * 육각 격자는 웹 메르카토르 평면 위라서, 축 좌표 한 칸 = [HexGrid.SIZE_M] 메르카토르 미터로 바로 지도 타일 좌표가 된다.
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
    // 밝은 지도를 어두운 화면에 맞춰 어둡게, 채도를 낮춰 깐다(타일 위 색이 잘 보이게).
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply {
            setSaturation(0.35f)
            postConcat(android.graphics.ColorMatrix().apply { setScale(0.5f, 0.52f, 0.58f, 1f) })
        })
    }
    private val credit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 200, 205, 215); textAlign = Paint.Align.RIGHT }
    private val tileRect = RectF()
    private val mapTiles = MapTiles(context) { invalidate() }

    /**
     * 이 타일들이 한눈에 들어오게 맞춘다. 타일이 몇 칸 안 되면 너무 커지지 않게 최소 [minSpan]칸 넓이로 본다.
     * [minSize]를 주면 타일들이 넓게 퍼져 있어도 그보다 작게 줄이지 않고 가운데를 그 크기로 보여 준다.
     */
    fun fit(ids: Collection<String>, minSpan: Float = 0f, minSize: Float = MIN_SIZE) {
        if (ids.isEmpty() || width == 0) { fitted = false; return }
        val pts = ids.map { axialToWorld(HexGrid.parse(it)) }
        val minX = pts.minOf { it.first }; val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }; val maxY = pts.maxOf { it.second }
        centerX = ((minX + maxX) / 2).toFloat()
        centerY = ((minY + maxY) / 2).toFloat()
        val spanX = maxOf((maxX - minX + 4).toFloat(), minSpan)
        val spanY = maxOf((maxY - minY + 4).toFloat(), minSpan)
        val whole = minOf(width / spanX, height / spanY)
        size = whole.coerceIn(minSize, MAX_SIZE)
        // 다 안 들어가서 확대해 보여 줄 때는 범위 가운데(꺾인 길이면 빈 곳일 수 있다) 대신 거기서 가장 가까운 타일을 가운데에 둔다.
        if (whole < minSize) {
            val near = pts.minBy { (it.first - centerX).let { d -> d * d } + (it.second - centerY).let { d -> d * d } }
            centerX = near.first.toFloat()
            centerY = near.second.toFloat()
        }
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
        drawBaseMap(canvas)
        if (route.isNotEmpty()) drawRoute(canvas) else drawTerritory(canvas)
        credit.textSize = 11f * resources.displayMetrics.density
        canvas.drawText(MapTiles.ATTRIBUTION, width - 8f, height - 8f, credit)
    }

    /** 지금 보이는 범위의 지도 타일을 깐다. 아직 안 받은 칸은 한 단계 위 줌 타일을 늘려 메운다. */
    private fun drawBaseMap(canvas: Canvas) {
        if (width == 0) return
        // 화면 1px = 1/size 칸 = SIZE_M/size 메르카토르 미터. 그보다 조금 촘촘한 줌을 골라 줄여 그린다.
        val metersPerPx = HexGrid.SIZE_M / size
        val z = ceil(ln(WORLD_M / (256 * metersPerPx)) / ln(2.0)).toInt().coerceIn(1, MapTiles.MAX_ZOOM)
        val n = 1 shl z
        val tilePx = (WORLD_M / n / metersPerPx).toFloat()
        // 화면 왼쪽 위의 타일 좌표(소수).
        val left = mercX(-width / 2f) / WORLD_M * n
        val top = mercYDown(-height / 2f) / WORLD_M * n
        val x0 = floor(left).toInt(); val y0 = floor(top).toInt()
        val cols = ceil(width / tilePx).toInt() + 1
        val rows = ceil(height / tilePx).toInt() + 1
        for (ty in y0..y0 + rows) for (tx in x0..x0 + cols) {
            if (ty < 0 || ty >= n) continue
            val sx = ((tx - left) * tilePx).toFloat()
            val sy = ((ty - top) * tilePx).toFloat()
            tileRect.set(sx, sy, sx + tilePx, sy + tilePx)
            val wrapped = Math.floorMod(tx, n)
            val bmp = mapTiles.get(z, wrapped, ty)
            if (bmp != null) { canvas.drawBitmap(bmp, null, tileRect, tilePaint); continue }
            // 위 줌의 같은 자리 1/4 조각으로 임시로 메운다.
            val parent = mapTiles.get(z - 1, wrapped / 2, ty / 2) ?: continue
            val half = parent.width / 2
            val src = android.graphics.Rect((wrapped % 2) * half, (ty % 2) * half, (wrapped % 2) * half + half, (ty % 2) * half + half)
            canvas.drawBitmap(parent, src, tileRect, tilePaint)
        }
    }

    /** 화면 가운데에서 dx 픽셀 떨어진 곳의 메르카토르 x(서쪽 끝 0 ~ 지구 둘레). */
    private fun mercX(dx: Float) = (centerX + dx / size) * HexGrid.SIZE_M + WORLD_M / 2

    /** 화면 가운데에서 dy 픽셀 떨어진 곳의 메르카토르 y(북쪽 끝 0에서 아래로). 화면 좌표는 아래가 +라 그대로 더한다. */
    private fun mercYDown(dy: Float) = (centerY + dy / size) * HexGrid.SIZE_M + WORLD_M / 2

    private fun drawTerritory(canvas: Canvas) {
        for (t in tiles) {
            val tileColor = when {
                t.owned -> ownedColor(t.level)
                t.ownedByOther -> Color.argb(170, 200, 80, 90) // 다른 차의 땅: 빼앗을 수 있다
                t.visited -> Color.argb(110, 70, 100, 150)
                else -> Color.argb(90, 36, 40, 48)
            }
            drawHex(canvas, t.id, tileColor, if (t.buyCost != null) Palette.ACCENT else null)
            val (x, y) = screen(t.id)
            label.textSize = size * if (t.level >= 10) 0.45f else 0.55f
            if (size < 14) continue // 너무 작으면 글자는 생략
            if (t.owned) canvas.drawText("${t.level}", x, y + size * 0.2f, label.apply { color = Color.WHITE })
            if (t.homeRoute) canvas.drawCircle(x + size * 0.45f, y - size * 0.45f, size * 0.12f, fill.apply { color = Color.rgb(255, 210, 90) })
        }
        selected?.let { drawHex(canvas, it, null, Color.WHITE, 4f) }
    }

    private fun drawRoute(canvas: Canvas) {
        for (t in tiles) drawHex(canvas, t.id, if (t.owned) Color.argb(120, 40, 110, 76) else Color.argb(80, 34, 40, 50), null)
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
        // 아주 작게 줄였을 때는 테두리가 칸을 덮으므로 고른 칸 표시만 남긴다.
        if (outline != null && (size >= 6 || outlineWidth > 2f)) canvas.drawPath(hex, stroke.apply { this.color = outline; strokeWidth = outlineWidth })
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

    companion object {
        /** 화면에서 타일 한 칸(약 10m)의 크기(px) 범위. 0.4px면 지도 칸이 수 km, 120px면 골목 단위. */
        const val MIN_SIZE = 0.4f
        const val MAX_SIZE = 120f
        /** 영토 화면을 처음 열 때 크기. 타일 한 칸이 손가락으로 고를 만큼(약 16px, 화면 너비 약 400m). */
        const val STREET_SIZE = 16f
        /** 웹 메르카토르 평면 한 변(지구 둘레, 미터). */
        const val WORLD_M = 2 * Math.PI * 6378137.0
        const val MAX_LEVEL = 60

        /** 레벨 1(짙은 초록)에서 [MAX_LEVEL](밝은 연두)까지 이어지는 색. 지도가 비치게 반투명. */
        fun ownedColor(level: Int): Int {
            val f = ((level - 1).toFloat() / (MAX_LEVEL - 1)).coerceIn(0f, 1f)
            fun mix(a: Int, b: Int) = (a + (b - a) * f).toInt()
            return Color.argb(mix(170, 225), mix(40, 170), mix(120, 230), mix(80, 110))
        }
    }
}
