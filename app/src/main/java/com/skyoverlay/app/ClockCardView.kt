package com.skyoverlay.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Barrinha fina de progresso. */
class BarView(ctx: Context) : View(ctx) {
    var fraction: Float = 0f
        set(v) {
            val n = v.coerceIn(0f, 1f)
            if (n == field) return
            field = n
            invalidate()
        }
    var barColor: Int = Palette.CYAN
        set(v) {
            if (v == field) return
            field = v
            invalidate()
        }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#26FFFFFF") }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Sem largura propria: quem define a largura e o card (senao a barra ocupa a tela toda e estica tudo). */
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = if (MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthSpec) else 0
        setMeasuredDimension(w, getDefaultSize(suggestedMinimumHeight, heightSpec))
    }

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
    private val body = LinearLayout(ctx)   // horizontal: tempo grande | nome e horario
    private val info = LinearLayout(ctx)   // vertical: linha do evento + "comeca as"
    private val dot = View(ctx)
    private val label = TextView(ctx)
    private val name = TextView(ctx)
    private val time = TextView(ctx)
    private val bar = BarView(ctx)
    private val sub = TextView(ctx)
    private val alertBox = LinearLayout(ctx)   // faixa do alerta: imagem do fragmento + nome do local
    private val alertImg = ImageView(ctx)
    private val alertTv = TextView(ctx)
    private var alertKind: ShardKind? = null
    private var scale = -1f
    private var lastActive: Boolean? = null   // so mexe em cores/rotulo quando o estado do evento muda

    private companion object {
        const val PREVIEW_SEC = 60L   // o teste de fragmento da previa dura 1 minuto
        const val STARTUP_SEC = 60L   // aviso ao iniciar quando o fragmento ja esta no chao
    }

    /** Evita refazer o layout da janela por texto igual (o overlay inteiro e redesenhado a cada mudanca). */
    private fun TextView.setIfChanged(t: String) {
        if (text.toString() != t) text = t
    }

    /** Usado so na previa do app para testar o alerta; some sozinho quando o tempo acaba. */
    var forcedAlert: Shards.Alert? = null

    /** Alerta mostrado ao iniciar a sobreposicao, se um fragmento ja estiver rolando nesse momento. */
    private var startupAlert: Shards.Alert? = null
    private var startupUntil: ZonedDateTime? = null

    /**
     * Chamado uma vez quando o overlay inicia. Se um fragmento esta ativo agora, avisa:
     * ainda caindo -> ate ele pousar; ja no chao -> por STARTUP_SEC.
     */
    fun showOngoingShardOnStart() {
        val now = ZonedDateTime.now(Events.SKY_ZONE)
        val a = Shards.ongoing(now) ?: return
        val toLand = a.secondsToLand(now)
        startupAlert = a
        startupUntil = now.plusSeconds(if (toLand > 0) toLand else STARTUP_SEC)
    }

    /** Opcoes de exibicao (podem ficar ligadas juntas ou separadas). */
    private var showEvents = true
    private var showShards = true

    fun setOptions(events: Boolean, shards: Boolean) {
        if (events == showEvents && shards == showShards) return
        showEvents = events
        showShards = shards
        update()
    }
    private val fmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun d(v: Float) = context.dp(v)

    /** Tamanho de texto em dp (sem depender da escala de fonte do sistema), para acompanhar o resto do card. */
    private fun TextView.size(dpValue: Float) =
        setTextSize(TypedValue.COMPLEX_UNIT_PX, dpValue * context.resources.displayMetrics.density)

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
        alertTv.typeface = fonts.poppinsMedium
        alertTv.setTextColor(Color.WHITE)
        alertTv.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        alertTv.maxLines = 2
        alertImg.scaleType = ImageView.ScaleType.FIT_CENTER
        alertBox.orientation = LinearLayout.HORIZONTAL
        alertBox.gravity = Gravity.CENTER_VERTICAL
        alertBox.visibility = View.GONE
        alertBox.addView(alertImg)
        alertBox.addView(alertTv)

        // uma linha so: sem quebra, nada fica cortado nem empurra o card
        listOf(label, name, time, sub).forEach { it.isSingleLine = true }

        info.orientation = LinearLayout.VERTICAL
        info.gravity = Gravity.CENTER_VERTICAL
        info.addView(row)
        info.addView(sub)

        body.orientation = LinearLayout.HORIZONTAL
        body.gravity = Gravity.CENTER_VERTICAL
        body.addView(time)
        body.addView(info)

        // retangular: faixa de alerta em cima, tempo + info lado a lado, barra embaixo
        addView(alertBox)
        addView(body)
        addView(bar)

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
        minimumWidth = d(250f * s)

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val match = ViewGroup.LayoutParams.MATCH_PARENT

        alertBox.setPadding(d(10f * s), d(6f * s), d(10f * s), d(6f * s))
        alertBox.layoutParams = LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = d(10f * s) }
        alertImg.layoutParams = LinearLayout.LayoutParams(d(32f * s), d(32f * s)).apply { marginEnd = d(10f * s) }
        alertTv.size(13f * s)
        alertTv.maxWidth = d(150f * s)   // nomes maiores quebram em 2 linhas em vez de alargar o card
        alertTv.layoutParams = LinearLayout.LayoutParams(wrap, wrap)
        alertKind = null   // refaz o fundo do alerta com o novo tamanho no proximo update()

        row.layoutParams = LinearLayout.LayoutParams(wrap, wrap)
        dot.layoutParams = LinearLayout.LayoutParams(d(8f * s), d(8f * s)).apply { marginEnd = d(8f * s) }
        label.size(10f * s)
        name.size(15f * s)
        name.layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = d(8f * s) }
        time.size(34f * s)
        time.minWidth = Math.ceil(time.paint.measureText("0:00:00").toDouble()).toInt()   // largura real do texto: nao corta e o card nao pula
        time.layoutParams = LinearLayout.LayoutParams(wrap, wrap)
        info.layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = d(14f * s) }
        body.layoutParams = LinearLayout.LayoutParams(match, wrap)
        bar.layoutParams = LinearLayout.LayoutParams(match, d(4f * s)).apply { topMargin = d(10f * s) }
        sub.size(11f * s)
        sub.layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = d(2f * s) }
    }

    /** Faixa com a imagem do fragmento (preto ou vermelho) + nome do local, durante todo o periodo dele. */
    private fun updateAlert(): Boolean {
        val now = ZonedDateTime.now(Events.SKY_ZONE)
        val forced = forcedAlert
        if (forced != null && forced.elapsedSec(now) >= PREVIEW_SEC) forcedAlert = null
        // o teste da previa aparece mesmo com o alerta desligado
        val until = startupUntil
        if (startupAlert != null && (until == null || !now.isBefore(until))) startupAlert = null
        val a = forcedAlert ?: if (showShards) (startupAlert ?: Shards.alert(now)) else null

        if (a == null) {
            alertBox.visibility = View.GONE
            alertKind = null
            return false
        }
        if (alertKind != a.kind) {
            alertKind = a.kind
            when (a.kind) {
                ShardKind.RED -> {
                    alertImg.setImageResource(R.drawable.shard_red)
                    alertBox.background = context.rounded(Palette.SHARD_RED, 12f * scale, Color.parseColor("#66FFFFFF"), 1f)
                }
                ShardKind.BLACK -> {
                    alertImg.setImageResource(R.drawable.shard_black)
                    // preto puro some no fundo escuro: borda clara para continuar visivel
                    alertBox.background = context.rounded(Palette.SHARD_BLACK, 12f * scale, Color.parseColor("#B3FFFFFF"), 1f)
                }
            }
        }
        alertTv.setIfChanged(a.place.ifEmpty { a.kind.label })
        alertBox.visibility = View.VISIBLE
        // sem a area de eventos embaixo, nao precisa de espaco depois da faixa
        (alertBox.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = if (showEvents) d(10f * scale) else 0
        return true
    }

    fun update() {
        val alertShown = updateAlert()
        body.visibility = if (showEvents) View.VISIBLE else View.GONE
        bar.visibility = if (showEvents) View.VISIBLE else View.GONE
        // nada para mostrar: o card some e a janela encolhe (nao bloqueia toques no jogo)
        visibility = if (showEvents || alertShown) View.VISIBLE else View.GONE
        if (!showEvents) return   // sem a area de eventos: nada para atualizar

        val p = Events.pick()
        val st = p.state
        if (lastActive != st.active) {
            lastActive = st.active
            val accent = if (st.active) Palette.GOLD else Palette.CYAN
            label.text = if (st.active) "AGORA" else "PRÓXIMO"
            label.setTextColor(accent)
            (dot.background as GradientDrawable).setColor(accent)
            time.setTextColor(if (st.active) Palette.GOLD else Palette.TEXT)
            bar.barColor = accent
        }
        name.setIfChanged(p.event.name)
        time.setIfChanged(clock(st.seconds))

        val total = if (st.active) p.event.durationMin * 60f else 7200f
        bar.fraction = 1f - st.seconds / total

        val local = st.start.withZoneSameInstant(ZoneId.systemDefault()).format(fmt)
        sub.setIfChanged(if (st.active) "começou às $local" else "começa às $local")
    }
}
