package com.skyoverlay.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
            update()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
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
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        wm = getSystemService(WindowManager::class.java)
        buildOverlay()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
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
        c.setOptions(prefs.getBoolean("showEvents", true), prefs.getBoolean("showShards", true))
        c.showOngoingShardOnStart()   // se um fragmento ja estiver rolando ao iniciar, avisa por ate 1 minuto

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
                    wm.updateViewLayout(c, lp)
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
        c.setOptions(prefs.getBoolean("showEvents", true), prefs.getBoolean("showShards", true))
        c.update()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        card?.let { wm.removeView(it) }
        card = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
