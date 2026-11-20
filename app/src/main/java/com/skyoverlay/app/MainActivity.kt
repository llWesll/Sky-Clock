package com.skyoverlay.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var fonts: Fonts
    private lateinit var preview: ClockCardView
    private lateinit var permChip: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            preview.update()
            handler.postDelayed(this, 1000)
        }
    }

    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val match = ViewGroup.LayoutParams.MATCH_PARENT

    private fun tv(s: String, size: Float, color: Int, face: Typeface): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        t.typeface = face
        return t
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(Palette.CARD, 20f, Palette.LINE, 1f)
        c.setPadding(dp(18f), dp(16f), dp(18f), dp(18f))
        c.layoutParams = LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(14f) }
        return c
    }

    private fun toggle(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(12f) }

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply { marginEnd = dp(12f) }
        texts.addView(tv(title, 14f, Palette.TEXT, fonts.poppins))
        texts.addView(tv(desc, 12f, Palette.DIM, fonts.poppins))

        val sw = Switch(this)
        sw.isChecked = checked
        sw.thumbTintList = ColorStateList.valueOf(Palette.CYAN)
        sw.trackTintList = ColorStateList.valueOf(Palette.LINE)
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }

        row.addView(texts)
        row.addView(sw)
        return row
    }

    private fun slider(
        title: String, lo: Int, hi: Int, current: Int,
        format: (Int) -> String, onChange: (Int) -> Unit
    ): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.layoutParams = LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(12f) }

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        val t = tv(title, 14f, Palette.TEXT, fonts.poppins)
        t.layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
        val value = tv(format(current), 14f, Palette.CYAN, fonts.poppinsMedium)
        head.addView(t)
        head.addView(value)

        val sb = SeekBar(this)
        sb.max = hi - lo
        sb.progress = current - lo
        sb.progressTintList = ColorStateList.valueOf(Palette.CYAN)
        sb.thumbTintList = ColorStateList.valueOf(Palette.CYAN)
        sb.progressBackgroundTintList = ColorStateList.valueOf(Palette.LINE)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + lo
                value.text = format(v)
                onChange(v)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        box.addView(head)
        box.addView(sb)
        return box
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fonts = Fonts(this)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(20f), dp(24f), dp(20f), dp(32f))

        // ---- cabecalho
        content.addView(tv("Sky Overlay", 28f, Palette.TEXT, fonts.poppinsMedium))
        content.addView(tv("Relógio dos eventos de cera", 14f, Palette.DIM, fonts.poppins))

        // ---- previa ao vivo, sobre um fundo de "ceu" para a transparencia aparecer
        content.addView(tv("PRÉVIA", 11f, Palette.DIM, fonts.poppinsMedium).apply {
            letterSpacing = 0.14f
            layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(22f) }
        })

        preview = ClockCardView(this)
        preview.applyScale(prefs.getFloat("size", 13f) / 13f)
        preview.alpha = prefs.getFloat("alpha", 0.7f)
        preview.setOptions(Options.events(prefs), Options.shards(prefs))

        val stage = FrameLayout(this)
        stage.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#2F5D8C"), Color.parseColor("#7A5C9E"), Color.parseColor("#E39A8B"))
        ).apply { cornerRadius = dp(20f).toFloat() }
        stage.setPadding(dp(20f), dp(28f), dp(20f), dp(28f))
        stage.layoutParams = LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(8f) }
        stage.addView(preview, FrameLayout.LayoutParams(wrap, wrap, Gravity.CENTER_HORIZONTAL))
        content.addView(stage)

        // ---- permissao
        val perm = card()
        val permRow = LinearLayout(this)
        permRow.orientation = LinearLayout.HORIZONTAL
        permRow.gravity = Gravity.CENTER_VERTICAL
        val permTitle = tv("Sobreposição", 16f, Palette.TEXT, fonts.poppinsMedium)
        permTitle.layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
        permChip = tv("", 12f, Palette.TEXT, fonts.poppinsMedium)
        permChip.setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
        permRow.addView(permTitle)
        permRow.addView(permChip)
        perm.addView(permRow)
        perm.addView(tv("Necessária para o relógio aparecer por cima do jogo.", 13f, Palette.DIM, fonts.poppins))
        perm.addView(pillButton("Permitir exibir sobre outros apps", fonts, false) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        content.addView(perm)

        // ---- ligar / desligar
        val power = card()
        power.addView(pillButton("Mostrar relógio", fonts, true) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Falta a permissão de sobreposição", Toast.LENGTH_LONG).show()
            } else {
                startForegroundService(Intent(this, OverlayService::class.java))
            }
        })
        power.addView(pillButton("Esconder relógio", fonts, false) {
            stopService(Intent(this, OverlayService::class.java))
        })
        content.addView(power)

        // ---- aparencia
        val look = card()
        look.addView(tv("Aparência", 16f, Palette.TEXT, fonts.poppinsMedium))
        look.addView(slider("Opacidade", 20, 100, (prefs.getFloat("alpha", 0.7f) * 100).toInt().coerceIn(20, 100),
            { "$it%" }) { v ->
            prefs.edit().putFloat("alpha", v / 100f).apply()
            preview.alpha = v / 100f
        })
        look.addView(slider("Tamanho", 8, 32, prefs.getFloat("size", 13f).toInt().coerceIn(8, 32),
            { "$it" }) { v ->
            prefs.edit().putFloat("size", v.toFloat()).apply()
            preview.applyScale(v / 13f)
        })
        look.addView(tv("Arraste o relógio na tela para mudar de posição.", 13f, Palette.DIM, fonts.poppins).apply {
            layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(14f) }
        })
        content.addView(look)

        // ---- o que mostrar (juntos ou separados)
        val show = card()
        show.addView(tv("O que mostrar", 16f, Palette.TEXT, fonts.poppinsMedium))
        show.addView(toggle("Horários dos eventos", "Geyser e Vovó (contagem e barra)",
            Options.events(prefs)) { v ->
            prefs.edit().putBoolean("showEvents", v).apply()
            preview.setOptions(v, Options.shards(prefs))
        })
        show.addView(toggle("Alerta de fragmento", "Imagem e local enquanto ele rola",
            Options.shards(prefs)) { v ->
            prefs.edit().putBoolean("showShards", v).apply()
            preview.setOptions(Options.events(prefs), v)
        })
        if (BuildConfig.HAS_EVENTS && BuildConfig.HAS_SHARDS) content.addView(show)   // chaves so na versao completa

        // ---- alerta de fragmento
        val shard = card()
        shard.addView(tv("Alerta de fragmento", 16f, Palette.TEXT, fonts.poppinsMedium))
        shard.addView(tv(
            "Nos dias com fragmento, a imagem dele (preto ou vermelho) e o nome do local aparecem no relógio durante todo o período dele, do começo até pousar.",
            13f, Palette.DIM, fonts.poppins
        ).apply { layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(6f) } })
        shard.addView(pillButton("Testar fragmento vermelho", fonts, false) {
            preview.forcedAlert = Shards.Alert(ShardKind.RED, java.time.ZonedDateTime.now(Events.SKY_ZONE), "Ninho do Pássaro")
            preview.update()
        })
        shard.addView(pillButton("Testar fragmento preto", fonts, false) {
            preview.forcedAlert = Shards.Alert(ShardKind.BLACK, java.time.ZonedDateTime.now(Events.SKY_ZONE), "Deserto da Luz Estelar")
            preview.update()
        })
        if (BuildConfig.HAS_SHARDS) content.addView(shard)   // teste de fragmento so onde ha fragmento

        // ---- fundo em degrade + margens para a barra de status (Android 15+ desenha por baixo dela)
        val scroll = ScrollView(this)
        scroll.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Palette.BG_TOP, Palette.BG_BOTTOM)
        )
        scroll.addView(content)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars())
                top = i.top
                bottom = i.bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            v.setPadding(0, top, 0, bottom)
            insets
        }
        setContentView(scroll)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        val ok = Settings.canDrawOverlays(this)
        permChip.text = if (ok) "Permitida" else "Falta permitir"
        permChip.setTextColor(if (ok) Palette.OK else Palette.WARN)
        permChip.background = rounded(Color.parseColor("#1AFFFFFF"), 20f, if (ok) Palette.OK else Palette.WARN, 1f)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }
}
