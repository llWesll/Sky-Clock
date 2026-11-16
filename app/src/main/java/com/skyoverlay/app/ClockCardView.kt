package com.skyoverlay.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Barrinha fina de progresso. */
class BarView(ctx: Context) : View(ctx) {
    var fraction: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    var barColor: Int = Palette.CYAN
        set(v) {
            field = v
            invalidate()
        }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#26FFFFFF") }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, track)
        if (fraction > 0f) {
            fill.color = barColor
            c.drawRoundRect(0f, 0f, width * fraction, height.toFloat(), r, r, fill)
        }
    }
}

/** Mostra UM evento por vez: o que esta acontecendo agora, ou o mais proximo de comecar. */
class ClockCardView(ctx: Context) : LinearLayout(ctx) {

    private val fonts = Fonts(ctx)
    private val row = LinearLayout(ctx)
    private val dot = View(ctx)
    private val label = TextView(ctx)
    private val name = TextView(ctx)
    private val time = TextView(ctx)
    private val bar = BarView(ctx)
    private val sub = TextView(ctx)
    private var scale = -1f
    private val fmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun d(v: Float) = context.dp(v)

    init {
        orientation = LinearLayout.VERTICAL

        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Palette.CYAN)
        }
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(dot)
        row.addView(label)
        row.addView(name)

        label.typeface = fonts.poppinsMedium
        label.letterSpacing = 0.14f
        name.typeface = fonts.poppinsMedium
        name.setTextColor(Palette.TEXT)
        time.typeface = fonts.interLight
        time.fontFeatureSettings = "tnum"       // algarismos de largura fixa: o numero nao "pula"
        time.includeFontPadding = false
        sub.typeface = fonts.poppins
        sub.setTextColor(Palette.DIM)

        addView(row)
        addView(time)
        addView(bar)
        addView(sub)

        applyScale(1f)
        update()
    }

    /** s = 1.0 e o tamanho padrao; o controle de tamanho do app muda isso. */
    fun applyScale(s: Float) {
        if (s == scale) return
        scale = s

        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#F20F1B33"), Color.parseColor("#F21A2D52"))
        ).apply {
            cornerRadius = d(20f * s).toFloat()
            setStroke(d(1f), Color.parseColor("#33FFFFFF"))
        }
        setPadding(d(16f * s), d(12f * s), d(16f * s), d(14f * s))
        minimumWidth = d(190f * s)

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val match = ViewGroup.LayoutParams.MATCH_PARENT

        dot.layoutParams = LinearLayout.LayoutParams(d(8f * s), d(8f * s)).apply { marginEnd = d(8f * s) }
        label.textSize = 10f * s
        name.textSize = 15f * s
        name.layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = d(8f * s) }
        time.textSize = 38f * s
        time.layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = d(4f * s) }
        bar.layoutParams = LinearLayout.LayoutParams(match, d(4f * s)).apply { topMargin = d(8f * s) }
        sub.textSize = 11f * s
        sub.layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = d(8f * s) }
    }

    fun update() {
        val p = Events.pick()
        val st = p.state
        val accent = if (st.active) Palette.GOLD else Palette.CYAN

        label.text = if (st.active) "AGORA" else "PRÓXIMO"
        label.setTextColor(accent)
        (dot.background as GradientDrawable).setColor(accent)
        name.text = p.event.name

        time.text = clock(st.seconds)
        time.setTextColor(if (st.active) Palette.GOLD else Palette.TEXT)

        val total = if (st.active) p.event.durationMin * 60f else 7200f
        bar.barColor = accent
        bar.fraction = 1f - st.seconds / total

        val local = st.start.withZoneSameInstant(ZoneId.systemDefault()).format(fmt)
        sub.text = if (st.active) "começou às $local" else "começa às $local"
    }
}
