# Leitor de Tela para Android

Aplicativo Android nativo de captura pontual de tela, OCR local e leitura em voz alta.

## Recursos
- Botão flutuante **LER** sobre outros aplicativos.
- Captura de tela iniciada com autorização explícita do Android.
- Reconhecimento de texto no aparelho usando Google ML Kit (modelo latino).
- Leitura automática em português do Brasil com a voz TTS do Android.
- Controles para pausar, continuar e ajustar a velocidade da fala.
- Botão para encerrar a sessão pela notificação ou pelo app.

## Como obter o APK
O GitHub Actions compila uma versão de teste automaticamente quando este projeto é atualizado. Depois da conclusão, o APK fica na seção **Releases** do repositório, com o nome `app-debug.apk`.

## Como usar
1. Instale o APK no Android.
2. Abra **Leitor de Tela** e toque em **ATIVAR LEITOR**.
3. Permita que o aplicativo apareça sobre outros apps.
4. Autorize a captura de tela na janela oficial do Android.
5. Vá até a foto, página ou aplicativo que deseja ler.
6. Toque no botão flutuante **LER**. O texto será reconhecido e lido em voz alta.
7. Use os controles na janela para pausar, continuar ou mudar a velocidade.
8. Para terminar, toque em **PARAR LEITOR** no aplicativo ou em **Parar** na notificação.

## Privacidade e limitações
- O aplicativo só executa OCR quando o usuário toca em **LER**; não faz leitura contínua.
- O Android mantém uma sessão de captura ativa enquanto o botão flutuante está disponível. Por regra do Android, essa sessão usa um serviço em primeiro plano e mostra uma notificação. Isso não significa que o app esteja analisando continuamente o texto.
- O reconhecimento de texto é feito localmente pelo ML Kit. Este projeto não envia a imagem ou o texto a um servidor.
- Alguns aplicativos protegidos impedem capturas de tela; nesses casos a leitura pode não funcionar.
- O APK de debug é adequado para testes pessoais, não é uma versão assinada para distribuição pública.

## Abrir no Android Studio
Abra esta pasta como projeto Gradle. Requer JDK 17 e Android SDK 35.
