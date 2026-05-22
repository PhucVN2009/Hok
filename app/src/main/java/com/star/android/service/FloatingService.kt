package com.star.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.star.android.utils.ShellUtils
import com.star.android.utils.SocketClient
import java.io.File

class FloatingService : Service() {

    companion object {
        init { System.loadLibrary("Starcool") }
    }

    // ── HOK package ──────────────────────────────────────────────
    private val target = "com.levelinfinite.sgameGlobal.midaspay"

    private lateinit var wm: WindowManager
    private var container: FrameLayout? = null
    private var scrollMenu: ScrollView? = null
    private var injected = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun makeParams(
        w: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        h: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        flags: Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    ): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    override fun onBind(i: android.content.Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupMenu()
        handler.post(autoInject)
    }

    private val autoInject = object : Runnable {
        override fun run() {
            Thread {
                val running = ShellUtils.runAsRoot("pidof $target")
                handler.post {
                    if (running) {
                        if (!injected && injectLib()) {
                            injected = true
                            Toast.makeText(this@FloatingService, "HOK Injected!", Toast.LENGTH_SHORT).show()
                        }
                    } else injected = false
                }
            }.start()
            handler.postDelayed(this, 1000)
        }
    }

    private fun setupMenu() {
        val menuParams = makeParams(580, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = 40; y = 120
        }

        val cont = FrameLayout(this)
        container = cont

        // ── Icon thu gọn ──────────────────────────────────────────
        val icon = ImageView(this).apply {
            setImageResource(com.star.android.R.drawable.logo)
            layoutParams = FrameLayout.LayoutParams(110, 110)
            setOnClickListener {
                visibility = View.GONE
                scrollMenu?.visibility = View.VISIBLE
                wm.updateViewLayout(cont, menuParams)
            }
            makeDraggable(this, menuParams, cont)
        }

        // ── ScrollView menu ───────────────────────────────────────
        val sv = ScrollView(this).apply { visibility = View.GONE }
        scrollMenu = sv

        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE000000"))
            setPadding(28, 28, 28, 28)

            // Header
            addView(TextView(context).apply {
                text = "⚡ HOK Mod ⚡"
                setTextColor(Color.YELLOW); textSize = 17f; gravity = Gravity.CENTER
                setPadding(0, 0, 0, 18)
                makeDraggable(this, menuParams, cont)
            })

            // Map Hack
            addView(TextView(context).apply {
                text = "📡 Map Hack"
                setTextColor(Color.parseColor("#FFD700")); textSize = 13f; setPadding(0, 10, 0, 4)
            })
            addView(CheckBox(context).apply {
                text = "🔍 Luôn nhìn thấy địch"
                setTextColor(Color.WHITE); textSize = 15f; setPadding(8, 4, 0, 4)
                setOnCheckedChangeListener { _, c -> SocketClient.setMapHack(c) }
            })

            // Buttons
            addView(View(context).apply {
                setBackgroundColor(Color.parseColor("#44FFFFFF"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { setMargins(0, 14, 0, 14) }
            })
            addView(Button(context).apply {
                text = "⬅ Thu gọn"
                setTextColor(Color.BLACK); setBackgroundColor(Color.LTGRAY)
                setPadding(16, 14, 16, 14)
                setOnClickListener {
                    sv.visibility = View.GONE
                    icon.visibility = View.VISIBLE
                    wm.updateViewLayout(cont, menuParams)
                }
            })
            addView(Button(context).apply {
                text = "❌ Thoát"
                setTextColor(Color.WHITE); setBackgroundColor(Color.RED)
                setPadding(16, 14, 16, 14)
                setOnClickListener { stopSelf() }
            })
        }

        sv.addView(menu)
        cont.addView(icon)
        cont.addView(sv)
        wm.addView(cont, menuParams)
    }

    private fun makeDraggable(v: View, p: WindowManager.LayoutParams, root: FrameLayout) {
        v.setOnTouchListener(object : View.OnTouchListener {
            var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { ix=p.x; iy=p.y; tx=e.rawX; ty=e.rawY; return true }
                    MotionEvent.ACTION_MOVE -> {
                        p.x = ix + (e.rawX-tx).toInt()
                        p.y = iy + (e.rawY-ty).toInt()
                        wm.updateViewLayout(root, p); return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (Math.abs(e.rawX-tx)<10 && Math.abs(e.rawY-ty)<10) view.performClick()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun injectLib(): Boolean {
        val lib = File(applicationInfo.nativeLibraryDir, "libStarcool.so").absolutePath
        return ShellUtils.deployAndRunInjector(this, target, lib)
    }

    private fun startForeground() {
        val ch = "hok_ch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(ch, "HOK Mod", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, ch)
            .setContentTitle("HOK Map Hack")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoInject)
        try { container?.let { wm.removeView(it) } } catch (_: Exception) {}
    }
}