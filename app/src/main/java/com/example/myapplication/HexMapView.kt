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
        set(value) { field = value; byId = value.associateBy { it.id }; clusterCache.clear(); invalidate() }
    /** 이번 주행 경로. 있으면 경로 보기로 그린다. */
    var route: List<TileSegment> = emptyList()
        set(value) { field = value; invalidate() }
    var selected: String? = null
        set(value) { field = value; invalidate() }
    var onSelect: ((String) -> Unit)? = null

    var exploreRegions = false
    private data class Region(val x: Float, val y: Float, val count: Int, val ownedCount: Int)
    private val clusterCache = mutableMapOf<Int, List<Region>>()
    private val regionHits = mutableListOf<Triple<Float, Float, Region>>()

    /** 대한민국 본토와 제주·울릉도·독도를 포함하는 시작 범위. */
    fun showKorea() {
        fit(listOf(HexGrid.tileFor(32.7, 124.3), HexGrid.tileFor(38.8, 132.1)))
        size *= 0.86f
        selected = null
        invalidate()
    }

    private fun regionSpan(): Int = when {
        size < 0.06f -> 8000 // 메르카토르 100 km 권역
        size < 0.4f -> 1600
        size < 3f -> 160
        else -> 16
    }

    private fun drawRegions(canvas: Canvas) {
        val span = regionSpan()
        val regions = clusterCache.getOrPut(span) {
            tiles.filter { it.owned || it.visited }
                .map { it to axialToWorld(HexGrid.parse(it.id)) }
                .groupBy { (_, point) -> floor(point.first / span).toLong() to floor(point.second / span).toLong() }
                .values.map { entries ->
                    Region(entries.map { it.second.first }.average().toFloat(),
                        entries.map { it.second.second }.average().toFloat(), entries.size,
                        entries.count { it.first.owned })
                }
        }
        regionHits.clear()
        val density = resources.displayMetrics.density
        val radius = 32f * density
        for (region in regions) {
            val x = width / 2f + (region.x - centerX) * size
            val y = height / 2f + (region.y - centerY) * size
            if (x < -radius || y < -radius || x > width + radius || y > height + radius) continue
            val color = if (region.ownedCount == region.count) Palette.ACCENT else Palette.ASSIST
            canvas.drawCircle(x, y, radius * 1.45f, fill.apply { this.color = withAlpha(color, 65) })
            canvas.drawCircle(x, y, radius, fill.apply { this.color = color })
            label.apply { textSize = 20f * density; this.color = Palette.ON_ACCENT; isFakeBoldText = true }
            canvas.drawText(region.count.toString(), x, y - (label.ascent() + label.descent()) / 2, label)
            val caption = when {
                region.ownedCount == region.count -> "내 땅 ${region.count}"
                region.ownedCount == 0 -> "지나온 땅 ${region.count}"
                else -> "내 땅 ${region.ownedCount} · 지나온 땅 ${region.count - region.ownedCount}"
            }
            label.textSize = 16f * density
            val textWidth = label.measureText(caption)
            canvas.drawRoundRect(x - textWidth / 2 - 10 * density, y + radius + 6 * density,
                x + textWidth / 2 + 10 * density, y + radius + 34 * density, 8 * density, 8 * density,
                fill.apply { this.color = Palette.CARD })
            label.color = Palette.INK
            canvas.drawText(caption, x, y + radius + 26 * density, label)
            regionHits.add(Triple(x, y, region))
        }
    }

    private fun openRegion(x: Float, y: Float) {
        val hit = regionHits.minByOrNull { hypot(it.first - x, it.second - y) } ?: return
        if (hypot(hit.first - x, hit.second - y) > 64f * resources.displayMetrics.density) return
        centerX = hit.third.x
        centerY = hit.third.y
        size = when { size < 0.06f -> 0.15f; size < 0.4f -> 1f; size < 3f -> 6f; else -> 24f }
        selected = null
        invalidate()
    }

    private var byId: Map<String, LandTile> = emptyMap()
    private var size = 28f
    private var centerX = 0f
    private var centerY = 0f
    private var fitted = false
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.WHITE }
    private val hex = Path()
    // 지명과 도로를 읽을 수 있는 밝기를 유지하면서 영토 색을 강조한다.
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply {
            setSaturation(0.45f)
            postConcat(android.graphics.ColorMatrix().apply { setScale(0.72f, 0.74f, 0.76f, 1f) })
        })
    }
    /** 내 땅의 빛나는 테두리(바깥으로 번지는 라임). */
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Palette.ACCENT
        maskFilter = android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.OUTER)
    }
    private val credit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 200, 205, 215); textAlign = Paint.Align.RIGHT }
    private val tileRect = RectF()
    private val tileSrc = android.graphics.Rect()
    private val mapTiles = MapTiles(context) { postInvalidateOnAnimation() }

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
        canvas.drawColor(Color.rgb(14, 14, 15))
        drawBaseMap(canvas)
        if (route.isNotEmpty()) drawRoute(canvas) else if (exploreRegions && size < 10f) drawRegions(canvas) else drawTerritory(canvas)
        credit.textSize = 11f * resources.displayMetrics.density
        canvas.drawText(MapTiles.ATTRIBUTION, width - 8f, height - 8f, credit)
    }

    /**
     * 지금 보이는 범위의 지도 타일을 깐다. 아직 안 받은 칸은 메모리에 있는 더 넓은 줌 타일을 늘려 메우고,
     * 화면 바깥 둘레와 한 단계 넓은 줌은 미리 받아 둬서 끌거나 줄일 때 빈 칸이 안 보이게 한다.
     */
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
        mapTiles.setViewport(z, x0, y0, cols, rows)
        for (ty in y0..y0 + rows) for (tx in x0..x0 + cols) {
            if (ty < 0 || ty >= n) continue
            val sx = ((tx - left) * tilePx).toFloat()
            val sy = ((ty - top) * tilePx).toFloat()
            tileRect.set(sx, sy, sx + tilePx, sy + tilePx)
            val wrapped = Math.floorMod(tx, n)
            val bmp = mapTiles.get(z, wrapped, ty)
            if (bmp != null) { canvas.drawBitmap(bmp, null, tileRect, tilePaint); continue }
            drawFromCoarser(canvas, z, wrapped, ty)
        }
        // 한 단계 넓은 줌의 같은 범위와 화면 바깥 둘레(PREFETCH_RING칸)를 미리 받는다.
        // 미리 받기는 나중에 줄 선 것부터 받으므로, 먼 것부터 줄 세워 화면에 가까운 둘레가 먼저 오게 한다.
        if (z > 1) {
            val pn = n / 2
            for (ty in (y0 - PREFETCH_RING) / 2..(y0 + rows + PREFETCH_RING) / 2) {
                for (tx in (x0 - PREFETCH_RING) / 2..(x0 + cols + PREFETCH_RING) / 2) prefetch(z - 1, pn, tx, ty)
            }
        }
        for (ring in PREFETCH_RING downTo 1) {
            for (tx in x0 - ring..x0 + cols + ring) {
                prefetch(z, n, tx, y0 - ring)
                prefetch(z, n, tx, y0 + rows + ring)
            }
            for (ty in y0 - ring + 1 until y0 + rows + ring) {
                prefetch(z, n, x0 - ring, ty)
                prefetch(z, n, x0 + cols + ring, ty)
            }
        }
    }

    private fun prefetch(z: Int, n: Int, tx: Int, ty: Int) {
        if (ty in 0 until n) mapTiles.prefetch(z, Math.floorMod(tx, n), ty)
    }

    /** 아직 없는 타일 자리를 메모리에 있는 더 넓은 줌 타일(최대 MAX_FALLBACK 단계 위)의 해당 조각으로 메운다. */
    private fun drawFromCoarser(canvas: Canvas, z: Int, x: Int, y: Int) {
        for (k in 1..minOf(MAX_FALLBACK, z)) {
            val coarse = mapTiles.peek(z - k, x shr k, y shr k) ?: continue
            val part = coarse.width shr k
            if (part == 0) return
            val sx = (x and ((1 shl k) - 1)) * part
            val sy = (y and ((1 shl k) - 1)) * part
            tileSrc.set(sx, sy, sx + part, sy + part)
            canvas.drawBitmap(coarse, tileSrc, tileRect, tilePaint)
            return
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
                t.ownedByOther -> withAlpha(Palette.RIVAL, 150) // 다른 차의 땅: 빼앗을 수 있다
                t.visited -> withAlpha(Palette.ASSIST, 125)
                else -> Color.argb(60, 30, 30, 32)
            }
            val outline = when {
                t.owned -> Palette.ACCENT
                t.ownedByOther -> Palette.RIVAL
                t.visited -> Palette.ASSIST // 지나온 미소유 구간
                else -> null
            }
            if (t.owned && size >= 6) drawHex(canvas, t.id, null, null, glowPaint = glow)
            drawHex(canvas, t.id, tileColor, outline)
            val (x, y) = screen(t.id)
            label.textSize = size * if (t.level >= 10) 0.45f else 0.55f
            if (size < 14) continue // 너무 작으면 글자는 생략
            if (t.owned) canvas.drawText("${t.level}", x, y + size * 0.2f, label.apply { color = Palette.ON_ACCENT; isFakeBoldText = true })
            if (t.homeRoute) canvas.drawCircle(x + size * 0.45f, y - size * 0.45f, size * 0.12f, fill.apply { color = Color.rgb(255, 210, 90) })
        }
        selected?.let { drawHex(canvas, it, null, Color.WHITE, 4f) }
    }

    private fun drawRoute(canvas: Canvas) {
        for (t in tiles) drawHex(canvas, t.id, if (t.owned) withAlpha(Palette.ACCENT, 70) else Color.argb(40, 255, 255, 255), null)
        for (s in route) {
            val segColor = when {
                s.hardBrake || s.warning -> Palette.WARN
                s.assisted -> Palette.ASSIST
                else -> Palette.GOOD
            }
            drawHex(canvas, s.id, segColor, null)
        }
    }

    private fun drawHex(canvas: Canvas, id: String, color: Int?, outline: Int?, outlineWidth: Float = 2f, glowPaint: Paint? = null) {
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
        if (glowPaint != null) canvas.drawPath(hex, glowPaint.apply { strokeWidth = maxOf(2f, size * 0.12f) })
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
            MotionEvent.ACTION_UP -> if (!dragging) {
                if (exploreRegions && size < 10f) openRegion(e.x, e.y)
                else tileAt(e.x, e.y)?.let { selected = it; onSelect?.invoke(it) }
            }
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
        const val MIN_SIZE = 0.003f
        const val MAX_SIZE = 120f
        /** 영토 화면을 처음 열 때 크기. 타일 한 칸이 손가락으로 고를 만큼(약 16px, 화면 너비 약 400m). */
        const val STREET_SIZE = 16f
        /** 화면 바깥으로 미리 받아 두는 지도 타일 둘레(칸). 256px 타일 2칸이면 손가락으로 한 번 끄는 거리쯤이다. */
        private const val PREFETCH_RING = 2
        /** 빈 칸을 메울 때 거슬러 올라가는 줌 단계 수. */
        private const val MAX_FALLBACK = 4
        /** 웹 메르카토르 평면 한 변(지구 둘레, 미터). */
        const val WORLD_M = 2 * Math.PI * 6378137.0
        const val MAX_LEVEL = 60

        /** 레벨 1(짙은 초록)에서 [MAX_LEVEL](밝은 연두)까지 이어지는 색. 지도가 비치게 반투명. */
        /** 레벨 1(옅은 라임)에서 [MAX_LEVEL](꽉 찬 라임)까지 진해진다. 낮은 레벨은 지도가 비친다. */
        fun ownedColor(level: Int): Int {
            val f = ((level - 1).toFloat() / (MAX_LEVEL - 1)).coerceIn(0f, 1f)
            return withAlpha(Palette.ACCENT, (150 + 100 * f).toInt())
        }

        fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
