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

    // ── External ESP function ─────────────────────────────────────
    external fun GetEspActors(screenW: Int, screenH: Int): FloatArray

    companion object {
        init { System.loadLibrary("Starcool") }
    }

    private lateinit var wm: WindowManager
    private lateinit var container: FrameLayout
    private var menuView: LinearLayout? = null
    private var btnCollapse: ImageView? = null
    private var espOverlay: EspOverlayView? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val target = "com.garena.game.kgvn"
    private var injected = false

    // ── ESP overlay view ─────────────────────────────────────────
    inner class EspOverlayView(ctx: Context) : View(ctx) {
        private val paintRed   = Paint().apply { color = Color.RED;    style = Paint.Style.STROKE; strokeWidth = 3f }
        private val paintOrange= Paint().apply { color = Color.parseColor("#FF8C00"); style = Paint.Style.STROKE; strokeWidth = 3f }
        private val paintText  = Paint().apply { color = Color.YELLOW; textSize = 28f; isFakeBoldText = true }
        private val paintHpBg  = Paint().apply { color = Color.BLACK;  style = Paint.Style.FILL }
        private val paintHpFg  = Paint().apply { color = Color.GREEN;  style = Paint.Style.FILL }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!espEnabled) { postInvalidateOnAnimation(); return }
            try {
                val data = GetEspActors(width, height)
                if (data.isEmpty()) { postInvalidateOnAnimation(); return }
                val count = data[0].toInt()
                for (i in 0 until count) {
                    val base = 1 + i * 5
                    if (base + 4 >= data.size) break
                    val sx   = data[base + 1]
                    val sy   = data[base + 2]
                    val hp   = data[base + 3]
                    val mhp  = data[base + 4].takeIf { it > 0f } ?: 1f
                    val paint = paintRed

                    // Box
                    val bw = 90f; val bh = 160f
                    canvas.drawRect(sx - bw/2, sy - bh, sx + bw/2, sy, paint)

                    // HP bar background
                    canvas.drawRect(sx - bw/2 - 12, sy - bh, sx - bw/2 - 6, sy, paintHpBg)
                    val hpH = bh * (hp / mhp)
                    paintHpFg.color = when {
                        hp / mhp < 0.3f -> Color.RED
                        hp / mhp < 0.6f -> Color.YELLOW
                        else -> Color.GREEN
                    }
                    canvas.drawRect(sx - bw/2 - 12, sy - hpH, sx - bw/2 - 6, sy, paintHpFg)

                    // HP text
                    val hpPct = "${(hp / mhp * 100).toInt()}%"
                    canvas.drawText(hpPct, sx - 20f, sy - bh - 8f, paintText)
                }
            } catch (_: Exception) {}
            postInvalidateOnAnimation()
        }
    }

    private var scrollMenu: ScrollView? = null
    private var espEnabled = false

    // ── Window params helper ─────────────────────────────────────
    private fun makeParams(w: Int = WindowManager.LayoutParams.WRAP_CONTENT,
                           h: Int = WindowManager.LayoutParams.WRAP_CONTENT,
                           flags: Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                           fmt: Int = PixelFormat.TRANSLUCENT): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(w, h, type, flags, fmt).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    override fun onBind(i: android.content.Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // ── ESP transparent full-screen overlay ───────────────────
        espOverlay = EspOverlayView(this)
        val espParams = makeParams(
            w = WindowManager.LayoutParams.MATCH_PARENT,
            h = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            fmt = PixelFormat.TRANSPARENT
        )
        wm.addView(espOverlay, espParams)
        espOverlay!!.postInvalidateOnAnimation()

        // ── Floating menu ─────────────────────────────────────────
        setupMenu()

        // ── Auto inject ───────────────────────────────────────────
        handler.post(autoInject)
    }

    private val autoInject = object : Runnable {
        override fun run() {
            Thread {
                val running = ShellUtils.runAsRoot("pidof $target")
                handler.post {
                    if (running) {
                        if (!injected) {
                            if (injectLib()) { injected = true
                                Toast.makeText(this@FloatingService, "Injected!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else injected = false
                }
            }.start()
            handler.postDelayed(this, 1000)
        }
    }

    private fun setupMenu() {
        // ── Menu params: draggable, NOT TOUCH-MODAL ───────────────
        val menuParams = makeParams(
            w = 640,
            h = WindowManager.LayoutParams.WRAP_CONTENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        menuParams.x = 50; menuParams.y = 100

        container = FrameLayout(this)

        // ── Collapsed button ──────────────────────────────────────
        val btnIcon = ImageView(this).apply {
            setImageResource(com.star.android.R.drawable.logo)
            layoutParams = FrameLayout.LayoutParams(120, 120)
            setOnClickListener {
                visibility = View.GONE
                scrollMenu?.visibility = View.VISIBLE
                wm.updateViewLayout(container, menuParams)
            }
            makeDraggable(this, menuParams)
        }
        btnCollapse = btnIcon

        // ── Scrollable menu ───────────────────────────────────────
        val scrollView = ScrollView(this).apply {
            visibility = View.GONE
            // saved for click handlers
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE000000"))
            setPadding(30, 30, 30, 30)

            // ── Drag handle / header ──────────────────────────────
            addView(TextView(context).apply {
                text = "⚡ Fang Yuan Mod ⚡"
                setTextColor(Color.YELLOW); textSize = 18f; gravity = Gravity.CENTER
                setPadding(0, 0, 0, 20)
                makeDraggable(this, menuParams)
            })

            // ── Map Hack ──────────────────────────────────────────
            addView(sectionHeader("📡 Map Hack"))
            addView(CheckBox(context).apply {
                text = "🔍 Map Hack (Nhìn full map)"
                styleCheck(); setOnCheckedChangeListener { _, c -> SocketClient.setMapHack(c) }
            })

            // ── Camera Zoom ───────────────────────────────────────
            addView(sectionHeader("📷 Camera Zoom"))
            val camLabel = TextView(context).apply { text = "0.0"; setTextColor(Color.CYAN); textSize = 14f }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(10,4,0,10)
                addView(camLabel.apply { layoutParams = LinearLayout.LayoutParams(60, LinearLayout.LayoutParams.WRAP_CONTENT) })
                addView(SeekBar(context).apply {
                    max = 100; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                            val v = p / 10f; camLabel.text = "%.1f".format(v); SocketClient.setCamXa(v)
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                })
            })

            // ── Skin unlock ───────────────────────────────────────
            addView(sectionHeader("🎨 Mở Khóa Skin"))
            addView(TextView(context).apply {
                text = "Bật để dùng mọi skin đã sở hữu\n(chọn skin tại màn hình chọn tướng)"
                setTextColor(Color.LTGRAY); textSize = 13f; setPadding(10,0,0,6)
            })
            addView(CheckBox(context).apply {
                text = "✅ Mở khóa tất cả skin"
                styleCheck()
                setOnCheckedChangeListener { _, c -> SocketClient.setSkinUnlock(c) }
            })

            // ── ESP ───────────────────────────────────────────────
            addView(sectionHeader("🎯 ESP"))
            addView(CheckBox(context).apply {
                text = "📦 Vẽ box địch (ESP)"
                styleCheck()
                setOnCheckedChangeListener { _, c ->
                    espEnabled = c
                    SocketClient.setESP(c)
                }
            })
            addView(CheckBox(context).apply {
                text = "🔍 Map Hack (kết hợp ESP)"
                styleCheck()
                setOnCheckedChangeListener { _, c -> SocketClient.setMapHack(c) }
            })

            // ── Buttons ───────────────────────────────────────────
            addView(divider())
            addView(Button(context).apply {
                text = "⬅ Thu gọn"; setTextColor(Color.BLACK); setBackgroundColor(Color.LTGRAY)
                setPadding(20,16,20,16)
                setOnClickListener {
                    scrollMenu?.visibility = View.GONE
                    btnIcon.visibility     = View.VISIBLE
                    wm.updateViewLayout(container, menuParams)
                }
            })
            addView(Button(context).apply {
                text = "❌ Thoát"; setTextColor(Color.WHITE); setBackgroundColor(Color.RED)
                setPadding(20,16,20,16); setOnClickListener { stopSelf() }
            })
        }
        menuView = menu
        scrollMenu = scrollView  // save reference
        scrollView.addView(menu)

        container.addView(btnIcon)
        container.addView(scrollView)
        wm.addView(container, menuParams)
    }

    // ── Helpers ───────────────────────────────────────────────────
    private fun CheckBox.styleCheck() { setTextColor(Color.WHITE); textSize = 15f; setPadding(10,6,0,6) }

    private fun sectionHeader(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#FFD700")); textSize = 14f; setPadding(0,14,0,4)
    }
    private fun divider() = View(this).apply {
        setBackgroundColor(Color.parseColor("#44FFFFFF"))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(0,12,0,12) }
    }

    private fun makeDraggable(v: View, p: WindowManager.LayoutParams) {
        v.setOnTouchListener(object: View.OnTouchListener {
            var ix=0; var iy=0; var tx=0f; var ty=0f
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { ix=p.x; iy=p.y; tx=e.rawX; ty=e.rawY; return true }
                    MotionEvent.ACTION_MOVE -> {
                        p.x = ix + (e.rawX - tx).toInt()
                        p.y = iy + (e.rawY - ty).toInt()
                        wm.updateViewLayout(container, p); return true
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
        val ch = "star_ch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(ch, "Star", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, ch)
            .setContentTitle("Fang Yuan Mod")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoInject)
        try { if (::container.isInitialized) wm.removeView(container) } catch (_: Exception) {}
        try { espOverlay?.let { wm.removeView(it) } } catch (_: Exception) {}
    }
}
