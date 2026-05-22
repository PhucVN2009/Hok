package com.star.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import com.star.android.utils.ShellUtils
import com.star.android.utils.SocketClient
import java.io.File

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var menuView: LinearLayout
    private lateinit var collapsedView: ImageView
    private lateinit var container: LinearLayout
    private lateinit var params: WindowManager.LayoutParams
    private val targetPackage = "com.garena.game.kgvn"
    private var isInjected = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // ── State ─────────────────────────────────────────────────────
    private var skinHeroId = 0
    private var skinSkinId = 1
    private var skinEnabled = false

    // ── Auto inject ───────────────────────────────────────────────
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, createNotification())
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupFloatingMenu()
        handler.post(autoInjectRunnable)
    }

    private fun createNotification(): Notification {
        val channelId = "star_menu_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId,
                "Star Menu Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Star Menu Active")
            .setContentText("Menu is running on top of game.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
    }

    private fun setupFloatingMenu() {
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100; y = 100
            width = WindowManager.LayoutParams.WRAP_CONTENT
        }

        // ── Collapsed icon ────────────────────────────────────────
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

        // ── Menu ──────────────────────────────────────────────────
        menuView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#DD000000"))
            setPadding(50, 50, 50, 50)
            visibility = View.GONE

            // Header (draggable)
            addView(TextView(context).apply {
                text = "⚡ Fang Yuan Mod ⚡"
                setTextColor(Color.YELLOW)
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 30)
                makeDraggable(this)
            })

            // ════════════════════════════════════════
            // MAP HACK
            // ════════════════════════════════════════
            addView(sectionHeader("📡 Map Hack / ESP"))
            addView(CheckBox(context).apply {
                text = "🔍 Map Hack (Nhìn full map)"
                setTextColor(Color.WHITE); textSize = 16f
                setPadding(20, 8, 0, 8)
                setOnCheckedChangeListener { _, c ->
                    SocketClient.setMapHack(c)
                    // ESP theo map hack
                    SocketClient.setESP(c)
                }
            })

            // ════════════════════════════════════════
            // CAM XA
            // ════════════════════════════════════════
            addView(sectionHeader("📷 Camera Zoom"))
            addView(TextView(context).apply {
                text = "Cam Xa (0 → 10)"; setTextColor(Color.WHITE)
                textSize = 14f; setPadding(20, 4, 0, 0)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 4, 0, 12)
                val label = TextView(context).apply {
                    text = "0.0"; setTextColor(Color.CYAN); textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(80, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val seekBar = SeekBar(context).apply {
                    max = 100; progress = 0
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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
                addView(label); addView(seekBar)
            })

            // ════════════════════════════════════════
            // MOD SKIN
            // ════════════════════════════════════════
            addView(sectionHeader("🎨 Mod Skin"))

            // Hero ID input
            addView(TextView(context).apply {
                text = "Hero ID (vd: 105=Toro, 116=Butterfly):"
                setTextColor(Color.WHITE); textSize = 13f; setPadding(20, 4, 0, 2)
            })
            val etHeroId = EditText(context).apply {
                hint = "Hero ID (số)"; setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setBackgroundColor(Color.parseColor("#44FFFFFF"))
                inputType = InputType.TYPE_CLASS_NUMBER
                textSize = 14f; setPadding(20, 8, 20, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(20, 0, 20, 6)
                }
                setText("0")
            }
            addView(etHeroId)

            // Skin ID input
            addView(TextView(context).apply {
                text = "Skin ID (vd: 1=Mặc định, 2=Skin 2...):"
                setTextColor(Color.WHITE); textSize = 13f; setPadding(20, 4, 0, 2)
            })
            val etSkinId = EditText(context).apply {
                hint = "Skin ID (số)"; setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setBackgroundColor(Color.parseColor("#44FFFFFF"))
                inputType = InputType.TYPE_CLASS_NUMBER
                textSize = 14f; setPadding(20, 8, 20, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(20, 0, 20, 6)
                }
                setText("1")
            }
            addView(etSkinId)

            // Enable toggle
            val skinLabel = TextView(context).apply {
                text = "Skin: TẮT"
                setTextColor(Color.RED); textSize = 14f
                setPadding(20, 4, 0, 4)
            }
            addView(CheckBox(context).apply {
                text = "✅ Bật Mod Skin"
                setTextColor(Color.WHITE); textSize = 16f
                setPadding(20, 8, 0, 4)
                setOnCheckedChangeListener { _, isChecked ->
                    skinEnabled = isChecked
                    try {
                        skinHeroId = etHeroId.text.toString().toInt()
                        skinSkinId = etSkinId.text.toString().toInt()
                    } catch (_: Exception) {}
                    SocketClient.setModSkin(isChecked, skinHeroId, skinSkinId)
                    skinLabel.text  = if (isChecked)
                        "Skin: BẬT (Hero=$skinHeroId Skin=$skinSkinId)"
                    else "Skin: TẮT"
                    skinLabel.setTextColor(if (isChecked) Color.GREEN else Color.RED)
                }
            })
            addView(skinLabel)

            // Apply button (áp dụng khi thay đổi ID)
            addView(Button(context).apply {
                text = "🔄 Áp dụng Hero/Skin ID"
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.parseColor("#FF9800"))
                setPadding(20, 12, 20, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(20, 4, 20, 12)
                }
                setOnClickListener {
                    try {
                        skinHeroId = etHeroId.text.toString().toInt()
                        skinSkinId = etSkinId.text.toString().toInt()
                    } catch (_: Exception) {
                        Toast.makeText(context, "Nhập số hợp lệ!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    SocketClient.setModSkin(skinEnabled, skinHeroId, skinSkinId)
                    skinLabel.text = if (skinEnabled)
                        "Skin: BẬT (Hero=$skinHeroId Skin=$skinSkinId)"
                    else "Skin: TẮT"
                    Toast.makeText(context, "Đã áp dụng: Hero=$skinHeroId Skin=$skinSkinId",
                        Toast.LENGTH_SHORT).show()
                }
            })

            // ════════════════════════════════════════
            // BUTTONS
            // ════════════════════════════════════════
            addView(divider())
            addView(Button(context).apply {
                text = "⬅ Thu menu"
                setTextColor(Color.BLACK); setBackgroundColor(Color.LTGRAY)
                setPadding(20, 16, 20, 16)
                setOnClickListener {
                    menuView.visibility = View.GONE
                    collapsedView.visibility = View.VISIBLE
                    windowManager.updateViewLayout(container, params)
                }
            })
            addView(Button(context).apply {
                text = "❌ Thoát"
                setTextColor(Color.WHITE); setBackgroundColor(Color.RED)
                setPadding(20, 16, 20, 16)
                setOnClickListener { stopSelf() }
            })
        }

        container = LinearLayout(this).apply {
            addView(collapsedView)
            addView(menuView)
        }
        windowManager.addView(container, params)
    }

    // ── Helpers ───────────────────────────────────────────────────
    private fun sectionHeader(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#FFD700"))
        textSize = 15f
        setPadding(0, 16, 0, 4)
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(Color.parseColor("#44FFFFFF"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(0, 12, 0, 12) }
    }

    private fun makeDraggable(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var ix = 0; private var iy = 0
            private var tx = 0f; private var ty = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        ix = params.x; iy = params.y
                        tx = e.rawX;  ty = e.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = ix + (e.rawX - tx).toInt()
                        params.y = iy + (e.rawY - ty).toInt()
                        windowManager.updateViewLayout(container, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (Math.abs(e.rawX-tx)<10 && Math.abs(e.rawY-ty)<10) v.performClick()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun injectLibrary(): Boolean {
        val libPath = File(applicationInfo.nativeLibraryDir, "libStarcool.so").absolutePath
        return ShellUtils.deployAndRunInjector(this, targetPackage, libPath)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoInjectRunnable)
        if (::container.isInitialized) windowManager.removeView(container)
    }
}
