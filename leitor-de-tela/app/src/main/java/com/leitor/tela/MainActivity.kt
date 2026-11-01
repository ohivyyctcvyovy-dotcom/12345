package com.leitor.tela

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val captureRequest = 701
    private val overlayRequest = 702

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(Color.rgb(16, 16, 16))
        }
        val title = TextView(this).apply {
            text = "LEITOR DE TELA"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(222, 183, 100))
        }
        val info = TextView(this).apply {
            text = "Ative o botão flutuante. Quando encontrar um texto na tela, toque em LER para reconhecer e ouvir em português."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 24)
        }
        val start = Button(this).apply {
            text = "ATIVAR LEITOR"
            setOnClickListener { requestOverlay() }
        }
        val stop = Button(this).apply {
            text = "PARAR LEITOR"
            setOnClickListener { stopService(Intent(this@MainActivity, CaptureService::class.java)) }
        }
        root.addView(title)
        root.addView(info)
        root.addView(start)
        root.addView(stop)
        setContentView(root)
    }

    private fun requestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(i, overlayRequest)
        } else requestCapture()
    }

    private fun requestCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Compatibility with Android activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == overlayRequest) {
            if (Settings.canDrawOverlays(this)) requestCapture()
            return
        }
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        }
    }
}
