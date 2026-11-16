package com.imagelab.ai;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(16, 11, 24);
    private final int PANEL = Color.rgb(43, 30, 60);
    private final int PURPLE = Color.rgb(192, 132, 252);
    private final int WHITE = Color.rgb(248, 244, 255);
    private EditText prompt;
    private ImageView preview;
    private TextView status;
    private static final int PICK_IMAGE = 10;
    private static final int SPEECH = 11;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(page);

        page.addView(text("ImageLab AI", 30, PURPLE, true));
        page.addView(text("Seu estúdio de criação e edição", 14, 0xFFD7C7E8, false));

        TextView createTitle = text("CRIAR IMAGEM", 13, PURPLE, true);
        createTitle.setPadding(0, dp(24), 0, dp(8));
        page.addView(createTitle);
        prompt = new EditText(this);
        prompt.setTextColor(WHITE);
        prompt.setHintTextColor(0xFFB8A9C8);
        prompt.setHint("Descreva a imagem ou a alteração que deseja...");
        prompt.setTextSize(15);
        prompt.setGravity(Gravity.TOP);
        prompt.setMinLines(3);
        prompt.setPadding(dp(14), dp(14), dp(14), dp(14));
        prompt.setBackground(round(PANEL, 18));
        page.addView(prompt, new LinearLayout.LayoutParams(-1, dp(116)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button voice = button("🎙 Falar", false);
        Button add = button("＋ Escolher foto", false);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(48), 1);
        ap.topMargin = dp(10);
        actions.addView(voice, ap);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(48), 1);
        bp.leftMargin = dp(8); bp.topMargin = dp(10);
        actions.addView(add, bp);
        page.addView(actions);

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(round(PANEL, 18));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(220));
        pp.topMargin = dp(18);
        page.addView(preview, pp);

        status = text("Nenhuma foto selecionada.", 13, 0xFFD7C7E8, false);
        status.setPadding(0, dp(8), 0, dp(4));
        page.addView(status);

        Button generate = button("✦  Verificar geração local", true);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(54));
        gp.topMargin = dp(14);
        page.addView(generate, gp);

        TextView editTitle = text("EDITAR FOTO", 13, PURPLE, true);
        editTitle.setPadding(0, dp(28), 0, dp(8));
        page.addView(editTitle);
        page.addView(text("Escolha uma foto da galeria e descreva a mudança: roupa, tênis, pose ou cenário. A edição generativa será ativada quando o motor local compatível estiver integrado.", 13, 0xFFD7C7E8, false));

        TextView memory = text(deviceMemoryText(), 12, 0xFFB8A9C8, false);
        memory.setPadding(0, dp(18), 0, 0);
        page.addView(memory);

        TextView foot = text("Esta versão ainda não gera imagens por IA. Ela prepara a interface e verifica o aparelho; não envia suas fotos para a internet.", 12, 0xFFB8A9C8, false);
        foot.setPadding(0, dp(14), 0, 0);
        page.addView(foot);

        add.setOnClickListener(v -> openPicker());
        voice.setOnClickListener(v -> startSpeech());
        generate.setOnClickListener(v -> {
            String p = prompt.getText().toString().trim();
            if (p.isEmpty()) {
                Toast.makeText(this, "Escreva ou fale o que deseja criar.", Toast.LENGTH_SHORT).show();
            } else {
                new AlertDialogBuilder(this).showInfo(
                    "Motor de IA ainda não conectado",
                    "Seu pedido foi preenchido, mas esta versão ainda não tem um modelo local integrado. Não vamos iniciar uma geração que possa fechar o aplicativo."
                );
            }
        });
        setContentView(scroll);
    }

    private String deviceMemoryText() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(info);
        double total = info.totalMem / 1073741824.0;
        double free = info.availMem / 1073741824.0;
        return String.format(java.util.Locale.US,
            "Memória aproximada: %.1f GB total; %.1f GB disponível agora. O Google recomenda aparelho com pelo menos 8 GB para seu exemplo experimental de geração de imagens MediaPipe. Isso não é um teste de compatibilidade definitivo.",
            total, free);
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_IMAGE);
    }

    private void startSpeech() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale o que deseja criar ou editar");
            startActivityForResult(i, SPEECH);
        } catch (Exception e) {
            Toast.makeText(this, "O reconhecimento de voz não está disponível neste aparelho.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_IMAGE) {
            Uri uri = data.getData();
            if (uri != null) {
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (Exception ignored) {}
                preview.setImageURI(uri);
                status.setText("Foto selecionada. Escreva ou fale a alteração desejada.");
            }
        } else if (requestCode == SPEECH) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String old = prompt.getText().toString();
                prompt.setText(old.isEmpty() ? results.get(0) : old + " " + results.get(0));
            }
        }
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(primary ? Color.rgb(28, 15, 40) : WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(primary ? PURPLE : PANEL, 16));
        return b;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private static class AlertDialogBuilder {
        private final Activity activity;
        AlertDialogBuilder(Activity a) { activity = a; }
        void showInfo(String title, String message) {
            new android.app.AlertDialog.Builder(activity)
                .setTitle(title).setMessage(message)
                .setPositiveButton("Entendi", null).show();
        }
    }
}
