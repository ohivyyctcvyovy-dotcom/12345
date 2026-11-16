# Estado dos motores de imagem

## Modelos investigados
- **SDXS-512 Tiny (683 MB, Q8):** candidato mais leve. O runtime LlamaDart/stable-diffusion.cpp documenta geração no Android arm64 e validação em Galaxy A53, S24 e Pixel 9 Pro. O tamanho do arquivo não é a RAM total necessária. No Galaxy A53, a medição publicada foi de 26–29 segundos por imagem em 512x512, uma etapa. Não há garantia para este aparelho.
- **SD-Turbo Q8 + TAESD (aprox. 2 GB):** segundo modelo documentado, mas pesado; o teste publicado informa que a verificação de memória recusou sua execução no Galaxy A53. Não deve ser selecionado como padrão.
- **Stable Diffusion 1.5:** alternativa conhecida, porém não incluída como motor integrado nesta versão.

## Edição de foto
O runtime LlamaDart documenta operações de imagem-para-imagem e edição com máscara em receitas específicas. Isso não significa que a função esteja conectada neste aplicativo. A aba de edição atual permite selecionar uma foto e escrever/ditar instruções, mas ainda não aplica mudanças com IA.

## Estado real deste APK
- Duas abas separadas: criar imagem e modificar foto existente.
- Seleção de imagem independente em cada aba.
- Campo de instruções independente e entrada por voz.
- Seletor informativo para SDXS e SD-Turbo.
- Os motores nativos, os arquivos dos modelos, o descarregamento de modelos e a inferência ainda NÃO estão integrados.
- Não anunciar geração ou edição por IA como funcional até integrar o runtime, verificar a arquitetura ARM do aparelho e testar no telefone com a memória disponível.
