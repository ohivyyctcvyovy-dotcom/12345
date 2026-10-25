# Aurora — API gratuita e segura

Este projeto usa um Cloudflare Worker como intermediário seguro entre o site Aurora e o OpenRouter. A chave do OpenRouter fica como **secret no servidor**; nunca a coloque em `app.js`, `index.html` ou no GitHub.

## Custos e limites

- Cloudflare Workers Free: cota publicada de até 100.000 requisições por dia, sujeita a limites e alterações do fornecedor.
- OpenRouter: use o roteador/modelos gratuitos disponíveis na sua conta. Os modelos gratuitos têm limites, atualmente até 50 requisições por dia e 20 por minuto segundo a página de preços.
- Não existe garantia de IA gratuita ou ilimitada. Modelos e cotas podem mudar.
- O Worker não pode impedir todos os abusos se o URL público for compartilhado. Não use esta configuração para distribuir uma API pública a muitas pessoas sem autenticação/rate limiting adicional.

## Publicar o Worker (uma vez)

1. Entre em https://dash.cloudflare.com/ e crie/entre na conta gratuita.
2. Abra **Workers & Pages** e crie um Worker chamado `aurora-assistente-api`.
3. Copie o conteúdo de `worker.js` para o editor do Worker e publique.
4. No Worker, abra **Settings → Variables and Secrets** (o nome pode variar).
5. Adicione um secret chamado exatamente `OPENROUTER_API_KEY`. Cole a chave criada em https://openrouter.ai/keys. Não a envie no chat e não a coloque no GitHub.
6. Salve e publique novamente, se o painel pedir.
7. Copie o endereço público do Worker, por exemplo `https://aurora-assistente-api.SEU-SUBDOMINIO.workers.dev`.
8. Abra a Aurora no celular → Configurações → campo **Endereço da API Aurora**. Cole o endereço do Worker e salve.
9. Teste abrindo `SEU-ENDERECO/health`. Deve aparecer um JSON com `"ok": true`. Depois envie uma pergunta geral na Aurora.

## Segurança e limitações

- O Worker aceita apenas a origem pública do site Aurora e endereços locais de desenvolvimento.
- A chave é lida de `env.OPENROUTER_API_KEY`, como secret do Worker.
- O endpoint `/chat` recebe as mensagens e devolve `reply`.
- O Worker usa o modelo roteador gratuito `openrouter/free`; a disponibilidade depende do catálogo do OpenRouter.
- Se receber erro de cota, aguarde a renovação ou selecione outro modelo gratuito no catálogo e ajuste `worker.js`.
- O endpoint gratuito é público. A lista de origens reduz chamadas de sites de terceiros, mas não substitui autenticação contra scripts/bots que imitem cabeçalhos.

## Arquivos

- `worker.js`: API serverless.
- `wrangler.toml`: configuração para deploy com Wrangler.
- `API_GRATUITA.md`: estas instruções.
