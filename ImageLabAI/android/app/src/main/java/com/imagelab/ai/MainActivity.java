package com.imagelab.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 11, 24);
    private static final int PANEL = Color.rgb(43, 30, 60);
    private static final int PURPLE = Color.rgb(192, 132, 252);
    private static final int WHITE = Color.rgb(248, 244, 255);

    private static final int PICK_CREATE_IMAGE = 10;
    private static final int PICK_EDIT_IMAGE = 12;
    private static final int SPEAK_CREATE = 11;
    private static final int SPEAK_EDIT = 13;
    private static final int PICK_MODEL = 14;
    private static final long MIN_FREE_RAM_BYTES = 1536L * 1024L * 1024L;

    private static boolean nativeReady;
    static {
        try {
            System.loadLibrary("imagelab_engine");
            nativeReady = true;
        } catch (Throwable error) {
            nativeReady = false;
        }
    }

    private native int[] nativeGenerate(ParcelFileDescriptor modelFd, String prompt,
                                         int width, int height, int threads);
    private native String nativeLastError();

    private LinearLayout page, createPanel, editPanel;
    private EditText createPrompt, editPrompt;
    private ImageView createPreview, editPreview;
    private TextView createStatus, editStatus, modelStatus;
    private Uri modelUri;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        String savedModelPath = getPreferences(MODE_PRIVATE).getString("model_path", "");
        File savedModel = savedModelPath.isEmpty() ? null : new File(savedModelPath);
        modelUri = savedModel != null && savedModel.isFile() ? Uri.fromFile(savedModel) : null;
        build();
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(page);

        page.addView(txt("ImageLab AI", 29, PURPLE, true));
        page.addView(txt("Estúdio de criação e edição no celular", 13, 0xFFD7C7E8, false));

        LinearLayout tabs = new LinearLayout(this);
        Button createTab = btn("✦ Criar imagem", true);
        Button editTab = btn("▧ Modificar foto", false);
        LinearLayout.LayoutParams tabA = new LinearLayout.LayoutParams(0, dp(48), 1);
        tabA.topMargin = dp(18);
        tabs.addView(createTab, tabA);
        LinearLayout.LayoutParams tabB = new LinearLayout.LayoutParams(0, dp(48), 1);
        tabB.leftMargin = dp(8);
        tabB.topMargin = dp(18);
        tabs.addView(editTab, tabB);
        page.addView(tabs);

        createPanel = new LinearLayout(this);
        createPanel.setOrientation(LinearLayout.VERTICAL);
        createPanel.setPadding(0, dp(18), 0, 0);
        createPanel.addView(txt("CRIAR UMA IMAGEM NOVA", 14, PURPLE, true));
        createPrompt = edit("Descreva a imagem que deseja criar...");
        createPanel.addView(createPrompt, new LinearLayout.LayoutParams(-1, dp(110)));

        LinearLayout voiceAndReference = new LinearLayout(this);
        Button voice = btn("🎙 Falar", false);
        Button reference = btn("＋ Foto de referência", false);
        voiceAndReference.addView(voice, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams referenceParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        referenceParams.leftMargin = dp(8);
        voiceAndReference.addView(reference, referenceParams);
        createPanel.addView(voiceAndReference);

        createPreview = preview();
        createPanel.addView(createPreview, previewParams());
        createStatus = txt("Nenhuma referência selecionada.", 12, 0xFFD7C7E8, false);
        createPanel.addView(createStatus);

        createPanel.addView(txt("MODELO DE IA LOCAL", 13, PURPLE, true));
        modelStatus = txt(modelUri == null ? "A IA SDXS-512 Tiny ainda não foi baixada." :
                "IA pronta: SDXS-512 Tiny Q8", 12, 0xFFD7C7E8, false);
        LinearLayout.LayoutParams modelStatusParams = new LinearLayout.LayoutParams(-1, -2);
        modelStatusParams.topMargin = dp(6);
        createPanel.addView(modelStatus, modelStatusParams);

        Button chooseModel = btn("↓ Carregar IA SDXS-512 Tiny", false);
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(-1, dp(48));
        chooseParams.topMargin = dp(8);
        createPanel.addView(chooseModel, chooseParams);

        Button generate = btn("✦ Gerar imagem (SDXS / 1 etapa)", true);
        LinearLayout.LayoutParams generateParams = new LinearLayout.LayoutParams(-1, dp(50));
        generateParams.topMargin = dp(10);
        createPanel.addView(generate, generateParams);

        Button diagnostic = btn("Verificar aparelho e memória", false);
        LinearLayout.LayoutParams diagnosticParams = new LinearLayout.LayoutParams(-1, dp(46));
        diagnosticParams.topMargin = dp(8);
        createPanel.addView(diagnostic, diagnosticParams);
        page.addView(createPanel);

        editPanel = new LinearLayout(this);
        editPanel.setOrientation(LinearLayout.VERTICAL);
        editPanel.setPadding(0, dp(18), 0, 0);
        editPanel.setVisibility(View.GONE);
        editPanel.addView(txt("MODIFICAR UMA FOTO EXISTENTE", 14, PURPLE, true));
        editPanel.addView(txt("Escolha uma foto e escreva o que deseja mudar.", 13, 0xFFD7C7E8, false));
        Button pickEdit = btn("＋ Escolher foto da galeria", false);
        LinearLayout.LayoutParams pickEditParams = new LinearLayout.LayoutParams(-1, dp(48));
        pickEditParams.topMargin = dp(10);
        editPanel.addView(pickEdit, pickEditParams);
        editPreview = preview();
        editPanel.addView(editPreview, previewParams());
        editStatus = txt("Nenhuma foto selecionada.", 12, 0xFFD7C7E8, false);
        editPanel.addView(editStatus);
        editPrompt = edit("Ex.: mude o fundo, a roupa ou a iluminação...");
        LinearLayout.LayoutParams editPromptParams = new LinearLayout.LayoutParams(-1, dp(110));
        editPromptParams.topMargin = dp(10);
        editPanel.addView(editPrompt, editPromptParams);
        Button editVoice = btn("🎙 Ditar alteração", false);
        editPanel.addView(editVoice);
        Button apply = btn("✦ Aplicar mudança na foto", true);
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(-1, dp(50));
        applyParams.topMargin = dp(10);
        editPanel.addView(apply, applyParams);
        page.addView(editPanel);

        TextView note = txt("MOTOR NATIVO C/C++ INTEGRADO EM TESTE. O modelo é escolhido pelo usuário e fica no próprio celular. A geração ainda precisa ser validada neste aparelho; se a memória estiver baixa, o app bloqueia a tentativa para evitar travamentos. A edição de fotos ainda não está conectada. Nenhuma foto ou descrição é enviada para servidores.", 12, 0xFFB8A9C8, false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(20);
        page.addView(note, noteParams);

        createTab.setOnClickListener(v -> switchTab(true, createTab, editTab));
        editTab.setOnClickListener(v -> switchTab(false, createTab, editTab));
        voice.setOnClickListener(v -> speak(SPEAK_CREATE));
        reference.setOnClickListener(v -> pickImage(PICK_CREATE_IMAGE));
        chooseModel.setOnClickListener(v -> showModelDownloadDialog(chooseModel));
        generate.setOnClickListener(v -> generateImage(generate));
        diagnostic.setOnClickListener(v -> showDiagnostic());
        pickEdit.setOnClickListener(v -> pickImage(PICK_EDIT_IMAGE));
        editVoice.setOnClickListener(v -> speak(SPEAK_EDIT));
        apply.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Edição ainda não ativada")
                .setMessage("O motor de edição de fotos ainda precisa ser integrado. Nenhuma alteração foi aplicada.")
                .setPositiveButton("Entendi", null).show());

        setContentView(scroll);
    }

    private void switchTab(boolean creating, Button createTab, Button editTab) {
        createPanel.setVisibility(creating ? View.VISIBLE : View.GONE);
        editPanel.setVisibility(creating ? View.GONE : View.VISIBLE);
        createTab.setBackground(bg(creating ? PURPLE : PANEL));
        createTab.setTextColor(creating ? Color.rgb(28, 15, 40) : WHITE);
        editTab.setBackground(bg(creating ? PANEL : PURPLE));
        editTab.setTextColor(creating ? WHITE : Color.rgb(28, 15, 40));
    }

    private String memorySummary() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(info);
        return String.format(Locale.US, "RAM total: %.2f GB\nRAM disponível agora: %.2f GB",
                info.totalMem / 1073741824.0, info.availMem / 1073741824.0);
    }

    private void showDiagnostic() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(info);
        boolean arm64 = false;
        for (String abi : Build.SUPPORTED_ABIS) if ("arm64-v8a".equalsIgnoreCase(abi)) arm64 = true;
        String message = "Android: " + Build.VERSION.RELEASE +
                "\nArquitetura arm64: " + (arm64 ? "sim" : "não detectada") +
                "\nMotor C/C++ carregado: " + (nativeReady ? "sim" : "não") +
                "\n" + memorySummary() +
                "\n\nO app usa CPU genérica arm64; não exige GPU Adreno. Para proteger o aparelho, a geração só começa com pelo menos 1,5 GB de RAM disponível. Isso é uma margem de segurança, não garantia de que o modelo vai funcionar.";
        new AlertDialog.Builder(this).setTitle("Diagnóstico do aparelho")
                .setMessage(message).setPositiveButton("Entendi", null).show();
    }

    private void showModelDownloadDialog(Button button) {
        if (modelUri != null && new File(modelUri.getPath()).isFile()) {
            Toast.makeText(this, "A IA SDXS-512 Tiny já está carregada.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Modelo de IA disponível")
                .setMessage("SDXS-512 Tiny Q8 GGUF\nAproximadamente 683 MB\n\nEste é o único modelo disponível nesta versão.")
                .setPositiveButton("Baixar e carregar", (dialog, which) -> downloadModel(button))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void downloadModel(Button button) {
        if (modelUri != null && new File(modelUri.getPath()).isFile()) {
            Toast.makeText(this, "A IA já está carregada no aplicativo.", Toast.LENGTH_LONG).show();
            return;
        }
        button.setEnabled(false);
        modelStatus.setText("Preparando o download da IA SDXS-512 Tiny...");
        new Thread(() -> {
            File target = null;
            HttpURLConnection connection = null;
            try {
                File folder = getExternalFilesDir(null);
                if (folder == null) folder = getFilesDir();
                StatFs storage = new StatFs(folder.getAbsolutePath());
                long available = storage.getAvailableBytes();
                if (available < 750L * 1024L * 1024L) {
                    throw new Exception("É necessário liberar pelo menos 750 MB de espaço no celular para baixar o modelo.");
                }
                target = new File(folder, "sdxs-512-tinySDdistilled_Q8_0.gguf");
                URL url = new URL("https://huggingface.co/concedo/sdxs-512-tinySDdistilled-GGUF/resolve/3144d898d61492f8382ffcabec055733fc5b2a0e/sdxs-512-tinySDdistilled_Q8_0.gguf?download=true");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();
                int response = connection.getResponseCode();
                if (response < 200 || response >= 300) throw new Exception("O servidor não liberou o download (código " + response + ").");
                long total = connection.getContentLengthLong();
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[1024 * 256];
                    long received = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        received += count;
                        if (total > 0 && received % (5L * 1024L * 1024L) < buffer.length) {
                            final int percent = (int) (received * 100 / total);
                            runOnUiThread(() -> modelStatus.setText("Baixando a IA... " + percent + "%"));
                        }
                    }
                    output.getFD().sync();
                }
                if (target.length() < 500L * 1024L * 1024L) throw new Exception("O download ficou incompleto. Verifique a internet e tente novamente.");
                File completed = target;
                runOnUiThread(() -> {
                    modelUri = Uri.fromFile(completed);
                    getPreferences(MODE_PRIVATE).edit().putString("model_path", completed.getAbsolutePath()).apply();
                    modelStatus.setText("IA pronta: SDXS-512 Tiny Q8");
                    button.setEnabled(true);
                    button.setText("✓ IA carregada");
                    Toast.makeText(this, "Modelo baixado e pronto para usar.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                if (target != null && target.exists()) target.delete();
                String message = error.getMessage() == null ? "Não foi possível baixar a IA." : error.getMessage();
                runOnUiThread(() -> {
                    modelStatus.setText("A IA ainda não foi baixada.");
                    button.setEnabled(true);
                    showError(message + "\n\nConecte-se ao Wi-Fi ou confira o espaço livre e tente novamente.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "ImageLab-ModelDownload").start();
    }

    private void pickModel() {
        // Abra o seletor de documentos diretamente. Em alguns aparelhos Samsung,
        // envolver ACTION_OPEN_DOCUMENT em ACTION_CHOOSER deixa uma tela branca.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/octet-stream", "application/x-gguf", "application/gguf", "*/*"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        try {
            startActivityForResult(intent, PICK_MODEL);
        } catch (android.content.ActivityNotFoundException error) {
            // Fallback para gerenciadores que só oferecem ACTION_GET_CONTENT.
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("*/*");
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                    "application/octet-stream", "application/x-gguf", "application/gguf", "*/*"
            });
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(fallback, PICK_MODEL);
            } catch (Exception fallbackError) {
                showError("O Android não conseguiu abrir o seletor de arquivos. Abra o aplicativo Arquivos do celular e tente novamente.");
            }
        } catch (Exception error) {
            showError("Não foi possível abrir os arquivos: " + error.getMessage());
        }
    }

    private String displayName(Uri uri) {
        String name = uri.getLastPathSegment();
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return name == null ? "modelo selecionado" : name;
    }

    private String savedModelName() {
        return modelUri == null ? "" : displayName(modelUri);
    }

    private void generateImage(Button button) {
        if (!nativeReady) {
            showError("O motor nativo não foi carregado nesta instalação.");
            return;
        }
        if (modelUri == null) {
            showError("Primeiro selecione o arquivo GGUF do modelo SDXS no armazenamento do celular.");
            return;
        }
        String prompt = createPrompt.getText().toString().trim();
        if (prompt.isEmpty()) {
            showError("Escreva ou dite uma descrição para a imagem.");
            return;
        }

        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(info);
        if (info.availMem < MIN_FREE_RAM_BYTES) {
            showError("A RAM disponível está baixa (" +
                    String.format(Locale.US, "%.2f GB", info.availMem / 1073741824.0) +
                    "). Para evitar que o celular trave, a geração não foi iniciada. Feche outros aplicativos e tente novamente quando houver pelo menos 1,5 GB disponíveis.");
            return;
        }

        final Uri selectedModel = modelUri;
        final String finalPrompt = prompt;
        button.setEnabled(false);
        createStatus.setText("Carregando o modelo e preparando a imagem...");
        new Thread(() -> {
            int[] pixels = null;
            String error = null;
            try (ParcelFileDescriptor descriptor = openModelDescriptor(selectedModel)) {
                if (descriptor == null) {
                    error = "Não foi possível abrir o arquivo do modelo.";
                } else {
                    pixels = nativeGenerate(descriptor, finalPrompt, 512, 512, 2);
                    if (pixels == null) error = nativeLastError();
                }
            } catch (OutOfMemoryError memoryError) {
                error = "O Android ficou sem memória durante a geração. O modelo não foi executado até o fim.";
            } catch (Exception exception) {
                error = "Falha ao abrir o modelo: " + exception.getMessage();
            }

            final int[] generatedPixels = pixels;
            final String generationError = error;
            runOnUiThread(() -> {
                button.setEnabled(true);
                if (generatedPixels == null) {
                    createStatus.setText("A geração não foi concluída.");
                    showError(generationError == null || generationError.isEmpty()
                            ? "O motor não conseguiu gerar a imagem." : generationError);
                    return;
                }
                Bitmap bitmap = Bitmap.createBitmap(generatedPixels, 512, 512, Bitmap.Config.ARGB_8888);
                createPreview.setImageBitmap(bitmap);
                String saved = saveImage(bitmap);
                createStatus.setText(saved == null ? "Imagem gerada, mas não foi possível salvá-la." :
                        "Imagem gerada e salva em Pictures/ImageLabAI.");
                Toast.makeText(this, saved == null ? "Imagem gerada." : "Imagem salva na galeria.", Toast.LENGTH_LONG).show();
            });
        }, "ImageLab-Generate").start();
    }

    private ParcelFileDescriptor openModelDescriptor(Uri uri) throws java.io.FileNotFoundException {
        if (uri == null) return null;
        // Modelos baixados pelo próprio app usam file://; abra-os diretamente.
        // Arquivos escolhidos pelo usuário continuam usando content://.
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return ParcelFileDescriptor.open(new File(uri.getPath()),
                    ParcelFileDescriptor.MODE_READ_ONLY);
        }
        return getContentResolver().openFileDescriptor(uri, "r");
    }

    private String saveImage(Bitmap bitmap) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "ImageLab_" + System.currentTimeMillis() + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/ImageLabAI");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return null;
            try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                if (stream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    getContentResolver().delete(uri, null, null);
                    return null;
                }
            }
            ContentValues complete = new ContentValues();
            complete.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, complete, null, null);
            return uri.toString();
        } catch (Exception error) {
            if (uri != null) getContentResolver().delete(uri, null, null);
            return null;
        }
    }

    private void showError(String message) {
        new AlertDialog.Builder(this).setTitle("ImageLab AI")
                .setMessage(message).setPositiveButton("Entendi", null).show();
    }

    private void pickImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, requestCode);
    }

    private void speak(int requestCode) {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
            startActivityForResult(intent, requestCode);
        } catch (Exception error) {
            Toast.makeText(this, "Voz indisponível.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            // Cancelar o seletor não apaga um modelo escolhido anteriormente.
            return;
        }
        Uri uri = data.getData();
        if (requestCode == PICK_MODEL) {
            String name = displayName(uri);
            if (!name.toLowerCase(Locale.US).endsWith(".gguf")) {
                showError("Por enquanto, selecione um arquivo .gguf compatível com SDXS.");
                return;
            }
            try {
                int grantedFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if (grantedFlags != 0) getContentResolver().takePersistableUriPermission(uri, grantedFlags);
            } catch (Exception ignored) {
                // Alguns provedores permitem leitura apenas durante a sessão atual.
            }
            modelUri = uri;
            getPreferences(MODE_PRIVATE).edit().putString("model_uri", uri.toString()).apply();
            modelStatus.setText("Modelo selecionado: " + name + "\nO arquivo permanece no armazenamento escolhido.");
        } else if (requestCode == PICK_CREATE_IMAGE || requestCode == PICK_EDIT_IMAGE) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            if (requestCode == PICK_CREATE_IMAGE) {
                createPreview.setImageURI(uri);
                createStatus.setText("Referência selecionada.");
            } else {
                editPreview.setImageURI(uri);
                editStatus.setText("Foto selecionada para modificação.");
            }
        } else if (requestCode == SPEAK_CREATE || requestCode == SPEAK_EDIT) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                (requestCode == SPEAK_CREATE ? createPrompt : editPrompt).setText(results.get(0));
            }
        }
    }

    private EditText edit(String hint) {
        EditText field = new EditText(this);
        field.setTextColor(WHITE);
        field.setHintTextColor(0xFFB8A9C8);
        field.setHint(hint);
        field.setTextSize(14);
        field.setGravity(Gravity.TOP);
        field.setMinLines(3);
        field.setPadding(dp(14), dp(14), dp(14), dp(14));
        field.setBackground(bg(PANEL));
        return field;
    }

    private ImageView preview() {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackground(bg(PANEL));
        return image;
    }

    private LinearLayout.LayoutParams previewParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(180));
        params.topMargin = dp(10);
        params.bottomMargin = dp(8);
        return params;
    }

    private TextView txt(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private Button btn(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(primary ? Color.rgb(28, 15, 40) : WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(bg(primary ? PURPLE : PANEL));
        return button;
    }

    private android.graphics.drawable.GradientDrawable bg(int color) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(16));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
