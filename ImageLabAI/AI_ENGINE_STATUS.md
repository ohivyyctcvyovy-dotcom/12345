# Estado do motor de imagens do ImageLab AI

## Motor nativo adicionado
- O aplicativo Android agora inclui uma camada JNI em C++ que chama a API C oficial do projeto [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp).
- Código upstream fixado no commit `532d2c15c430a785a712b2d94281f04e733008a4` para builds reproduzíveis.
- Compilação configurada para Android `arm64-v8a`, CPU genérica, sem OpenCL/Adreno, Vulkan, CUDA ou OpenMP.
- O motor usa memory mapping para evitar copiar todo o arquivo de modelo para a memória do aplicativo.
- A tela permite escolher um arquivo GGUF local. O arquivo não é baixado automaticamente e não é enviado para servidor.
- A primeira rotina de inferência está preparada para SDXS: 512×512, uma etapa e guidance 1. O resultado é convertido em bitmap e salvo em Pictures/ImageLabAI.
- O app limita a tentativa a aparelhos com pelo menos 1,5 GB de RAM disponível como proteção inicial. Esse limite é uma margem conservadora, não uma garantia de execução.

## Modelo candidato
- **SDXS-512 Tiny Q8 GGUF (683 MB):** candidato inicial, não embutido no APK. A documentação upstream descreve SDXS-512 e exige CFG 1 e uma etapa. O arquivo GGUF deve ser escolhido pelo usuário.
- A aceitação do arquivo, a memória de pico, o tempo e a qualidade precisam ser validados em um aparelho real.
- O tamanho do arquivo não corresponde à RAM total necessária. Em um aparelho com cerca de 0,7 GB livres, o app deve bloquear a geração para reduzir o risco de travamento.

## Limitações atuais
- A integração C/C++ foi adicionada ao código, mas ainda precisa passar pela compilação NDK e por uma geração real em hardware antes de ser considerada funcional.
- O Galaxy A04e ainda não foi validado com esse motor/modelo. Não prometer que a geração local funcionará nele.
- A aba de edição de fotos ainda não está conectada à inferência image-to-image/inpainting.
- O APK não contém os pesos do modelo; o modelo é selecionado no armazenamento do próprio telefone.
- Nenhum prompt ou arquivo de imagem é enviado a servidores pelo fluxo de geração implementado.

## Licença
- O código stable-diffusion.cpp é incorporado como dependência de build e permanece sujeito à licença e avisos do repositório original. O modelo SDXS tem licença própria OpenRAIL++; verificar os termos do modelo antes de redistribuição.
