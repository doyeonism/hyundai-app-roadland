package com.example.myapplication

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** 차량 화면 공통 색. 최종 디자인이 오면 여기만 바꾼다. */
object Palette {
    val BG = Color.rgb(16, 20, 26)
    val CARD = Color.rgb(28, 34, 43)
    val INK = Color.rgb(236, 240, 245)
    val MUTED = Color.rgb(150, 160, 172)
    val ACCENT = Color.rgb(122, 162, 255)
    val GOOD = Color.rgb(92, 201, 138)
    val WARN = Color.rgb(240, 166, 90)
    val TRACK = Color.rgb(48, 56, 68)
}

/** 코드로 뷰를 만드는 작은 도우미. 운전석 터치를 고려해 버튼은 최소 80dp. */
class Ui(val ctx: Context) {
    fun dp(v: Number) = (v.toFloat() * ctx.resources.displayMetrics.density).roundToInt()

    fun text(value: CharSequence, size: Float = 22f, color: Int = Palette.INK, bold: Boolean = false) = TextView(ctx).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setLineSpacing(0f, 1.15f)
    }

    fun button(title: String, primary: Boolean = true, onClick: () -> Unit) = Button(ctx).apply {
        text = title
        textSize = 22f
        isAllCaps = false
        minHeight = dp(80)
        setTextColor(if (primary) Color.WHITE else Palette.ACCENT)
        background = rounded(if (primary) Palette.ACCENT else Palette.CARD, 18, if (primary) null else Palette.ACCENT)
        setOnClickListener { onClick() }
    }

    fun rounded(color: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(2), stroke)
    }

    fun column(gap: Int = 12) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        dividerDrawable = GradientDrawable().apply { setSize(1, dp(gap)) }
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }

    fun row(gap: Int = 12) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        dividerDrawable = GradientDrawable().apply { setSize(dp(gap), 1) }
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }

    fun card(gap: Int = 10) = column(gap).apply {
        background = rounded(Palette.CARD, 20)
        setPadding(dp(24), dp(20), dp(24), dp(20))
    }

    /** 가로 막대. value/max 비율만큼 채운다. */
    fun bar(value: Int, max: Int, color: Int = Palette.ACCENT): View = LinearLayout(ctx).apply {
        val ratio = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
        background = rounded(Palette.TRACK, 6)
        addView(View(ctx).apply { background = rounded(color, 6) }, LinearLayout.LayoutParams(0, -1, ratio))
        addView(View(ctx), LinearLayout.LayoutParams(0, -1, 1f - ratio))
        layoutParams = LinearLayout.LayoutParams(-1, dp(12))
    }

    fun matchWidth() = LinearLayout.LayoutParams(-1, -2)
    fun weight(w: Float) = LinearLayout.LayoutParams(0, -2, w)
}
