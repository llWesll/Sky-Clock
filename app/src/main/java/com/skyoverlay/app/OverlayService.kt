package com.skyoverlay.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.skyoverlay.app.STOP"
        private const val CHANNEL = "overlay"
    }

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var card: ClockCardView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            try {
                update()
            } catch (e: Exception) {
                Log.e("SkyOverlay", "falha ao atualizar o relogio", e)
            }
            // alinha com a virada do segundo: sem atraso acumulado e sem trabalho extra
            handler.postDelayed(this, 1000L - (System.currentTimeMillis() % 1000L) + 20L)
        }
    }

    // tela apagada: para de atualizar (economiza bateria e CPU); volta ao acender
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(tick)
            if (intent.action == Intent.ACTION_SCREEN_ON) handler.post(tick)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sky Overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Relógio do Sky ativo")
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Parar", stop).build())
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, n)
            }
            buildOverlay()
        } catch (e: Exception) {
            // sem permissao de sobreposicao ou sistema nao deixou iniciar: encerra sem derrubar o app
            Log.e("SkyOverlay", "nao foi possivel iniciar o overlay", e)
            stopSelf()
            return
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        if (getSystemService(PowerManager::class.java).isInteractive) handler.post(tick) else update()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // NOT_STICKY: se o sistema matar o app, nao tenta reiniciar o servico em segundo plano
        // (no Android 12+ isso derruba o app ao chamar startForeground)
        return START_NOT_STICKY
    }

    private fun buildOverlay() {
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", 40)
            y = prefs.getInt("y", 200)
        }

        val c = ClockCardView(this)
        c.applyScale(prefs.getFloat("size", 13f) / 13f)
        c.alpha = prefs.getFloat("alpha", 0.7f)
        c.setOptions(Options.events(prefs), Options.shards(prefs))
        c.showOngoingShardOnStart()   // se um fragmento ja estiver rolando ao iniciar, avisa ate ele pousar

        // arrastar a janela com o dedo
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        c.setOnTouchListener { _: View, ev: MotionEvent ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = ev.rawX
                    touchY = ev.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (ev.rawX - touchX).toInt()
                    lp.y = startY + (ev.rawY - touchY).toInt()
                    if (card != null) wm.updateViewLayout(c, lp)
                }
                MotionEvent.ACTION_UP ->
                    prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
            }
            true
        }

        card = c
        wm.addView(c, lp)
    }

    private fun update() {
        val c = card ?: return
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        c.alpha = prefs.getFloat("alpha", 0.7f)
        c.applyScale(prefs.getFloat("size", 13f) / 13f)
        c.setOptions(Options.events(prefs), Options.shards(prefs))
        c.update()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try { unregisterReceiver(screenReceiver) } catch (_: IllegalArgumentException) {}
        card?.let { try { wm.removeView(it) } catch (_: IllegalArgumentException) {} }
        card = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
