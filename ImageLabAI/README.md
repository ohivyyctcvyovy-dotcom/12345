# ImageLab AI

Aplicativo Android em português, com interface roxa, pensado para uso pelo celular.

## Objetivo
- Criar imagens a partir de texto ou voz.
- Selecionar fotos da galeria e pedir alterações (roupa, tênis, pose, cenário).
- Guardar resultados no próprio aparelho.
- Fazer a geração e a edição com um modelo local, sem enviar fotos ou prompts para servidores.

## Estado desta versão
A primeira versão Android contém a interface, seleção de fotos e entrada de voz. **A geração e a edição por IA ainda não estão ativadas.** Não é correto apresentar um botão falso como se a IA estivesse funcionando.

## Por que o motor ainda não foi ativado?
O Google recomenda testar o MediaPipe Image Generator em aparelhos com pelo menos 8 GB de RAM. A tarefa é experimental e depende de arquivos de modelo convertidos e instalados separadamente. Consulte o [Codelab oficial do Google](https://codelabs.developers.google.com/mp-image-generation-basic-android?hl=pt-br).

Como o objetivo é funcionar em um celular de entrada, não vamos incluir SDXL como padrão nem baixar um modelo grande sem teste. A próxima etapa é testar uma alternativa baseada em Stable Diffusion 1.5 quantizado, medir memória livre e verificar se o aparelho consegue executar uma imagem pequena sem fechar.

## Compilar APK pelo celular
1. Abra o repositório no GitHub.
2. Entre em **Actions**.
3. Escolha **Build ImageLab AI APK** e execute o workflow, ou aguarde uma execução após um envio de alterações.
4. Abra a execução concluída e baixe o artefato **ImageLab-AI-debug**.
5. Extraia o ZIP e instale o arquivo APK no Android.

A compilação acontece nos servidores do GitHub Actions. O aplicativo instalado não precisa de internet para abrir a interface e selecionar fotos. A geração offline só será habilitada quando um modelo compatível for integrado e testado.

## Estrutura
- `android/`: projeto Android nativo.
- `AI_ENGINE_STATUS.md`: decisões e situação do motor de IA.
