# Aurora — Assistente pessoal para Android

Projeto gratuito em formato PWA (aplicativo web instalável), em português.

## O que já funciona
- Interface adaptada para celular e instalação na tela inicial quando hospedada por HTTPS.
- Reconhecimento de voz pelo navegador (quando suportado, normalmente Google Chrome no Android).
- Leitura das respostas em voz alta pela síntese de voz do aparelho.
- Memórias, tarefas e histórico salvos localmente no navegador.
- Comandos locais em português: guardar/listar memórias, adicionar/listar/concluir tarefas, data e hora, ajuda e limpar conversa.
- Cache básico para abrir a interface offline depois da primeira visita.

## Limite importante
Esta versão **não inclui um modelo de linguagem grande** e não é equivalente ao ChatGPT para perguntas livres. As respostas gerais são limitadas a comandos locais. Uma conexão real com um modelo de IA exige um provedor compatível e pode ter limites de uso; a disponibilidade de uma cota gratuita pode mudar. Não coloque uma chave secreta de API diretamente em `app.js` ou em um site público.

## Como usar no Android sem pagar
### Opção recomendada: publicar gratuitamente no GitHub Pages
1. Extraia o ZIP no celular ou computador.
2. Entre em `https://github.com` e crie uma conta, se ainda não tiver.
3. Crie um repositório público chamado `aurora-assistente`.
4. Envie todos os arquivos e pastas deste projeto para a raiz do repositório (incluindo `index.html`, `app.js`, `style.css`, `manifest.webmanifest`, `sw.js` e `icons/`).
5. Nas configurações do repositório, abra **Pages**.
6. Em **Build and deployment**, escolha **Deploy from a branch**, selecione `main` e `/ (root)`, e salve.
7. Aguarde a publicação e abra a URL do Pages que o GitHub mostrar.
8. No Android, abra essa URL no Chrome. Use o menu ⋮ e escolha **Adicionar à tela inicial** ou **Instalar app**.
9. Permita o microfone quando o Chrome perguntar. A leitura em voz alta usa as vozes configuradas no Android.

## Testar no computador
Para testar o PWA e o service worker localmente, use um servidor HTTP (por exemplo, a extensão Live Server do VS Code). Abrir o `index.html` diretamente como `file://` não ativa todos os recursos de instalação/offline.

## Privacidade e dados
Os dados são guardados no `localStorage` do navegador neste dispositivo. Não são enviados para um servidor por este projeto. Limpar os dados do site/navegador pode apagá-los. O reconhecimento de voz do navegador pode depender de serviços do próprio navegador e da conexão à internet.

## Estrutura
- `index.html` — interface
- `style.css` — visual e layout responsivo
- `app.js` — conversa local, voz, memória, tarefas e configurações
- `manifest.webmanifest` — metadados do aplicativo instalável
- `sw.js` — cache básico offline
- `icons/` — ícones do aplicativo

## Próximas melhorias possíveis
- Conectar um modelo de linguagem por um backend seguro.
- Notificações de lembrete com permissões explícitas.
- Exportar/importar memórias e tarefas.
- Empacotar como APK nativo usando Capacitor ou uma ferramenta Android.
