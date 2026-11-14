package com.skyoverlay.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.skyoverlay.app.STOP"
        private const val CHANNEL = "overlay"
    }

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var box: LinearLayout? = null
    private val rows = ArrayList<TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private val fmt = DateTimeFormatter.ofPattern("HH:mm")

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

        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#E6000000"))
            cornerRadius = 24f
        }
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(28, 18, 28, 18)
        }
        Events.list.forEach { _ ->
            val t = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.MONOSPACE
            }
            b.addView(t)
            rows.add(t)
        }

        // arrastar a janela com o dedo
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        b.setOnTouchListener { _: View, ev: MotionEvent ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = ev.rawX; touchY = ev.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (ev.rawX - touchX).toInt()
                    lp.y = startY + (ev.rawY - touchY).toInt()
                    wm.updateViewLayout(b, lp)
                }
                MotionEvent.ACTION_UP ->
                    prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
            }
            true
        }

        box = b
        wm.addView(b, lp)
    }

    private fun clock(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun update() {
        val b = box ?: return
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        b.alpha = prefs.getFloat("alpha", 0.7f)
        val size = prefs.getFloat("size", 13f)
        val local = ZoneId.systemDefault()
        Events.list.forEachIndexed { i, e ->
            val st = Events.state(e)
            val t = rows[i]
            t.textSize = size
            if (st.active) {
                t.text = String.format("● %-9s AGORA  resta %s", e.name, clock(st.seconds))
                t.setTextColor(Color.rgb(120, 255, 140))
            } else {
                val at = st.start.withZoneSameInstant(local).format(fmt)
                t.text = String.format("  %-9s %s  em %s", e.name, at, clock(st.seconds))
                t.setTextColor(Color.WHITE)
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        box?.let { wm.removeView(it) }
        box = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
