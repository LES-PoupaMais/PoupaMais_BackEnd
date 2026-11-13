# Coleções da API (Bruno e Postman)

Todos os endpoints da API do PoupaMais (36 requisições em 8 pastas), prontos para importar no **Bruno** ou no **Postman**.
Cada requisição traz o corpo de exemplo, o teste do status esperado e uma descrição curta.

| Pasta | O que tem |
| --- | --- |
| `bruno/` | Coleção nativa do Bruno (arquivos `.bru`) + ambiente `Local` |
| `postman/PoupaMais.postman_collection.json` | Coleção Postman v2.1 (também pode ser importada no Bruno) |
| `exemplos/` | `lancamentos-exemplo.csv` (com uma linha inválida, para ver o resumo da importação) e `foto-exemplo.png` |
| `gerar_colecoes.py` | Gera as duas coleções a partir de uma lista única. **Não edite os arquivos gerados à mão** |

## Antes de começar

Suba a API: `docker compose up -d --build` (API em http://localhost:8080, e-mails no Mailpit em http://localhost:8025).

## Bruno

1. **Open Collection** → escolha a pasta `doc/api/bruno`.
2. No canto superior direito, selecione o ambiente **Local**.
3. Rode na ordem (ou clique com o botão direito na coleção → **Run**).

## Postman

1. **Import** → `doc/api/postman/PoupaMais.postman_collection.json`.
2. As variáveis ficam na própria coleção (aba **Variables**); não precisa de ambiente.
3. Em **Enviar foto** e **Importar CSV**, selecione o arquivo em `doc/api/exemplos/` no campo do body (o Postman não guarda o caminho).
4. Para rodar tudo: botão direito na coleção → **Run collection**. Nos downloads (exportar, modelo, foto), use **Send and Download**.

## Como a coleção funciona

- **Ordem importa:** `Cadastro` → `Login` (guarda o `token`) → as demais usam `Authorization: Bearer {{token}}` herdado da coleção.
  `Criar categoria` e `Criar lançamento` guardam `categoriaId` e `lancamentoId` para as requisições seguintes.
- **Rodar de novo:** o `Cadastro` aceita `201` ou `409` (e-mail já cadastrado). `Trocar senha` inverte `senha` e `novaSenha` sozinho,
  para a coleção continuar funcionando.
- **Recuperação de senha (manual):** `Pedir link de redefinição` manda o e-mail ao Mailpit (http://localhost:8025). Copie o `token` do
  link para a variável `tokenRedefinicao` e rode `Redefinir senha`. Sem isso a resposta é `422`, o esperado ao rodar tudo de uma vez.
- **`Excluir minha conta` fica por último:** apaga o usuário de teste e todos os dados dele. Depois, o `Cadastro` funciona de novo.
- Também há casos de erro de propósito: login com senha errada (`401`), lançamento inválido (`400` com `erros[]`) e exclusão de
  categoria em uso (`422`).
- Variáveis padrão: `baseUrl` `http://localhost:8080`, usuário `teste.colecao@poupamais.local` / `senha123`, `mes` `11`, `ano` `2026`.

## Mudou um endpoint?

Ajuste a lista `REQUISICOES` em `gerar_colecoes.py` e rode:

```bash
python3 doc/api/gerar_colecoes.py
```

Para conferir pela linha de comando (com a API no ar):

```bash
npx newman run doc/api/postman/PoupaMais.postman_collection.json --working-dir doc/api
(cd doc/api/bruno && npx @usebruno/cli run --env Local)
```
