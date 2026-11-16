package com.skyoverlay.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import java.util.Locale

object Palette {
    val BG_TOP = Color.parseColor("#0F1B33")
    val BG_BOTTOM = Color.parseColor("#1A2D52")
    val CARD = Color.parseColor("#1F3157")
    val LINE = Color.parseColor("#33FFFFFF")
    val CYAN = Color.parseColor("#5CD6FF")   // mesma cor do icone do Sky Clock
    val GOLD = Color.parseColor("#FFD36E")   // chama da vela: evento acontecendo agora
    val TEXT = Color.WHITE
    val DIM = Color.parseColor("#A9B7D0")
    val OK = Color.parseColor("#7BE0A4")
    val WARN = Color.parseColor("#FFB066")
}

class Fonts(ctx: Context) {
    val poppins: Typeface = ctx.resources.getFont(R.font.poppins_regular)
    val poppinsMedium: Typeface = ctx.resources.getFont(R.font.poppins_medium)
    val interLight: Typeface = ctx.resources.getFont(R.font.inter_light)
}

fun Context.dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.rounded(color: Int, radiusDp: Float, strokeColor: Int = 0, strokeDp: Float = 0f): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0f) setStroke(dp(strokeDp), strokeColor)
    }

fun Context.pillButton(text: String, fonts: Fonts, primary: Boolean, onClick: () -> Unit): Button {
    val b = Button(this)
    b.text = text
    b.isAllCaps = false
    b.stateListAnimator = null
    b.typeface = fonts.poppinsMedium
    b.textSize = 15f
    b.setTextColor(if (primary) Color.parseColor("#0A1426") else Palette.TEXT)
    val base = if (primary) rounded(Palette.CYAN, 14f) else rounded(Color.TRANSPARENT, 14f, Palette.LINE, 1f)
    b.background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), base, null)
    b.setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
    b.setOnClickListener { onClick() }
    b.layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(10f) }
    return b
}

fun clock(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%02d:%02d", m, s)
}
