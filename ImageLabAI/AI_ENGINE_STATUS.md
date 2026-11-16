# Motor de IA local — decisão técnica

## Requisitos do projeto
1. Processar texto e imagem no próprio telefone.
2. Não enviar fotos ou prompts a serviços externos.
3. Escolher o menor modelo viável e reduzir resolução/etapas quando necessário.
4. Não iniciar inferência se a memória livre estiver abaixo do limite de segurança definido após testes.
5. Preservar a aparência do personagem tanto quanto o modelo permitir; isso não pode ser garantido em toda edição.

## Candidatos investigados
- **MediaPipe Image Generator:** biblioteca Android oficial, mas o Google recomenda pelo menos 8 GB de RAM e descreve a tarefa como experimental. Não é uma escolha segura como padrão para um telefone de entrada.
- **MobileDiffusion:** pesquisa do Google com 520 milhões de parâmetros; o artigo relata otimização para dispositivos móveis, mas isso por si só não fornece um pacote pronto e testado para este projeto.
- **SDXL:** não será o modelo padrão. O usuário já observou que o SDAI fecha durante a geração com SDXL.

## Próximo teste
Investigar uma implementação local de Stable Diffusion 1.5 em CPU/GPU com modelo quantizado e medir:
- RAM livre antes e durante a geração;
- tamanho real dos arquivos do modelo;
- tempo de geração em baixa resolução;
- estabilidade sem outros aplicativos abertos;
- qualidade da imagem em comparação com o consumo de memória.

## Estado
O app desta pasta ainda não contém o runtime nem os pesos do modelo. O botão de geração informa isso explicitamente. Nenhuma geração por IA é anunciada como funcional até haver teste real no aparelho-alvo.
