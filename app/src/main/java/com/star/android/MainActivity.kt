package com.star.android

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.star.android.ui.theme.StarAndroidInjectTheme

class MainActivity : ComponentActivity() {

    companion object {
        init { System.loadLibrary("Starcool") }

        private const val REQUEST_CODE_WIFI_PERMISSION = 1001
        private const val REQUEST_CODE_STORAGE_PERMISSION = 1002
        private const val REQUEST_CODE_LOCATION_PERMISSION = 1003
        private const val REQUEST_CODE_OVERLAY_PERMISSION = 1234
    }

    // Native method được gọi để hiện dialog từ native code
    external fun showNativeAlertDialog()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hiển thị dialog từ native code
        showNativeAlertDialog()

        // Kiểm tra permissions
        checkPermissions()

        // Kiểm tra quyền overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ),
                REQUEST_CODE_OVERLAY_PERMISSION
            )
        }

        setContent {
            StarAndroidInjectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onStartMenu = {
                            if (checkOverlayPermission()) {
                                Initialize(this@MainActivity)
                            }
                        }
                    )
                }
            }
        }
    }

    // Được gọi từ C++ để hiển thị dialog tùy chỉnh
    fun displayNativeDialog(
        title: String,
        message: String,
        telegramLink: String,
        positiveBtn: String,
        negativeBtn: String,
        borderColor: String,
        bgColor: String,
        bgAlpha: Float
    ) {
        runOnUiThread {
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setCancelable(false)
            builder.setTitle(title)
            builder.setMessage(Html.fromHtml(message))

            builder.setPositiveButton(positiveBtn) { dialog, _ -> dialog.dismiss() }

            if (negativeBtn.isNotEmpty() && telegramLink.isNotEmpty()) {
                builder.setNegativeButton(negativeBtn) { dialog, _ ->
                    openTelegramGroup(telegramLink)
                    dialog.dismiss()
                }
            }

            val dialog = builder.create()
            dialog.show()

            val window: Window? = dialog.window
            if (window != null) {
                val layoutParams = window.attributes
                val screenWidth = resources.displayMetrics.widthPixels
                layoutParams.width = (screenWidth * 0.80).toInt()
                layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams.gravity = Gravity.CENTER
                window.attributes = layoutParams
            }

            val messageText = dialog.findViewById<TextView>(android.R.id.message)
            if (messageText != null) {
                messageText.movementMethod = LinkMovementMethod.getInstance()
                messageText.textSize = 14f
            }

            styleDialog(dialog, borderColor, bgColor, bgAlpha)
        }
    }

    private fun openTelegramGroup(telegramLink: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(telegramLink)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun styleDialog(dialog: AlertDialog, borderColorHex: String, bgColorHex: String, bgAlpha: Float) {
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = dpToPx(12).toFloat()
        bg.setStroke(dpToPx(2), Color.parseColor(borderColorHex))
        bg.setColor(adjustAlpha(Color.parseColor(bgColorHex), bgAlpha))
        dialog.window?.setBackgroundDrawable(bg)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_CODE_LOCATION_PERMISSION
            )
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_STORAGE_PERMISSION
            )
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_WIFI_STATE),
                REQUEST_CODE_WIFI_PERMISSION
            )
        }
    }

    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ),
                REQUEST_CODE_OVERLAY_PERMISSION
            )
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                Initialize(this@MainActivity)
            }
        }
    }
}

@Composable
fun MainScreen(onStartMenu: () -> Unit) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            ComposeColor(0xFF0F2027),
            ComposeColor(0xFF203A43),
            ComposeColor(0xFF2C5364)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ComposeColor(0xCC1E2A38)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "★",
                        fontSize = 80.sp,
                        color = ComposeColor(0xFFFFD700),
                        modifier = Modifier.padding(8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "STAR MENU INJECTOR",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = ComposeColor.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Floating Control Panel",
                        fontSize = 16.sp,
                        color = ComposeColor(0xFFB0BEC5),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = onStartMenu,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeColor.Transparent
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(ComposeColor(0xFFFF8C00), ComposeColor(0xFFFFD700))
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "LAUNCH STAR MENU",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ComposeColor.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Tap to start floating menu & open game",
                        fontSize = 12.sp,
                        color = ComposeColor(0xFF90A4AE),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
