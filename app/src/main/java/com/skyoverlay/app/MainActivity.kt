package com.skyoverlay.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        info = TextView(this).apply { textSize = 16f }
        box.addView(info)

        fun button(text: String, action: () -> Unit) {
            box.addView(Button(this).apply {
                this.text = text
                setOnClickListener { action() }
            })
        }

        button("1. Permitir exibir sobre outros apps") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        button("2. Mostrar relógio") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Falta a permissão do passo 1", Toast.LENGTH_LONG).show()
            } else {
                startForegroundService(Intent(this, OverlayService::class.java))
            }
        }
        button("Esconder relógio") { stopService(Intent(this, OverlayService::class.java)) }

        box.addView(TextView(this).apply { text = "\nTransparência (do mais transparente ao opaco)" })
        box.addView(SeekBar(this).apply {
            max = 80  // 20%..100%
            progress = ((prefs.getFloat("alpha", 0.7f) * 100).toInt() - 20).coerceIn(0, 80)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.edit().putFloat("alpha", (p + 20) / 100f).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        setContentView(box)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        info.text = "Sobreposição: " +
            (if (Settings.canDrawOverlays(this)) "permitida" else "falta permitir") +
            "\nArraste o relógio na tela para mudar de posição."
    }
}
