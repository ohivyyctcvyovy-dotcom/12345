package com.leitor.tela

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.ContentValues
import android.graphics.PixelFormat
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.DisplayMetrics
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
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
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import com.google.android.gms.tasks.Tasks
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
    private var languagePickerWindow: LinearLayout? = null
    private var resultWindow: LinearLayout? = null
    private var selectionWindow: FrameLayout? = null
    private var selectedArea: RectF? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRate = 1.0f
    private var selectedOcrLanguage: String? = null
    // Dimensões reais do display usado pelo MediaProjection, incluindo as barras do sistema.
    private var captureScreenWidth = 0
    private var captureScreenHeight = 0
    private var pendingDiagnosticBitmap: Bitmap? = null
    private var lastDiagnosticReport = "Diagnóstico ainda não disponível."
    private var lastSelectedArea: RectF? = null

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
        // Use o tamanho físico do display. resources.displayMetrics pode excluir barras
        // do sistema em alguns aparelhos, deslocando o recorte vertical da seleção.
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        captureScreenWidth = metrics.widthPixels
        captureScreenHeight = metrics.heightPixels
        if (captureScreenWidth <= 0 || captureScreenHeight <= 0) {
            captureScreenWidth = resources.displayMetrics.widthPixels
            captureScreenHeight = resources.displayMetrics.heightPixels
            metrics.densityDpi = resources.displayMetrics.densityDpi
        }
        imageReader = ImageReader.newInstance(
            captureScreenWidth, captureScreenHeight, PixelFormat.RGBA_8888, 3
        )
        display = projection?.createVirtualDisplay(
            "LeitorDeTela", captureScreenWidth, captureScreenHeight, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, handler
        )
    }

    private fun showFloatingButton() {
        val button = Button(this).apply {
            text = "LER"
            textSize = 13f
            setOnClickListener {
                this.visibility = View.INVISIBLE
                showLanguagePicker()
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

    private fun showLanguagePicker() {
        removeLanguagePicker()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(22, 22, 25))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), 0xFFD6AD55.toInt())
            }
        }
        val title = TextView(this).apply {
            text = "ESCOLHA O IDIOMA"
            textSize = 19f
            setTextColor(0xFFFFD778.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        val subtitle = TextView(this).apply {
            text = "O texto será traduzido para português."
            textSize = 13f
            setTextColor(0xFFCCCCCC.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        }
        card.addView(title)
        card.addView(subtitle)
        val choices = listOf(
            "Detectar automaticamente" to null,
            "🇯🇵  Japonês  →  Português" to "ja",
            "🇰🇷  Coreano  →  Português" to "ko",
            "🇬🇧  Inglês  →  Português" to "en"
        )
        choices.forEach { (label, code) ->
            val choice = Button(this).apply {
                text = label
                textSize = 14f
                isAllCaps = false
                setTextColor(0xFFFFE7B0.toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF343038.toInt())
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), 0xFF80652F.toInt())
                }
                setOnClickListener {
                    selectedOcrLanguage = code
                    removeLanguagePicker()
                    showSelectionOverlay()
                }
            }
            card.addView(choice, LinearLayout.LayoutParams(-1, dp(50)).apply {
                bottomMargin = dp(8)
            })
        }
        val cancel = TextView(this).apply {
            text = "Cancelar"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFBBBBBB.toInt())
            setPadding(0, dp(8), 0, dp(2))
            setOnClickListener {
                removeLanguagePicker()
                floatingButton?.visibility = View.VISIBLE
            }
        }
        card.addView(cancel)
        languagePickerWindow = card
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        try {
            wm.addView(card, params)
        } catch (_: Exception) {
            languagePickerWindow = null
            floatingButton?.visibility = View.VISIBLE
            showResult("Não consegui abrir o menu. Confira a permissão de aparecer sobre outros aplicativos.", false)
        }
    }

    private fun removeLanguagePicker() {
        languagePickerWindow?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        languagePickerWindow = null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun showSelectionOverlay() {
        removeSelectionOverlay()
        val frame = FrameLayout(this)
        val selector = SelectionView(this) { area ->
            selectedArea = RectF(area)
            lastSelectedArea = RectF(area)
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
            val label = "Arraste para marcar a área desejada"
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
                    // Converta coordenadas locais do overlay para coordenadas absolutas
                    // da tela. Isso evita o deslocamento causado pela barra de status.
                    val screenPosition = IntArray(2)
                    getLocationOnScreen(screenPosition)
                    val rect = RectF(
                        startX + screenPosition[0],
                        startY + screenPosition[1],
                        currentX + screenPosition[0],
                        currentY + screenPosition[1]
                    ).apply { sort() }
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
        pendingDiagnosticBitmap?.let { if (!it.isRecycled) it.recycle() }
        pendingDiagnosticBitmap = capturedBitmap.copy(Bitmap.Config.ARGB_8888, false)
        val area = selectedArea
        selectedArea = null
        val bitmapForOcr = if (area != null) {
            val screenWidth = captureScreenWidth.takeIf { it > 0 } ?: capturedBitmap.width
            val screenHeight = captureScreenHeight.takeIf { it > 0 } ?: capturedBitmap.height
            val sx = capturedBitmap.width.toFloat() / screenWidth.toFloat()
            val sy = capturedBitmap.height.toFloat() / screenHeight.toFloat()
            val left = (area.left * sx).toInt().coerceIn(0, capturedBitmap.width - 1)
            val top = (area.top * sy).toInt().coerceIn(0, capturedBitmap.height - 1)
            val right = (area.right * sx).toInt().coerceIn(left + 1, capturedBitmap.width)
            val bottom = (area.bottom * sy).toInt().coerceIn(top + 1, capturedBitmap.height)
            lastDiagnosticReport = "Captura: ${capturedBitmap.width} × ${capturedBitmap.height}px\n" +
                "Tela de referência: ${screenWidth} × ${screenHeight}px\n" +
                "Fator de escala: x=${"%.3f".format(sx)}, y=${"%.3f".format(sy)}\n" +
                "Seleção na tela: ${area.left.toInt()},${area.top.toInt()} até ${area.right.toInt()},${area.bottom.toInt()}\n" +
                "Recorte aplicado: $left,$top até $right,$bottom (${right-left} × ${bottom-top}px)\n" +
                "Idioma escolhido: ${selectedOcrLanguage ?: "automático"}\n"
            // O print salvo recebe uma moldura sobre a região selecionada para
            // permitir conferir visualmente se o arrasto corresponde ao texto.
            pendingDiagnosticBitmap?.let { diagnostic ->
                val diagnosticCanvas = Canvas(diagnostic)
                val highlightFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x33FFD166
                    style = Paint.Style.FILL
                }
                val highlightOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.RED
                    style = Paint.Style.STROKE
                    strokeWidth = maxOf(5f, diagnostic.width * 0.003f)
                }
                val marked = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
                diagnosticCanvas.drawRect(marked, highlightFill)
                diagnosticCanvas.drawRect(marked, highlightOutline)
            }
            Bitmap.createBitmap(capturedBitmap, left, top, right - left, bottom - top)
        } else {
            lastDiagnosticReport = "Captura: ${capturedBitmap.width} × ${capturedBitmap.height}px\nIdioma escolhido: ${selectedOcrLanguage ?: "automático"}\n"
            capturedBitmap
        }

        // Textos japoneses em jogos e imagens costumam ser pequenos. Amplie o recorte
        // para melhorar o OCR. Preserve também a captura inteira para recuperar casos
        // em que as coordenadas do recorte variem entre fabricantes/barras do sistema.
        val scaledCrop = if (bitmapForOcr !== capturedBitmap &&
            (bitmapForOcr.width < 1400 || bitmapForOcr.height < 900)) {
            val factor = minOf(3f, maxOf(1.5f, 1400f / bitmapForOcr.width))
            Bitmap.createScaledBitmap(
                bitmapForOcr,
                (bitmapForOcr.width * factor).toInt().coerceAtLeast(1),
                (bitmapForOcr.height * factor).toInt().coerceAtLeast(1),
                true
            )
        } else bitmapForOcr

        try {
            val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val japaneseRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            val koreanRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            val cropImage = InputImage.fromBitmap(scaledCrop, 0)
            val latinTask = latinRecognizer.process(cropImage)
            val japaneseTask = japaneseRecognizer.process(cropImage)
            val koreanTask = koreanRecognizer.process(cropImage)

            // OCR adicional da tela inteira: usado somente se o recorte não encontrar
            // caracteres japoneses, evitando perder texto por diferenças de coordenadas.
            val fullJapaneseTask = if (bitmapForOcr !== capturedBitmap) {
                japaneseRecognizer.process(InputImage.fromBitmap(capturedBitmap, 0))
            } else null
            val fullKoreanTask = if (bitmapForOcr !== capturedBitmap) {
                koreanRecognizer.process(InputImage.fromBitmap(capturedBitmap, 0))
            } else null

            Tasks.whenAllComplete(
                *listOfNotNull(latinTask, japaneseTask, koreanTask, fullJapaneseTask, fullKoreanTask).toTypedArray()
            ).addOnSuccessListener { completed ->
                val latinText = if (latinTask.isSuccessful) latinTask.result?.text?.trim().orEmpty() else ""
                val cropJapaneseText = if (japaneseTask.isSuccessful) japaneseTask.result?.text?.trim().orEmpty() else ""
                val cropKoreanText = if (koreanTask.isSuccessful) koreanTask.result?.text?.trim().orEmpty() else ""
                val fullKoreanText = if (fullKoreanTask?.isSuccessful == true) fullKoreanTask.result?.text?.trim().orEmpty() else ""
                val koreanText = cropKoreanText.ifBlank { fullKoreanText }
                val fullJapaneseText = if (fullJapaneseTask?.isSuccessful == true) {
                    fullJapaneseTask.result?.text?.trim().orEmpty()
                } else ""
                val japaneseText = cropJapaneseText.ifBlank { fullJapaneseText }

                val japaneseChars = japaneseText.any {
                    it in '\u3040'..'\u30FF' || it in '\u4E00'..'\u9FFF'
                }
                val koreanChars = koreanText.any { it in '\uAC00'..'\uD7A3' }

                val selected = when {
                    selectedOcrLanguage == "ja" -> Pair(japaneseText.ifBlank { latinText.takeIf { value -> value.any { it in '\u3040'..'\u30FF' || it in '\u4E00'..'\u9FFF' } }.orEmpty() }, "ja")
                    selectedOcrLanguage == "ko" -> Pair(koreanText.ifBlank { latinText.takeIf { value -> value.any { it in '\uAC00'..'\uD7A3' } }.orEmpty() }, "ko")
                    selectedOcrLanguage == "en" -> Pair(latinText, "en")
                    koreanText.isNotBlank() && koreanChars -> Pair(koreanText, "ko")
                    japaneseText.isNotBlank() && japaneseChars -> Pair(japaneseText, "ja")
                    else -> {
                        val candidates = listOf(latinText, japaneseText, koreanText).filter { it.isNotBlank() }
                        val best = candidates.maxByOrNull { value ->
                            val hangul = value.count { it in '\uAC00'..'\uD7A3' }
                            val kana = value.count { it in '\u3040'..'\u30FF' }
                            val latin = value.count { it in 'A'..'Z' || it in 'a'..'z' }
                            val cjk = value.count { it in '\u4E00'..'\u9FFF' }
                            maxOf(hangul * 5 + kana * 5 + cjk * 2, latin)
                        }.orEmpty()
                        Pair(best, null)
                    }
                }
                lastDiagnosticReport += "\nOCR latino: ${latinText.take(1200)}\nOCR japonês: ${japaneseText.take(1200)}\nOCR coreano: ${koreanText.take(1200)}\n"
                if (selected.first.isBlank()) {
                    val errors = listOfNotNull(
                        latinTask.exception?.message,
                        japaneseTask.exception?.message,
                        koreanTask.exception?.message,
                        fullJapaneseTask?.exception?.message,
                        fullKoreanTask?.exception?.message
                    ).distinct().joinToString("; ")
                    val diagnostic = if (errors.isBlank()) "Os motores terminaram, mas não localizaram caracteres." else errors
                    lastDiagnosticReport += "Erros dos motores: ${if (errors.isBlank()) "nenhum erro técnico retornado" else errors}\n"
                    showResult(
                        "O OCR não encontrou texto. $diagnostic\n\n" +
                            "Imagem capturada: ${capturedBitmap.width} × ${capturedBitmap.height}px. " +
                            "Área analisada: ${scaledCrop.width} × ${scaledCrop.height}px.\n\n" +
                            "Marque uma área maior, incluindo todo o texto, e tente novamente.",
                        false
                    )
                } else {
                    lastDiagnosticReport += "Texto selecionado para tradução (${selected.second ?: "detecção automática"}): ${selected.first.take(2000)}\n"
                    detectAndTranslate(selected.first, selected.second)
                }
            }.addOnFailureListener { error ->
                lastDiagnosticReport += "Falha geral do OCR: ${error.message ?: error.javaClass.simpleName}\n"
                showResult("O reconhecimento falhou: ${error.message ?: "erro não identificado"}. Tente novamente.", false)
            }.addOnCompleteListener {
                if (scaledCrop !== bitmapForOcr && !scaledCrop.isRecycled) scaledCrop.recycle()
                if (bitmapForOcr !== capturedBitmap && !bitmapForOcr.isRecycled) bitmapForOcr.recycle()
                if (!capturedBitmap.isRecycled) capturedBitmap.recycle()
                latinRecognizer.close()
                japaneseRecognizer.close()
                koreanRecognizer.close()
            }
        } catch (error: Exception) {
            lastDiagnosticReport += "Exceção ao iniciar OCR: ${error.message ?: error.javaClass.simpleName}\n"
            if (scaledCrop !== bitmapForOcr && !scaledCrop.isRecycled) scaledCrop.recycle()
            if (bitmapForOcr !== capturedBitmap && !bitmapForOcr.isRecycled) bitmapForOcr.recycle()
            if (!capturedBitmap.isRecycled) capturedBitmap.recycle()
            showResult("Ocorreu um erro ao reconhecer a imagem. Tente novamente.", false)
        }
    }

    private fun detectAndTranslate(originalText: String, preferredLanguage: String? = null) {
        // Quando o reconhecedor especializado detecta caracteres japoneses ou coreanos,
        // respeite a origem do OCR. O Language ID costuma confundir textos curtos
        // compostos por kanji ou retornar "und".
        if (preferredLanguage == "ja" || preferredLanguage == "ko" || preferredLanguage == "en") {
            translateFromLanguage(originalText, preferredLanguage)
            return
        }
        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(originalText)
            .addOnSuccessListener { detectedCode ->
                languageIdentifier.close()
                // O identificador costuma retornar "und" em frases japonesas curtas.
                // Kana é um sinal forte de japonês; Hangul identifica coreano.
                val hasJapaneseKana = originalText.any { it in '\u3040'..'\u30FF' }
                val hasHangul = originalText.any { it in '\uAC00'..'\uD7A3' }
                val languageCode = when {
                    hasJapaneseKana -> "ja"
                    hasHangul -> "ko"
                    detectedCode != "und" -> detectedCode
                    else -> "und"
                }

                if (languageCode == "pt") {
                    showResult(originalText, true)
                    return@addOnSuccessListener
                }
                if (languageCode == "und") {
                    showResult("Não consegui identificar o idioma deste texto. Tente selecionar uma área maior.\n\nTexto reconhecido:\n$originalText", true)
                    return@addOnSuccessListener
                }

                translateFromLanguage(originalText, languageCode)
            }
            .addOnFailureListener {
                languageIdentifier.close()
                val hasJapaneseKana = originalText.any { it in '\u3040'..'\u30FF' }
                val hasHangul = originalText.any { it in '\uAC00'..'\uD7A3' }
                val fallbackCode = when {
                    hasJapaneseKana -> "ja"
                    hasHangul -> "ko"
                    else -> null
                }
                if (fallbackCode == null) {
                    showResult("Não consegui identificar o idioma.\n\nTexto reconhecido:\n$originalText", true)
                } else {
                    translateFromLanguage(originalText, fallbackCode)
                }
            }
    }

    private fun translateFromLanguage(originalText: String, languageCode: String) {
        val targetLanguage = TranslateLanguage.PORTUGUESE
        val sourceLanguage = TranslateLanguage.fromLanguageTag(languageCode)
        lastDiagnosticReport += "Idioma usado na tradução: $languageCode → pt\n"
        if (sourceLanguage == null || sourceLanguage == targetLanguage) {
            showResult("Este idioma ainda não está disponível para tradução automática.\n\nTexto reconhecido:\n$originalText", true)
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()
        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator.translate(originalText)
                    .addOnSuccessListener { translated ->
                        showResult(translated, true)
                        translator.close()
                    }
                    .addOnFailureListener { error ->
                        lastDiagnosticReport += "Falha na tradução: ${error.message ?: error.javaClass.simpleName}\n"
                        showResult("Não consegui traduzir este texto. Texto original:\\n$originalText", true)
                        translator.close()
                    }
            }
            .addOnFailureListener { error ->
                lastDiagnosticReport += "Falha ao baixar modelo: ${error.message ?: error.javaClass.simpleName}\n"
                showResult("Não consegui baixar o modelo de tradução. Conecte-se à internet e tente novamente.\\n\\nTexto original:\\n$originalText", false)
                translator.close()
            }
    }

    private fun showResult(text: String, autoSpeak: Boolean) {
        handler.post {
            floatingButton?.visibility = View.VISIBLE
            removeResult()
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                setBackgroundColor(0x66000000.toInt())
            }
            val heading = TextView(this).apply {
                this.text = "TRADUÇÃO / TEXTO EM PORTUGUÊS"
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
            val saveDiagnostic = Button(this).apply {
                this.text = "SALVAR PRINT + RELATÓRIO DE ERRO"
                textSize = 10f; minHeight = 0; minimumHeight = 0; setPadding(8, 4, 8, 4)
                setOnClickListener { saveDiagnosticFiles() }
            }
            val shareDiagnostic = Button(this).apply {
                this.text = "COMPARTILHAR PRINT + RELATÓRIO"
                textSize = 10f; minHeight = 0; minimumHeight = 0; setPadding(8, 4, 8, 4)
                setOnClickListener { shareDiagnosticFiles() }
            }
            val close = Button(this).apply { this.text = "FECHAR"; textSize = 10f; minHeight = 0; minimumHeight = 0; setPadding(8, 2, 8, 2); setOnClickListener { removeResult() } }
            panel.addView(heading)
            panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            panel.addView(listen); panel.addView(pause); panel.addView(slower); panel.addView(faster)
            if (pendingDiagnosticBitmap != null) {
                panel.addView(saveDiagnostic)
                panel.addView(shareDiagnostic)
            }
            panel.addView(close)
            resultWindow = panel
            val p = WindowManager.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                (resources.displayMetrics.heightPixels * 0.65f).toInt(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            wm.addView(panel, p)
            lastDiagnosticReport += "\nResultado mostrado ao usuário: ${text.take(2000)}\n"
            if (autoSpeak) speak(text)
        }
    }


    private fun saveDiagnosticFiles(): Uri? {
        val bitmap = pendingDiagnosticBitmap
        if (bitmap == null || bitmap.isRecycled) {
            Toast.makeText(this, "Não há captura disponível para salvar.", Toast.LENGTH_LONG).show()
            return
        }
        val stamp = System.currentTimeMillis()
        try {
            val imageValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "leitor_tela_$stamp.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LeitorDeTela")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageValues)
                ?: throw IllegalStateException("Não foi possível criar o arquivo de imagem")
            contentResolver.openOutputStream(imageUri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IllegalStateException("Falha ao gravar a imagem")
                }
            } ?: throw IllegalStateException("Não foi possível abrir o arquivo de imagem")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                contentResolver.update(imageUri, ready, null, null)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val reportValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "leitor_tela_diagnostico_$stamp.txt")
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LeitorDeTela")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val reportUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, reportValues)
                    ?: throw IllegalStateException("Imagem salva, mas não foi possível criar o relatório")
                contentResolver.openOutputStream(reportUri)?.use { it.write(lastDiagnosticReport.toByteArray(Charsets.UTF_8)) }
                    ?: throw IllegalStateException("Imagem salva, mas não foi possível gravar o relatório")
                val readyReport = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                contentResolver.update(reportUri, readyReport, null, null)
            }
            Toast.makeText(this, "Print salvo em Imagens/Pictures > LeitorDeTela. Relatório em Downloads > LeitorDeTela.", Toast.LENGTH_LONG).show()
            return imageUri
        } catch (error: Exception) {
            Toast.makeText(this, "Erro ao salvar diagnóstico: ${error.message ?: "erro desconhecido"}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun shareDiagnosticFiles() {
        val imageUri = saveDiagnosticFiles() ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, "Diagnóstico do Leitor de Tela\\n\\n$lastDiagnosticReport")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(contentResolver, "Print do Leitor de Tela", imageUri)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, "Enviar diagnóstico").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: Exception) {
            Toast.makeText(this, "O print foi salvo, mas não consegui abrir o compartilhamento: ${error.message ?: "erro"}", Toast.LENGTH_LONG).show()
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
        removeLanguagePicker()
        removeSelectionOverlay()
        floatingButton?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        display?.release()
        imageReader?.close()
        pendingDiagnosticBitmap?.let { if (!it.isRecycled) it.recycle() }
        pendingDiagnosticBitmap = null
        projection?.stop()
        tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
