package com.star.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.star.android.utils.ShellUtils
import com.star.android.utils.SocketClient
import com.star.android.utils.SocketClient.ESPHero
import java.io.File

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var menuView: LinearLayout
    private lateinit var collapsedView: ImageView
    private lateinit var container: LinearLayout
    private lateinit var params: WindowManager.LayoutParams

    // ESP overlay — full-screen canvas, FLAG_NOT_TOUCHABLE
    private lateinit var espView: ESPOverlayView
    private lateinit var espParams: WindowManager.LayoutParams

    private val targetPackage = "com.garena.game.kgvn"
    private var isInjected = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    @Volatile private var espRunning = false
    private var espThread: Thread? = null

    private var screenW = 1080f
    private var screenH = 2400f

    // ── Auto-inject (giống gốc) ───────────────────────────────────────────
    private val autoInjectRunnable = object : Runnable {
        override fun run() {
            Thread {
                val isRunning = ShellUtils.runAsRoot("pidof $targetPackage")
                handler.post {
                    if (isRunning) {
                        if (!isInjected) {
                            if (injectLibrary()) {
                                isInjected = true
                                Toast.makeText(this@FloatingService,
                                    "Injection Successful!", Toast.LENGTH_SHORT).show()
                                // Gửi kích thước màn hình cho C++
                                Thread {
                                    SocketClient.sendScreenSize(
                                        screenW.toInt(), screenH.toInt())
                                }.start()
                            }
                        }
                    } else {
                        isInjected = false
                    }
                }
            }.start()
            handler.postDelayed(this, 1000)
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════════════
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Foreground service (giống gốc)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, createNotification())
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        screenW = dm.widthPixels.toFloat()
        screenH = dm.heightPixels.toFloat()

        setupESPOverlay()      // thêm mới — luôn add trước menu
        setupFloatingMenu()    // giống gốc
        handler.post(autoInjectRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoInjectRunnable)
        stopEspLoop()
        if (::espView.isInitialized)    try { windowManager.removeView(espView) } catch (_: Exception) {}
        if (::container.isInitialized)  windowManager.removeView(container)
    }

    // ════════════════════════════════════════════════════════════════
    //  NOTIFICATION (giống gốc)
    // ════════════════════════════════════════════════════════════════
    private fun createNotification(): Notification {
        val channelId = "star_menu_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId,
                "Star Menu Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Star Menu Active")
            .setContentText("Menu is running on top of game.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
    }

    // ════════════════════════════════════════════════════════════════
    //  ESP OVERLAY SETUP (thêm mới)
    //  Full-screen, FLAG_NOT_TOUCHABLE → game vẫn nhận input bình thường
    // ════════════════════════════════════════════════════════════════
    private fun setupESPOverlay() {
        espView = ESPOverlayView(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY

        espParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(espView, espParams)
    }

    // ════════════════════════════════════════════════════════════════
    //  FLOATING MENU SETUP (giữ nguyên cấu trúc gốc, thêm ESP checkbox)
    // ════════════════════════════════════════════════════════════════
    private fun setupFloatingMenu() {
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100; y = 100
            width = WindowManager.LayoutParams.WRAP_CONTENT
        }

        // Collapsed icon (giống gốc)
        collapsedView = ImageView(this).apply {
            setImageResource(com.star.android.R.drawable.logo)
            layoutParams = LinearLayout.LayoutParams(120, 120)
            setOnClickListener {
                menuView.visibility = View.VISIBLE
                collapsedView.visibility = View.GONE
                windowManager.updateViewLayout(container, params)
            }
            visibility = View.VISIBLE
            makeDraggable(this)
        }

        // Menu layout (giống gốc + thêm ESP)
        menuView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#DD000000"))
            setPadding(50, 50, 50, 50)
            visibility = View.GONE

            // Header (giống gốc)
            addView(TextView(context).apply {
                text = "⚡ Fang Yuan Mod ⚡"
                setTextColor(Color.YELLOW)
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 40)
                makeDraggable(this)
            })

            // Map Hack (giống gốc)
            addView(CheckBox(context).apply {
                text = "🔍 Map Hack"
                setTextColor(Color.WHITE)
                textSize = 18f
                setPadding(20, 10, 0, 10)
                setOnCheckedChangeListener { _, isChecked ->
                    SocketClient.setMapHack(isChecked)
                }
            })

            // Cam Xa (giống gốc)
            addView(TextView(context).apply {
                text = "📷 Cam Xa (0 → 10)"
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(0, 20, 0, 0)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 5, 0, 15)
                val label = TextView(context).apply {
                    text = "0.0"
                    setTextColor(Color.CYAN)
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(100,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val seekBar = SeekBar(context).apply {
                    max = 100
                    progress = 0
                    layoutParams = LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                            val v = p / 10f
                            label.text = String.format("%.1f", v)
                            SocketClient.setCamXa(v)
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                }
                addView(label)
                addView(seekBar)
            })

            // ── ESP CheckBox (thêm mới, cùng style Map Hack) ─────────
            addView(CheckBox(context).apply {
                text = "👁 ESP Heroes"
                setTextColor(Color.WHITE)
                textSize = 18f
                setPadding(20, 10, 0, 10)
                setOnCheckedChangeListener { _, isChecked ->
                    SocketClient.setEspEnable(isChecked)
                    if (isChecked) startEspLoop() else stopEspLoop()
                }
            })

            // Hide (giống gốc)
            addView(Button(context).apply {
                text = "⬅ Hide Menu"
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.LTGRAY)
                setPadding(20, 20, 20, 20)
                setOnClickListener {
                    menuView.visibility = View.GONE
                    collapsedView.visibility = View.VISIBLE
                    windowManager.updateViewLayout(container, params)
                }
            })

            // Exit (giống gốc)
            addView(Button(context).apply {
                text = "❌ Exit"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.RED)
                setPadding(20, 20, 20, 20)
                setOnClickListener { stopSelf() }
            })
        }

        container = LinearLayout(this).apply {
            addView(collapsedView)
            addView(menuView)
        }
        windowManager.addView(container, params)
    }

    // ── makeDraggable (giống gốc 1:1) ────────────────────────────────────
    private fun makeDraggable(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(container, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = Math.abs(event.rawX - initialTouchX)
                        val dy = Math.abs(event.rawY - initialTouchY)
                        if (dx < 10 && dy < 10) v.performClick()
                        return true
                    }
                }
                return false
            }
        })
    }

    // ── injectLibrary (giống gốc) ─────────────────────────────────────────
    private fun injectLibrary(): Boolean {
        val libPath = File(applicationInfo.nativeLibraryDir, "libStarcool.so").absolutePath
        return ShellUtils.deployAndRunInjector(this, targetPackage, libPath)
    }

    // ════════════════════════════════════════════════════════════════
    //  ESP POLL LOOP
    // ════════════════════════════════════════════════════════════════
    private fun startEspLoop() {
        if (espRunning) return
        espRunning = true
        espThread = Thread {
            while (espRunning) {
                val raw    = SocketClient.getEspData()
                val heroes = SocketClient.parseEspData(raw)
                espView.updateHeroes(heroes)
                Thread.sleep(50)
            }
            espView.updateHeroes(emptyArray())
        }.apply { isDaemon = true; start() }
    }

    private fun stopEspLoop() {
        espRunning = false
        espThread?.interrupt()
        espThread = null
        if (::espView.isInitialized) espView.updateHeroes(emptyArray())
    }

    // ════════════════════════════════════════════════════════════════
    //  ESP CANVAS VIEW
    // ════════════════════════════════════════════════════════════════
    inner class ESPOverlayView(context: Context) : View(context) {

        @Volatile private var heroes: Array<ESPHero> = emptyArray()

        private val pBoxEnemy = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 3f }
        private val pBoxAlly = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 2f }
        private val pHpBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 20, 20, 20); style = Paint.Style.FILL }
        private val pHpFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL }
        private val pTextEnemy = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED; textSize = 28f; typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(3f, 1f, 1f, Color.BLACK) }
        private val pTextAlly = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN; textSize = 26f; typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(3f, 1f, 1f, Color.BLACK) }
        private val pInfo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW; textSize = 22f
            setShadowLayer(2f, 1f, 1f, Color.BLACK) }
        private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 50, 50)
            style = Paint.Style.STROKE; strokeWidth = 1.5f }

        fun updateHeroes(arr: Array<ESPHero>) {
            heroes = arr; postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val list = heroes
            if (list.isEmpty()) return

            for (h in list) {
                val sx = h.screenX; val sy = h.screenY
                if (sx < -300 || sx > screenW + 300
                 || sy < -300 || sy > screenH + 300) continue

                // Box scale theo world dist
                val dist  = Math.sqrt((h.worldX * h.worldX + h.worldZ * h.worldZ).toDouble()).toFloat()
                val scale = (screenH * 0.08f / (dist * 0.08f + 1f)).coerceIn(25f, 110f)
                val bw = scale * 0.55f; val bh = scale
                val l = sx - bw/2; val r = sx + bw/2
                val t = sy - bh;   val b = sy

                val pBox  = if (h.isEnemy) pBoxEnemy  else pBoxAlly
                val pText = if (h.isEnemy) pTextEnemy else pTextAlly

                // Box
                canvas.drawRect(l, t, r, b, pBox)

                // HP bar
                val barT = t - 12f; val barB = t - 4f
                val hp   = h.hpPercent()
                canvas.drawRect(l, barT, r, barB, pHpBg)
                pHpFill.color = hpColor(hp)
                canvas.drawRect(l, barT, l + bw * hp, barB, pHpFill)

                // Tên
                val tw = pText.measureText(h.name)
                canvas.drawText(h.name, sx - tw/2, barT - 4f, pText)

                // HP + dist
                val infoStr = "${h.hp}/${h.maxHp}  ${dist.toInt()}m"
                val iw = pInfo.measureText(infoStr)
                canvas.drawText(infoStr, sx - iw/2, b + 24f, pInfo)

                // Snap-line chỉ với enemy
                if (h.isEnemy) canvas.drawLine(screenW/2, screenH, sx, sy, pLine)
            }
        }

        private fun hpColor(r: Float) = when {
            r > 0.6f -> Color.rgb(((1-r)/0.4f*255).toInt(), 255, 0)
            r > 0.3f -> Color.rgb(255, ((r-0.3f)/0.3f*255).toInt(), 0)
            else     -> Color.RED
        }
    }
}
