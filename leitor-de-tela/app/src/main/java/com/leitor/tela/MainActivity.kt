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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val captureRequest = 701
    private val overlayRequest = 702
    private val gold = Color.rgb(230, 190, 105)
    private val mutedGold = Color.rgb(170, 132, 65)
    private val backgroundColor = Color.rgb(12, 12, 15)
    private val panelColor = Color.rgb(27, 27, 32)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        render()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(22))
            setBackgroundColor(backgroundColor)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }

        val brand = TextView(this).apply {
            text = "LEITOR"
            textSize = 13f
            letterSpacing = 0.28f
            gravity = Gravity.CENTER
            setTextColor(mutedGold)
            setPadding(0, dp(12), 0, dp(3))
        }
        val title = TextView(this).apply {
            text = "LEITOR DE TELA"
            textSize = 27f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(gold)
        }
        val subtitle = TextView(this).apply {
            text = "Leia e traduza o que aparece no seu celular."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(7), 0, dp(20))
        }
        root.addView(brand)
        root.addView(title)
        root.addView(subtitle)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = rounded(panelColor, dp(20), mutedGold)
        }
        val heroTitle = TextView(this).apply {
            text = "TRADUÇÃO PARA PORTUGUÊS"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(gold)
        }
        val heroText = TextView(this).apply {
            text = "Escolha Japonês → Português, Coreano → Português ou Inglês → Português. Depois marque com o dedo a área que deseja traduzir."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, dp(16))
        }
        val start = actionButton("ATIVAR LEITOR", true).apply {
            setOnClickListener { requestOverlay() }
        }
        hero.addView(heroTitle)
        hero.addView(heroText)
        hero.addView(start, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(18)
        })

        val howTitle = TextView(this).apply {
            text = "COMO USAR"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
            setPadding(dp(3), dp(4), 0, dp(10))
        }
        root.addView(howTitle)
        root.addView(infoCard("01", "Ative o leitor", "Autorize a captura de tela e a exibição sobre outros aplicativos."))
        root.addView(infoCard("02", "Toque no botão LER", "O botão flutuante aparece por cima dos outros aplicativos."))
        root.addView(infoCard("03", "Escolha o idioma", "Selecione Japonês → Português para traduzir diretamente do japonês."))
        root.addView(infoCard("04", "Marque o texto", "Arraste o dedo para desenhar um retângulo em volta do texto."))

        val stop = actionButton("PARAR LEITOR", false).apply {
            setOnClickListener { stopService(Intent(this@MainActivity, CaptureService::class.java)) }
        }
        root.addView(stop, LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(18)
        })

        val footer = TextView(this).apply {
            text = "A tradução é processada no aparelho. Na primeira utilização, o modelo do idioma pode precisar ser baixado pela internet."
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(dp(8), dp(18), dp(8), dp(4))
        }
        root.addView(footer)
        setContentView(scroll)
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun actionButton(label: String, primary: Boolean): Button = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) Color.rgb(25, 22, 17) else gold)
        background = rounded(
            if (primary) gold else panelColor,
            dp(14),
            if (primary) null else mutedGold
        )
        stateListAnimator = null
    }

    private fun infoCard(number: String, heading: String, detail: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(panelColor, dp(15))
        }
        val num = TextView(this).apply {
            text = number
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(gold)
            background = rounded(Color.rgb(48, 42, 31), dp(12), mutedGold)
        }
        card.addView(num, LinearLayout.LayoutParams(dp(42), dp(42)))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        val title = TextView(this).apply {
            text = heading
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        val desc = TextView(this).apply {
            text = detail
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(3), 0, 0)
        }
        texts.addView(title)
        texts.addView(desc)
        card.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        return card.also {
            it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(9)
            }
        }
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
