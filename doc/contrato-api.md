# Contrato da API (fonte única)

> Fonte da verdade do contrato entre back e front. O `doc/contrato-api.md` do front é o resumo do lado dele: mudou aqui, atualize lá no mesmo PR. Índice dos guias no `AGENTS.md`.

## Contrato da API (combinado com o front: manter o resumo do front em sincronia)

Prefixo `/api`. JSON em camelCase. Header `Authorization: Bearer <token>`.

**Coleções Bruno/Postman** com todos os endpoints: `doc/api/` (ver `doc/api/README.md`). **Todo endpoint novo entra nas duas
coleções por padrão**, no mesmo PR (ver "Convenções"); o `ColecaoApiTest` reprova o build se faltar algum.

**Saúde** (actuator, fora do `/api`)

- `GET /actuator/health` **público** (sem token) → `200` `{"status":"UP"}` · banco fora do ar → `503` `{"status":"DOWN"}`. Sem detalhes
  nem componentes. O e-mail não entra na conta (`management.health.mail.enabled=false`: falha de envio é tolerada, RN014). É o que o
  `HEALTHCHECK` do `Dockerfile` consulta (`wget` do BusyBox da imagem). Não segue o `ErroResponse` (formato do actuator).

**Auth / usuário** (módulo `usuario`)

- `POST /api/auth/cadastro` `{nome, email, senha, aceiteTermos: true}` → `201` `{id, nome, email}` · e-mail duplicado → `409` · sem aceite → `400` (RF009, RF015)
- `POST /api/auth/login` `{email, senha}` → `200` `{token, expiraEm, usuario: {id, nome, email}}` · inválido → `401` (RF010)
- `POST /api/auth/recuperar-senha` `{email}` → `202` **sempre** (RF012, RN014)
- `POST /api/auth/redefinir-senha` `{token, novaSenha}` → `204` · token inválido, expirado ou usado → `422`

Detalhes do `usuario` (decididos):
- Cadastro: `nome` trim, 1–150; `email` trim + minúsculas, formato válido, ≤ 100; `senha` 6–72 caracteres (limite do BCrypt);
  `aceiteTermos` deve ser `true` (`false`/ausente → `400` com `erros[]` no campo `aceiteTermos`). Cria as 11 categorias padrão
  (`CategoriaFacade.criarPadroes`) **na mesma transação**. Corrida no e-mail único → `409`.
- Login: e-mail comparado em minúsculas; e-mail inexistente ou senha errada → `401` com a **mesma** mensagem
  ("E-mail ou senha inválidos"). `expiraEm` vem do `JwtService.gerar`.
- Recuperação: link = `${poupamais.front-url}/redefinir-senha?token=<token>`; token = 32 bytes aleatórios (`SecureRandom`) em Base64URL;
  grava só o SHA-256 (hex); 30 min; uso único; novo pedido marca os anteriores como usados. Falha de e-mail não muda a resposta (`202`).
- Redefinir/trocar senha: grava `senha_alterada_em = now()` (truncado em segundos). A troca devolve token novo, emitido **depois** dessa data.
- Foto: tipo detectado pelos **bytes** (assinaturas JPEG `FF D8 FF`, PNG `89 50 4E 47`, WEBP `RIFF....WEBP`), ≤ 2 MB; senão `422`.
- Decididos na implementação (feat/usuario):
  - Mensagens: e-mail duplicado "Este e-mail já está cadastrado"; token "Link de redefinição inválido ou expirado. Solicite um novo.";
    troca "A senha atual está incorreta" / "A nova senha deve ser diferente da atual"; exclusão "Senha incorreta";
    foto "A foto deve ser uma imagem JPG, PNG ou WEBP" / "A foto deve ter no máximo 2 MB" / sem foto "Foto de perfil não encontrada".
  - `recuperar-senha` com e-mail vazio ou em formato inválido → `400` (validação de formato, não revela nada); qualquer e-mail
    bem formado → `202` sem corpo. Falha de SMTP só gera `warn` sem e-mail/link/token; o token fica gravado (o usuário pede de novo).
  - Redefinir: valida a nova senha **antes** de gastar o token; ao redefinir, marca como usados todos os tokens pendentes do usuário.
  - `PUT /usuarios/me/foto` sem o campo `foto` → `422` (tipo inválido). `DELETE` da foto sem foto → `204` (idempotente).
  - Token válido de usuário que sumiu entre o filtro e o service → `401` (mesma mensagem de sessão expirada).
  - `PUT /usuarios/me` ignora `email` no body (e-mail não editável).

**Perfil e privacidade** (módulo `usuario`, RF013–RF017)

- `GET /api/usuarios/me` → `{id, nome, email, possuiFoto, dataCadastro}`
- `PUT /api/usuarios/me` `{nome}` → `200` (perfil)
- `PUT /api/usuarios/me/senha` `{senhaAtual, novaSenha}` → `200 {token, expiraEm}` · senha atual errada → `422` (não 401, para não derrubar a sessão)
- `GET /api/usuarios/me/foto` → bytes da imagem (`Content-Type` correto, `Cache-Control: no-cache`) · sem foto → `404`
- `PUT /api/usuarios/me/foto` (multipart, campo `foto`) → `204` · tipo/tamanho inválido → `422`
- `DELETE /api/usuarios/me/foto` → `204`
- `GET /api/usuarios/me/dados` → arquivo `meus-dados-poupamais.json` (RF016, RN016; estrutura abaixo)
- `POST /api/usuarios/me/excluir` `{senha}` → `204` (RF017) · senha errada → `422`. É POST porque DELETE com body não é confiável em proxies.

Rotas públicas: `cadastro`, `login`, `recuperar-senha`, `redefinir-senha` (e `GET /actuator/health`).

Exportação de dados do titular (`GET /api/usuarios/me/dados`, decidido em `feat/exportacao-dados`):
- `200` com `Content-Type: application/json` e `Content-Disposition: attachment; filename="meus-dados-poupamais.json"`; sem token → `401`.
- camelCase; datas `LocalDate` como `"AAAA-MM-DD"` e instantes ISO-8601 em UTC (`"2026-11-12T18:00:00Z"`); `geradoEm` truncado em segundos.
  Nos lançamentos, `data` é `"AAAA-MM-DD"` e `dataCadastro`/`dataAtualizacao`/`dataExclusao` são instantes.
- `perfil` traz **só** `id, nome, email, dataCadastro, aceiteTermosEm, possuiFoto`: nunca senha/hash, `senhaAlteradaEm` nem tokens.
  **A foto não vai no JSON** (só `possuiFoto`): imagem em Base64 incharia o arquivo e ela já pode ser baixada em `GET /api/usuarios/me/foto`.
- `categorias`: **todas** do usuário (ativas e inativas), ativas primeiro e depois alfabética (`CategoriaFacade.listarTodas`).
- `lancamentos`: **todos** os status (ATIVO, INATIVO e DELETADO), ordem data/id desc (`LancamentoFacade.listarTodos`), mesmo
  formato da response de lançamento (`categorias: [{id, descricao}]`). Usuário sem lançamentos → `[]` (não é erro, ao contrário do RN013).
- Só leitura e agregação: o `ExportacaoDadosServiceImpl` devolve direto o `DadosTitularResponse` (sem mapper), como o dashboard.

```json
{
  "geradoEm": "2026-11-12T18:00:00Z",
  "perfil": {
    "id": 7,
    "nome": "Ana",
    "email": "ana@email.com",
    "dataCadastro": "2026-11-01",
    "aceiteTermosEm": "2026-11-01T10:00:00Z",
    "possuiFoto": false
  },
  "categorias": [{ "id": 3, "descricao": "Lazer", "ativa": true }],
  "lancamentos": [
    {
      "id": 9,
      "descricao": "Cinema",
      "tipo": "DESPESA",
      "data": "2026-08-02",
      "valor": 45.9,
      "status": "DELETADO",
      "categorias": [{ "id": 3, "descricao": "Lazer" }],
      "dataCadastro": "2026-08-02T20:00:00Z",
      "dataAtualizacao": "2026-08-03T09:30:00Z",
      "dataExclusao": "2026-08-03T09:30:00Z"
    }
  ]
}
```

**Categorias** (módulo `categoria`, RF011 / UC009). Não há tela própria no front: criar e editar são feitos por modais abertos
no dropdown de categorias do formulário de lançamento. Por isso o `POST` devolve a categoria criada, que o front já seleciona.

- `GET /api/categorias?ativa=true|false&mes=&ano=` (sem `ativa` = todas) → `200 [{id, descricao, ativa, quantidadeLancamentos}]`.
  `quantidadeLancamentos` = lançamentos **ATIVO e INATIVO** do usuário com essa categoria cuja `data` cai no mês `mes`/`ano`
  (do dia 1 ao último dia; DELETADO e outros meses não contam; sem lançamento → `0`; lançamento com 2 categorias conta nas duas).
  `mes`/`ano` opcionais e com a mesma validação do dashboard: o que faltar vem de `Periodo.atual()` (fuso de Brasília); fora de
  1–12 / 2000–2100 ou não numérico → `400` "Dados inválidos" com `erros[{campo, mensagem}]`. Eles **não filtram** quais categorias
  vêm (só o `ativa` filtra) nem mudam a ordenação. Uma única consulta agregada (`CategoriaRepository.contarLancamentosPorCategoria`,
  nativa com `group by`, sem N+1). `POST`/`PUT`/`PATCH` continuam devolvendo `{id, descricao, ativa}` (sem a contagem).
  ```json
  [{ "id": 3, "descricao": "Lazer", "ativa": true, "quantidadeLancamentos": 4 },
   { "id": 8, "descricao": "Viagem", "ativa": false, "quantidadeLancamentos": 0 }]
  ```
- `POST /api/categorias` `{descricao}` → `201 {id, descricao, ativa}` · duplicada (case-insensitive) → `409`
- `PUT /api/categorias/{id}` `{descricao}`
- `PATCH /api/categorias/{id}/status` `{ativa: boolean}`
- `DELETE /api/categorias/{id}` → `204`; se tiver lançamentos → `422` "Categoria vinculada a lançamentos, apenas inative"
- Ordenação do `GET`: alfabética sem diferenciar maiúsculas; sem filtro, **ativas primeiro**. `PUT`/`PATCH` → `200 {id, descricao, ativa}`.
  Descrição: trim, obrigatória, ≤ 255 (`400` no DTO; `422` se chegar ao service); duplicada no `PUT` → `409`; de outro usuário → `404`.
- `CategoriaFacade` (implementada por `CategoriaServiceImpl`): `buscarPorIds(idUsuario, ids)` (ignora ids alheios/inexistentes, 1 consulta),
  `listarTodas(idUsuario)` (ativas e inativas, mesma ordem do `GET` sem filtro),
  `validarAtivasDoUsuario(idUsuario, ids)` → **`404` "Categoria não encontrada"** se alguma não existir ou for de outro usuário (RN005)
  e **`422`** se alguma estiver inativa, `buscarOuCriar(idUsuario, descricao)` (não reativa inativa), `criarPadroes(idUsuario)`.
- O bloqueio do `DELETE` e a contagem `quantidadeLancamentos` consultam `poupamais.categorias_lancamentos` (e `lancamento`) por
  **query nativa** no `CategoriaRepository` (o módulo `categoria` não pode depender de `lancamento`: criaria ciclo). Corrida no índice único `lower(descricao)` → `409`.

**Lançamentos** (módulo `lancamento`, RF001–RF005, RF008 / UC001, UC002, UC004, UC010)

- `GET /api/lancamentos?descricao=&mes=&ano=&tipo=&status=&categoriaId=&pagina=&tamanho=` → **página** ordenada por
  **data desc, id desc** (o id desempata datas iguais, então a ordem é estável entre páginas).
  `mes`/`ano` filtram pelo mês da `data` (do dia 1 ao último dia).
  `descricao` = contém, sem diferenciar maiúsculas (`lower(...) like`). Categoria/lançamento de outro usuário → `404`.
  Sem `status` = ATIVO + INATIVO (**nunca** DELETADO). `status=ATIVO|INATIVO|DELETADO` filtra só aquele (DELETADO = "Lixeira").
  - Paginação (Q3): `pagina` começa em **0** (padrão 0) e `tamanho` vai de **1 a 100** (padrão **12**). `pagina` < 0 ou
    `tamanho` fora de 1–100 → `400` "Dados inválidos" com `erros[{campo: "pagina", mensagem: "A página deve ser maior ou igual a 0"}]`
    ou `erros[{campo: "tamanho", mensagem: "O tamanho da página deve ser de 1 a 100"}]`. Página além do fim → `200` com `itens: []`
    e o `total` correto. A consulta pagina no banco (`LIMIT/OFFSET` + `count`); as categorias da página vêm numa segunda consulta.
  - Valor que não converte (vale para listagem e exportação): `pagina`/`tamanho`/`mes`/`ano`/`categoriaId` não numérico → `400`
    "Dados inválidos" com `erros[{campo, mensagem: "Informe um número inteiro"}]`; `tipo`/`status` fora do enum →
    `erros[{campo, mensagem: "Valor inválido. Use: RECEITA ou DESPESA"}]` (ou `"... Use: ATIVO, INATIVO ou DELETADO"`).
  - Resposta: `{ "itens": [<lançamento>...], "pagina": 0, "tamanho": 12, "total": 30, "totalPaginas": 3 }` (`itens` no formato
    de lançamento abaixo; `total` = quantos lançamentos o filtro encontra; sem resultado: `total: 0`, `totalPaginas: 0`).
  - A **exportação** (`/exportar`) e os usos internos (`LancamentoFacade.listar`, dashboard, LGPD) **não paginam**: trazem tudo.
- `GET /api/lancamentos/{id}`
- `POST /api/lancamentos` `{descricao, tipo, data, valor, categoriaIds: [..], status?}` → `201`. `status` opcional: `"ATIVO"` ou
  `"INATIVO"` (ausente/`null` = ATIVO), gravado na mesma chamada; `"DELETADO"` → `400` "Dados inválidos" com
  `erros[{campo: "status", mensagem: "Informe o status ATIVO ou INATIVO"}]`; valor fora do enum → `400` "Não foi possível ler os
  dados enviados. Confira o formato." (como o `tipo`). `data` em ISO `"AAAA-MM-DD"` (o front mostra
  dd/mm/aaaa). Data ausente → `400` "A data é obrigatória"; dia que não existe ou formato errado (`"2026-02-31"`, `"05/11/2026"`) → `400`
  "Dados inválidos" com `erros[{campo: "data", mensagem: "Data inválida. Use o formato AAAA-MM-DD com um dia que exista."}]`;
  fora de 01/01/2000–31/12/2100 → `422` "A data deve estar entre 01/01/2000 e 31/12/2100".
- `PUT /api/lancamentos/{id}` (mesmo body) → atualiza e seta `dataAtualizacao` · `status` ausente/`null` = mantém o atual;
  `ATIVO`/`INATIVO` = troca junto; `DELETADO` → `400` (igual ao POST) · lançamento na lixeira → `422` "Lançamento excluído não pode
  ser editado. Restaure-o antes." (mesmo com `status` no corpo)
- `PATCH /api/lancamentos/{id}/status` `{status: "ATIVO"|"INATIVO"}` → atualiza `dataAtualizacao` · se DELETADO → `422`
- `DELETE /api/lancamentos/{id}` → `204`; **não apaga**: muda o status para DELETADO, grava `dataExclusao` e guarda o status que
  tinha (ATIVO/INATIVO)
- `POST /api/lancamentos/{id}/restaurar` → `200` (lançamento de volta ao status de antes do `DELETE` e `dataExclusao: null`; sem
  esse registro, INATIVO) · se não estiver DELETADO → `422` "Só é possível restaurar um lançamento excluído". É o "Desfazer" do front.
- `POST`/`PUT`/`PATCH`/`restaurar` devolvem o lançamento no formato abaixo. `categoriaIds` repetidos são aceitos e gravados uma vez;
  categoria inativa → `422`; inexistente ou de outro usuário → `404` (vem do `validarAtivasDoUsuario`).
- `DELETE` de lançamento já DELETADO → `422`. Toda alteração (PUT, status, delete, restaurar) seta `dataAtualizacao = agora` (com hora).

As transições ficam num método da entidade (`Lancamento.alterarStatus(novo)`, que lança `RegraNegocioException`),
não espalhadas pelo service.

Response de lançamento:

```json
{
  "id": 1,
  "descricao": "Salário mensal",
  "tipo": "RECEITA",
  "data": "2026-08-05",
  "valor": 8500.0,
  "status": "ATIVO",
  "categorias": [{ "id": 3, "descricao": "Salário" }],
  "dataCadastro": "2026-08-05T13:05:00Z",
  "dataAtualizacao": null,
  "dataExclusao": null
}
```

**Importação / exportação** (módulo `arquivo`, RF003, RF007 / UC003, UC006, UC007, UC008). As rotas ficam sob `/api/lancamentos`, mas o controller é do módulo `arquivo`.

- `POST /api/lancamentos/importar` (multipart, campo `arquivo`) → `200`
  `{ totalLinhas, importados, falhas: [{linha, conteudo, motivo}], categoriasCriadas: ["..."] }`
- `GET /api/lancamentos/exportar?formato=csv|xlsx&<mesmos filtros da listagem>` → arquivo com `Content-Disposition: attachment`
  · DELETADO só entra com `status=DELETADO` · sem lançamentos → `422` "Não há dados para exportação"
- `GET /api/lancamentos/template-csv` → baixa o `template.csv` (em `src/main/resources/template.csv`)

**Dashboard** (módulo `dashboard`, RF006 / UC005)

- `GET /api/dashboard?mes=8&ano=2026` (padrão: mês/ano atual; entram os lançamentos cuja `data` cai no mês). `classificacaoTaxa` pode ser `DENTRO_DO_LIMITE`, `ATENCAO`, `ACIMA_DO_LIMITE` ou `null`:

```json
{
  "mes": 8,
  "ano": 2026,
  "receitas": 9700.0,
  "despesas": 4780.0,
  "saldo": 4920.0,
  "taxaGasto": 49.28,
  "classificacaoTaxa": "DENTRO_DO_LIMITE",
  "mensagem": "Saldo positivo de R$ 4.920,00 em Agosto",
  "ultimosMeses": [
    { "mes": 5, "ano": 2026, "receitas": 0, "despesas": 0, "saldo": 0 }
  ],
  "despesasPorCategoria": [{ "categoriaId": 2, "descricao": "Moradia", "valor": 2100.0 }],
  "maioresDespesas": [{ "id": 9, "descricao": "Aluguel", "data": "2026-08-05", "valor": 2100.0, "categorias": ["Moradia"] }],
  "pesoMaioresDespesas": { "valor": 2100.0, "percentual": 43.93 }
}
```

`ultimosMeses` tem sempre 4 itens, do mais antigo ao selecionado; meses sem lançamento vêm com zeros.
`pesoMaioresDespesas` vem sempre: `valor` = soma das `maioresDespesas`; `percentual` = valor / `despesas` × 100 (2 casas, `HALF_UP`),
`null` se o mês não tem despesa ativa (aí `valor` = 0, como a `taxaGasto` sem receita).

**Padrão de erro** (RNF007, mensagens em português prontas para exibir). Desde a **v1.2.0**, **toda** resposta de erro traz
`codigo`: string estável em MAIÚSCULAS_COM_UNDERSCORE, para o front decidir o que fazer sem depender do texto. A `mensagem`
continua sendo o texto pronto para exibir (os textos não mudaram); `erros[]` só vem em `VALIDACAO`.

```json
{
  "status": 400,
  "codigo": "VALIDACAO",
  "mensagem": "Dados inválidos",
  "erros": [{ "campo": "valor", "mensagem": "O valor deve ser maior que zero" }]
}
```

400 validação/parâmetro inválido ou ausente · 401 não autenticado · 403 acesso negado · 404 não encontrado/não pertence ao usuário · 405 método não suportado · 406 `Accept` não atendido · 409 duplicidade · 415 tipo de conteúdo não suportado · 422 regra de negócio · 500 genérico (sem stacktrace).
Rota inexistente (com token válido) → 404 `"Recurso não encontrado"`; método HTTP não suportado → 405 `"Método não permitido para este recurso."`.

Como funciona: os códigos ficam num lugar só, o enum `shared.exception.CodigoErro` (`shared` é módulo aberto, todos usam). Cada
exceção de negócio recebe o seu no construtor; os erros genéricos têm código padrão no `GlobalExceptionHandler`; o 401 do
`ErroAutenticacaoEntryPoint` e o 403 do `ErroAcessoNegadoHandler` (Spring Security) e o `/error` do servlet (`ErroPadraoController`,
para o que não passa pelo MVC: falha num filtro, recusa do servidor como 413) também saem no `ErroResponse` com `codigo`.
`ErroResponse` recusa `codigo` nulo. **Todo erro sai em `application/json`, qualquer que seja o `Accept`** (`ErroResponse.comoResposta()`
e o `resposta(...)` do handler fixam o `Content-Type`): um erro na exportação pedida com `Accept: text/csv` ou
`application/octet-stream` sai com o status e o `codigo` dele (antes virava 406 vazio ou 500). O `406` fica só para a rota que deu certo
mas produz um formato que o cliente não aceita. A tabela também está no Swagger (descrição da API, gerada do enum). Nunca renomeie um código.

| Código | Status | Quando acontece |
| --- | --- | --- |
| `VALIDACAO` | 400 | Bean Validation no corpo/parâmetros (`erros[]`), inclusive data que não existe no corpo (`"2026-02-31"`) e filtro de query que não converte (`?pagina=abc`, `?mes=abc`, `?tipo=XPTO`): o item de `erros[]` diz `"Informe um número inteiro"` (campo numérico), `"Valor inválido. Use: RECEITA ou DESPESA"` (enum, com os valores aceitos) ou `"Valor inválido"`. Antes da entrega 23 esse texto vinha do Spring, em inglês |
| `CORPO_INVALIDO` | 400 | Corpo ilegível: JSON quebrado, valor fora do enum (`tipo`, `status`), tipo errado |
| `FORMATO_EXPORTACAO_INVALIDO` | 400 | `GET /lancamentos/exportar` com `formato` ausente ou diferente de `csv`/`xlsx` |
| `PARAMETRO_INVALIDO` | 400 | Parâmetro de rota, `@RequestParam` ou header com tipo errado (`/api/lancamentos/abc`, `?ativa=talvez`): `"Parâmetro inválido: id. Confira o valor enviado."`, sem `erros[]`. Filtro que é objeto (`mes`/`ano`/`pagina` via `@ModelAttribute`, ex.: `?mes=abc`) continua em `VALIDACAO` com o erro no campo (ver acima). Antes saía 500 |
| `PARAMETRO_AUSENTE` | 400 | `@RequestParam`, part de multipart (`@RequestPart`) ou header obrigatório ausente: `"Parâmetro obrigatório ausente: formato."`. Hoje nenhuma rota tem parâmetro obrigatório (os ausentes têm código próprio, como `ARQUIVO_VAZIO`); fica de defesa. Antes saía 500 |
| `REQUISICAO_INVALIDA` | 400 (e outros 4xx) | Requisição recusada antes do MVC (`/error` com 400 ou um 4xx sem código próprio) |
| `NAO_AUTENTICADO` | 401 | Sem token, token inválido/expirado, sessão derrubada (troca de senha, conta excluída) |
| `CREDENCIAIS_INVALIDAS` | 401 | Login com e-mail inexistente ou senha errada |
| `ACESSO_NEGADO` | 403 | Spring Security negou acesso a usuário autenticado (hoje não há papéis; reservado) |
| `NAO_ENCONTRADO` | 404 | Rota inexistente |
| `CATEGORIA_NAO_ENCONTRADA` | 404 | Categoria inexistente ou de outro usuário (CRUD, `categoriaIds` do lançamento, filtro `categoriaId`) |
| `LANCAMENTO_NAO_ENCONTRADO` | 404 | Lançamento inexistente ou de outro usuário |
| `FOTO_NAO_ENCONTRADA` | 404 | `GET /usuarios/me/foto` sem foto |
| `METODO_NAO_PERMITIDO` | 405 | Método HTTP não suportado na rota |
| `TIPO_RESPOSTA_NAO_ACEITO` | 406 | `Accept` sem nenhum formato que a rota produz (ex.: `Accept: text/csv` num `GET` JSON). O corpo sai em JSON mesmo assim (`Content-Type: application/json` fixado, como em todo erro). Erro de outro tipo com esse `Accept` (ex.: `404`, `422`) sai com o próprio código, não `406`. Antes saía 500 |
| `EMAIL_JA_CADASTRADO` | 409 | Cadastro com e-mail que já existe |
| `CATEGORIA_DUPLICADA` | 409 | `POST`/`PUT` de categoria com descrição que já existe (sem diferenciar maiúsculas) |
| `TIPO_CONTEUDO_NAO_SUPORTADO` | 415 | `Content-Type` não aceito pela rota (ex.: `text/plain` num `POST` JSON). Antes da v1.2.0 saía 500 |
| `ARQUIVO_GRANDE_DEMAIS` | 422 (413 se o servidor recusar antes) | Upload acima de 2 MB: CSV, foto ou limite do multipart |
| `ARQUIVO_VAZIO` | 422 | Importação sem o campo `arquivo`, arquivo vazio ou sem linhas de dados |
| `ARQUIVO_INVALIDO` | 422 | Arquivo que não é texto UTF-8 (ou tem byte nulo) |
| `CABECALHO_INVALIDO` | 422 | CSV sem o cabeçalho ou com colunas obrigatórias faltando |
| `ARQUIVO_LINHAS_DEMAIS` | 422 | CSV com mais de 5.000 linhas de dados |
| `SEM_DADOS_EXPORTACAO` | 422 | Exportação sem lançamentos no filtro |
| `ACEITE_TERMOS_OBRIGATORIO` | 422 | Cadastro sem aceite chegando ao service (na API o DTO barra antes com `400 VALIDACAO`) |
| `DADOS_CONTA_INVALIDOS` | 422 | Nome ou senha fora das regras chegando ao service (na API o DTO costuma barrar antes com `400`) |
| `SENHA_ATUAL_INCORRETA` | 422 | Troca de senha com a senha atual errada |
| `SENHA_IGUAL_A_ATUAL` | 422 | Troca de senha com a nova igual à atual |
| `SENHA_INCORRETA` | 422 | Exclusão de conta com a senha errada |
| `TOKEN_INVALIDO` | 422 | `redefinir-senha` com link inválido, expirado ou já usado |
| `FOTO_TIPO_INVALIDO` | 422 | Foto que não é JPG/PNG/WEBP pelo conteúdo, ou sem o campo `foto` |
| `CATEGORIA_INVALIDA` | 422 | Descrição de categoria vazia ou > 255 chegando ao service |
| `CATEGORIA_INATIVA` | 422 | Categoria inativa usada em lançamento novo/editado |
| `CATEGORIA_VINCULADA` | 422 | `DELETE` de categoria com lançamentos (inclusive DELETADO) |
| `LANCAMENTO_INVALIDO` | 422 | RN001 no `LancamentoValidator` (ex.: data fora de 01/01/2000–31/12/2100, valor arredondado ≤ 0) |
| `LANCAMENTO_NA_LIXEIRA` | 422 | `PUT` ou `PATCH /status` num lançamento DELETADO |
| `LANCAMENTO_JA_EXCLUIDO` | 422 | `DELETE` de lançamento que já está na lixeira |
| `LANCAMENTO_NAO_EXCLUIDO` | 422 | `restaurar` de lançamento que não está na lixeira |
| `STATUS_INVALIDO` | 422 | `PATCH /status` com `DELETADO` ou sem status (no POST/PUT o DTO barra antes com `400 VALIDACAO`) |
| `TRANSICAO_STATUS_INVALIDA` | 422 | Transição recusada pela máquina de estados (defesa: pela API, os casos acima chegam antes) |
| `ERRO_INTERNO` | 500 | Erro inesperado (sem detalhes internos) |

Exemplo (antes da v1.2.0 não havia `codigo`):

```json
{ "status": 422, "codigo": "CATEGORIA_VINCULADA", "mensagem": "Categoria vinculada a lançamentos, apenas inative" }
```
