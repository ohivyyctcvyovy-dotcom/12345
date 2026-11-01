package com.leitor.tela

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

class CaptureService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL = "screen_reader"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_STOP = "com.leitor.tela.STOP"
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var floatingButton: Button? = null
    private var resultWindow: LinearLayout? = null
    private var selectionWindow: FrameLayout? = null
    private var selectedArea: RectF? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRate = 1.0f

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIFICATION_ID, notification())
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(code, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, handler)
            prepareScreenCapture()
            showFloatingButton()
        } catch (_: Exception) { stopSelf() }
        return START_NOT_STICKY
    }

    private fun prepareScreenCapture() {
        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 3)
        display = projection?.createVirtualDisplay(
            "LeitorDeTela", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, handler
        )
    }

    private fun showFloatingButton() {
        val button = Button(this).apply {
            text = "LER"
            textSize = 13f
            setOnClickListener {
                this.visibility = View.INVISIBLE
                showSelectionOverlay()
            }
        }
        floatingButton = button
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 18; y = 220 }
        wm.addView(button, params)
    }

    private fun showSelectionOverlay() {
        removeSelectionOverlay()
        val frame = FrameLayout(this)
        val selector = SelectionView(this) { area ->
            selectedArea = area
            removeSelectionOverlay()
            handler.postDelayed({ captureOnce() }, 300)
        }
        frame.addView(selector, FrameLayout.LayoutParams(-1, -1))
        selectionWindow = frame
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(frame, params)
        } catch (_: Exception) {
            selectionWindow = null
            floatingButton?.visibility = View.VISIBLE
            showResult("Não consegui abrir a seleção. Confira a permissão de aparecer sobre outros aplicativos.", false)
        }
    }

    private fun removeSelectionOverlay() {
        selectionWindow?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        selectionWindow = null
    }

    private inner class SelectionView(context: Context, val onSelected: (RectF) -> Unit) : View(context) {
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFD166.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFD166
            style = Paint.Style.FILL
        }
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16f * resources.displayMetrics.density
        }
        private val hintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
        private var startX = 0f
        private var startY = 0f
        private var currentX = 0f
        private var currentY = 0f
        private var dragging = false

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            val label = "Arraste para marcar a área • toque duas vezes para cancelar"
            val labelWidth = hintPaint.measureText(label)
            val box = RectF(12f * density, 48f * density, labelWidth + 32f * density, 82f * density)
            canvas.drawRoundRect(box, 10f * density, 10f * density, hintBg)
            canvas.drawText(label, 22f * density, 70f * density, hintPaint)
            if (dragging) {
                val rect = RectF(startX, startY, currentX, currentY).apply { sort() }
                canvas.drawRoundRect(rect, 5f * density, 5f * density, fill)
                canvas.drawRoundRect(rect, 5f * density, 5f * density, outline)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x; startY = event.y
                    currentX = event.x; currentY = event.y
                    dragging = true
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentX = event.x; currentY = event.y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    currentX = event.x; currentY = event.y
                    dragging = false
                    val rect = RectF(startX, startY, currentX, currentY).apply { sort() }
                    if (rect.width() >= 20f && rect.height() >= 20f) {
                        onSelected(rect)
                    } else {
                        removeSelectionOverlay()
                        floatingButton?.visibility = View.VISIBLE
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    invalidate()
                    return true
            }
            return true
        }
    }

    private fun captureOnce(attempt: Int = 0) {
        val image = try { imageReader?.acquireLatestImage() } catch (_: Exception) { null }
        if (image == null) {
            if (attempt < 10) {
                handler.postDelayed({ captureOnce(attempt + 1) }, 180)
            } else {
                showResult("Não consegui obter uma imagem da tela. Tente novamente e confira se autorizou o compartilhamento da tela.", false)
            }
            return
        }

        var bitmap: Bitmap? = null
        try {
            val plane = image.planes.firstOrNull()
                ?: throw IllegalStateException("A captura não contém uma camada de imagem")
            val buffer = plane.buffer.duplicate()
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val offset = y * rowStride + x * pixelStride
                    val red = buffer.get(offset).toInt() and 0xFF
                    val green = buffer.get(offset + 1).toInt() and 0xFF
                    val blue = buffer.get(offset + 2).toInt() and 0xFF
                    val alpha = buffer.get(offset + 3).toInt() and 0xFF
                    pixels[y * width + x] = Color.argb(alpha, red, green, blue)
                }
            }

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        } catch (_: Exception) {
            showResult("Não foi possível preparar a imagem capturada. Tente novamente.", false)
        } finally {
            try { image.close() } catch (_: Exception) {}
        }

        val capturedBitmap = bitmap ?: return
        val area = selectedArea
        selectedArea = null
        val bitmapForOcr = if (area != null) {
            val sx = capturedBitmap.width.toFloat() / resources.displayMetrics.widthPixels.toFloat()
            val sy = capturedBitmap.height.toFloat() / resources.displayMetrics.heightPixels.toFloat()
            val left = (area.left * sx).toInt().coerceIn(0, capturedBitmap.width - 1)
            val top = (area.top * sy).toInt().coerceIn(0, capturedBitmap.height - 1)
            val right = (area.right * sx).toInt().coerceIn(left + 1, capturedBitmap.width)
            val bottom = (area.bottom * sy).toInt().coerceIn(top + 1, capturedBitmap.height)
            Bitmap.createBitmap(capturedBitmap, left, top, right - left, bottom - top).also {
                if (it !== capturedBitmap) bitmapForOcr.recycle()
            }
        } else capturedBitmap
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(bitmapForOcr, 0))
                .addOnSuccessListener { result ->
                    val recognizedText = result.text.trim()
                    if (recognizedText.isBlank()) showResult("Não encontrei texto nesta tela.", false)
                    else showResult(recognizedText, true)
                }
                .addOnFailureListener {
                    showResult("Não foi possível reconhecer o texto. Tente outra captura.", false)
                }
                .addOnCompleteListener {
                    capturedBitmap.recycle()
                    recognizer.close()
                }
        } catch (_: Exception) {
            if (!bitmapForOcr.isRecycled) bitmapForOcr.recycle()
            showResult("Ocorreu um erro ao reconhecer a imagem. Tente novamente.", false)
        }
    }

    private fun showResult(text: String, autoSpeak: Boolean) {
        handler.post {
            removeResult()
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                setBackgroundColor(0x66000000.toInt())
            }
            val heading = TextView(this).apply {
                this.text = "TEXTO RECONHECIDO"
                textSize = 14f
                setTextColor(0xFFD6AD55.toInt())
            }
            val body = TextView(this).apply {
                this.text = text
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                setTextIsSelectable(true)
                setPadding(0, 10, 0, 10)
            }
            val scroll = ScrollView(this).apply { addView(body) }
            val listen = Button(this).apply { this.text = "OUVIR / CONTINUAR"; textSize = 10f; minHeight = 0; minimumHeight = 0; setPadding(8, 2, 8, 2); setOnClickListener { speak(text) } }
            val pause = Button(this).apply { this.text = "PAUSAR VOZ"; textSize = 10f; minHeight = 0; minimumHeight = 0; setPadding(8, 2, 8, 2); setOnClickListener { tts?.stop() } }
            val slower = Button(this).apply { this.text = "FALAR MAIS DEVAGAR"; textSize = 10f; minHeight = 0; minimumHeight = 0; setPadding(8, 2, 8, 2); setOnClickListener { speechRate = (speechRate - 0.15f).coerceAtLeast(0.55f); tts?.setSpeechRate(speechRate); speak(text) } }
            val faster = Button(this).apply { this.text = "FALAR MAIS RÁPIDO"; textSize = 10f; minHeight = 0; minimumHeight = 0; setPadding(8, 2, 8, 2); setOnClickListener { speechRate = (speechRate + 0.15f).coerceAtMost(1.8f); tts?.setSpeechRate(speechRate); speak(text) } }
            val close = Button(this).apply { this.text = "FECHAR"; textSize = 10f; minHeight = 0; minimumHeight = 0; setPadding(8, 2, 8, 2); setOnClickListener { removeResult() } }
            panel.addView(heading)
            panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            panel.addView(listen); panel.addView(pause); panel.addView(slower); panel.addView(faster); panel.addView(close)
            resultWindow = panel
            val p = WindowManager.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                (resources.displayMetrics.heightPixels * 0.65f).toInt(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            wm.addView(panel, p)
            if (autoSpeak) speak(text)
        }
    }

    private fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.language = Locale("pt", "BR")
        tts?.setSpeechRate(speechRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "texto-capturado")
    }

    private fun removeResult() {
        resultWindow?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        resultWindow = null
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) { tts?.language = Locale("pt", "BR"); tts?.setSpeechRate(speechRate) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "Leitor de Tela", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val stop = Intent(this, CaptureService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Leitor de Tela ativo")
            .setContentText("A leitura só começa quando você toca em LER.")
            .addAction(Notification.Action.Builder(null, "Parar", pending).build())
            .build()
    }

    override fun onDestroy() {
        removeResult()
        removeSelectionOverlay()
        floatingButton?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        display?.release()
        imageReader?.close()
        projection?.stop()
        tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
