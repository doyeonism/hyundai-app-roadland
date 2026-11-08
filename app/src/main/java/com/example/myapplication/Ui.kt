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

/**
 * 차량 화면 공통 색. 검정 바탕에 형광 라임 하나만 강조색으로 쓴다(러닝 영토 게임에서 흔한 다크 + 네온 스타일).
 * 라임 위 글자는 [ON_ACCENT](검정).
 */
object Palette {
    val BG = Color.rgb(10, 10, 11)
    val CARD = Color.rgb(22, 23, 25)
    val EDGE = Color.rgb(40, 42, 45)
    val INK = Color.rgb(245, 245, 242)
    val MUTED = Color.rgb(140, 143, 148)
    val ACCENT = Color.rgb(216, 242, 60)
    val ON_ACCENT = Color.rgb(12, 12, 12)
    val GOOD = ACCENT
    val WARN = Color.rgb(255, 138, 61)
    /** 다른 차의 땅. */
    val RIVAL = Color.rgb(255, 84, 112)
    /** 에코·크루즈로 지난 구간. */
    val ASSIST = Color.rgb(80, 220, 230)
    val TRACK = Color.rgb(42, 44, 47)
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

    /** 알약 모양 버튼. 주 버튼은 라임 바탕에 검정 굵은 글자, 보조 버튼은 어두운 바탕에 회색 테두리. */
    fun button(title: String, primary: Boolean = true, onClick: () -> Unit) = Button(ctx).apply {
        text = title
        textSize = 22f
        isAllCaps = false
        minHeight = dp(80)
        stateListAnimator = null
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(if (primary) Palette.ON_ACCENT else Palette.INK)
        background = rounded(if (primary) Palette.ACCENT else Palette.CARD, PILL, if (primary) null else Palette.EDGE)
        setOnClickListener { onClick() }
    }

    /** 작은 알약 꼬리표(상태 표시). 켜진 것은 라임, 아니면 어두운 테두리. */
    fun chip(label: String, on: Boolean = true) = text(label, 18f, if (on) Palette.ON_ACCENT else Palette.MUTED, bold = true).apply {
        background = rounded(if (on) Palette.ACCENT else Palette.CARD, PILL, if (on) null else Palette.EDGE)
        setPadding(dp(16), dp(6), dp(16), dp(6))
    }

    fun rounded(color: Int, radiusDp: Int, stroke: Int? = null, strokeDp: Float = 1.5f) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(strokeDp), stroke)
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
        background = rounded(Palette.CARD, 24, Palette.EDGE, 1f)
        setPadding(dp(28), dp(24), dp(28), dp(24))
    }

    /** 카드 위쪽의 작은 회색 제목. */
    fun label(value: String) = text(value, 18f, Palette.MUTED, bold = true)

    /** 가로 막대. value/max 비율만큼 채운다. */
    fun bar(value: Int, max: Int, color: Int = Palette.ACCENT): View = LinearLayout(ctx).apply {
        val ratio = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
        background = rounded(Palette.TRACK, PILL)
        addView(View(ctx).apply { background = rounded(color, PILL) }, LinearLayout.LayoutParams(0, -1, ratio))
        addView(View(ctx), LinearLayout.LayoutParams(0, -1, 1f - ratio))
        layoutParams = LinearLayout.LayoutParams(-1, dp(12))
    }

    fun matchWidth() = LinearLayout.LayoutParams(-1, -2)
    fun weight(w: Float) = LinearLayout.LayoutParams(0, -2, w)

    private companion object {
        /** 알약 모양이 되도록 충분히 큰 모서리(dp). */
        const val PILL = 999
    }
}
