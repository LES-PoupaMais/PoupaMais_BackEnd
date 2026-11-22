# Histórico: estado e entregas

> Registro do que foi feito e quando (antes no `CLAUDE.md`). Ao terminar uma entrega, atualize a tabela no mesmo PR.

## Estado atual (20/11/2026)

- **Fonte da verdade:** o `AGENTS.md` e os arquivos de `doc/` (até 21/11/2026, o `CLAUDE.md`). Os `.md` do DVP em `../Documentação/md/` são uma versão anterior; se divergirem, vale o guia.
- **18/11/2026:** RN001 revista (lançamento com data completa; auditoria com hora). Ver entrega 15 do "Plano de entregas".
- **19/11/2026:** RN006 revista (`status` no POST/PUT; restaurar volta ao status anterior). Ver entrega 19 do "Plano de entregas".
- **20/11/2026 (contrato v1.2.0, na `development`, release ainda não feita):** toda resposta de erro tem `codigo` estável (tabela em
  "Padrão de erro") e sai sempre em JSON, com qualquer `Accept`; cada item de `maioresDespesas` do dashboard traz `data`; erros do
  cliente que caíam no `500` têm código próprio (`400 PARAMETRO_INVALIDO`/`PARAMETRO_AUSENTE`, `406`); saúde da API em
  `GET /actuator/health`, usada pelo `HEALTHCHECK` do `Dockerfile`; Mailpit com versão fixa. Ver entregas 20 a 22 do "Plano de
  entregas" (PRs #31, #32 e #33, todos integrados).
- **Escopo atual do backend: concluído.** **Deploy (21/11/2026; a decisão de 12/11 de não publicar foi revista):** a API roda no
  Render (https://poupamais-backend.onrender.com, Docker, plano Free, publica a `main` a cada merge), com o banco no Neon e o
  e-mail pelo Brevo (porta 2525); um job do cron-job.org chama `/actuator/health` a cada 10 min para ela não dormir. Variáveis e
  cuidados na seção "Deploy" do README. Localmente continua igual (`docker compose up -d --build`, ou tudo junto pelo
  PoupaMais_Development; ver "Como rodar" e "Demonstração local" no README). Sem o usuário em memória do Spring Security
  (`exclude = UserDetailsServiceAutoConfiguration` no `PoupaMaisApplication`): a API só autentica por JWT, e ele escrevia uma
  senha gerada no log. O front já está integrado a esta API (contrato v1.2.0 espelhado no `doc/contrato-api.md` dele). Próximos passos do projeto:
  ensaio da demo no modo "como produção" (HTTPS) e as releases (back **v1.2.0**), feitas pelo líder.
- Entregas: Documento/Apresentação final em **12–13/11/2026**; código no GitHub até **26/11/2026**.

## Plano de entregas e pendências

Uma branch `feat/<nome>` por item, saindo da `development`; o dev para no **push da branch**, e o PR é aberto e integrado pela equipe.

| # | Entrega | Situação | Inclui (além do contrato) |
| --- | --- | --- | --- |
| 9 | Deploy | ✅ no ar (21/11/2026; antes fora do escopo, decisão de 12/11 revista) | Render (API, Docker) + Neon (Postgres) + Brevo (SMTP 2525); front na Vercel; passo a passo no README do PoupaMais_Development |
| 15 | Lançamento com data completa + auditoria com hora (Fase A: RN001 revista em 18/11/2026) | ✅ integrada (PR #24, release v1.1.0) | `V2__lancamento_data_completa.sql`; `data` ISO no contrato; `dataCadastro`/`dataAtualizacao`/`dataExclusao` com hora; CSV `descricao;tipo;data;valor;categorias` (dd/mm/aaaa); XLSX com célula de data; LGPD; coleções. Front: espelhar o contrato no `CLAUDE.md` do front |
| 16 | `pesoMaioresDespesas` no dashboard (Q4) | ✅ integrada com a 15 (PR #24, release v1.1.0) | Soma das 5 maiores e % sobre as despesas do mês (2 casas, `HALF_UP`; `null` sem despesa) |
| 17 | Paginação do `GET /lancamentos` (Q3) | ✅ integrada (PR #25, release v1.1.0) | `pagina`/`tamanho` (padrão 12, máx. 100); `{itens,pagina,tamanho,total,totalPaginas}`; exportação e Facade sem paginar |
| 18 | `quantidadeLancamentos` em `GET /categorias?mes&ano` (Q2) | ✅ integrada (PR #26, release v1.1.0) | Query nativa agregada no `CategoriaRepository` (evita ciclo categoria→lancamento) |
| 19 | `status` no POST/PUT de lançamento + restaurar ao status anterior (RN006 revista em 19/11/2026; contrato v1.2.0) | ✅ integrada na `development` (PR #29) | `status` opcional (ATIVO/INATIVO; DELETADO → 400) no `LancamentoRequest`; `V3__lancamento_status_anterior.sql`; `Lancamento.restaurar()`; o `PATCH /status` não tira mais da lixeira (antes, `INATIVO` num DELETADO restaurava); Swagger e coleções. Front: criar/editar com `status` numa chamada e "Desfazer" = `DELETE` imediato + `restaurar`; espelhar o contrato no `CLAUDE.md` do front |
| 20 | Contrato v1.2.0 (junto da 19): `codigo` em toda resposta de erro + `data` em `maioresDespesas` | ✅ integrada na `development` (PR #31) | Enum `CodigoErro` em `shared.exception` (tabela em "Padrão de erro" e no Swagger, com `info.version` 1.2.0); exceções com código no construtor; `GlobalExceptionHandler` com códigos padrão e novos 415 (antes caía no 500) e 403; `ErroAcessoNegadoHandler`, `ErroPadraoController` (`/error`, despacho de erro liberado na segurança); `MaiorDespesa.data` (ordem e peso iguais); `CodigoErroTest` reprova código sem teste; coleções testam o `codigo` dos casos de erro. Front: espelhar o contrato no `CLAUDE.md` do front (não feito nesta branch). Decidido (20/11/2026, líder): a recusa de CORS do Spring (`403` "Invalid CORS request", texto puro) fica como está, sem `ErroResponse` nem `codigo`, porque o navegador não chama a API (BFF). Parâmetro inválido/ausente e `406`: resolvidos na entrega 21 |
| 21 | Erros do cliente que caíam no `500 ERRO_INTERNO` (contrato v1.2.0) | ✅ integrada na `development` (PR #32) | `GlobalExceptionHandler`: `400 PARAMETRO_INVALIDO` (tipo errado em parâmetro de rota, query ou header), `400 PARAMETRO_AUSENTE` (parâmetro, part ou header obrigatório ausente) e `406 TIPO_RESPOSTA_NAO_ACEITO` (corpo em JSON com `Content-Type` fixado); tabela de códigos (aqui e no Swagger, gerada do enum); coleções com `Listar categorias com filtro inválido` (39 requisições). Front: espelhar os 3 códigos no `CLAUDE.md` do front. Pendência: os outros erros com um `Accept` que exclui JSON (ex.: `404` com `Accept: text/csv`) não conseguem escrever o `ErroResponse` e viram falha do servidor sem `codigo`; resolvida na entrega 22 |
| 22 | Erro em JSON com qualquer `Accept` + saúde da API + Mailpit com versão fixa | ✅ integrada na `development` (PR #33) | Todo erro (`GlobalExceptionHandler`, `@ExceptionHandler` do `ArquivoResource`, `ErroPadraoController`) com `Content-Type: application/json` fixado (`ErroResponse.comoResposta()`): erro na exportação com `Accept: text/csv`/`application/octet-stream` sai com status e `codigo` próprios (antes `500`/`406` sem `codigo`). `spring-boot-starter-actuator` só com `GET /actuator/health` público, sem detalhes (`SaudeIntegracaoTest`); `HEALTHCHECK` do `Dockerfile` por HTTP (`wget` do BusyBox, sem instalar nada) no lugar do `nc -z`; coleções com a pasta `00 Saúde` (40 requisições; `ColecaoApiTest` conhece o endpoint do actuator); Mailpit `v1.33.1` no compose. Front: espelhar "Saúde" e a nota do `Accept` no `CLAUDE.md` do front |
| 23 | Pendências das entregas 17 e 18: filtro de query que não converte com mensagem em português + `PeriodoParams` | aguardando PR (`fix/pendencias-finais`) | `GlobalExceptionHandler.mensagemDoCampo`: falha de conversão num `@ModelAttribute` (`?pagina=abc`, `?mes=abc`, `?tipo=XPTO`, também no `/exportar`, no `/categorias` e no `/dashboard`) continua `400 VALIDACAO` com o erro no campo, mas o texto passa a ser "Informe um número inteiro" / "Valor inválido. Use: …" (antes, a mensagem do Spring em inglês, com nome de classe). `shared.endpoints.dto.PeriodoParams` substitui `CategoriaListagemParams` e `DashboardParams` (mesmas mensagens e campos; contrato igual). Tabela de códigos, README e coleções (texto) atualizados. Front: espelhar no `CLAUDE.md` do front os novos textos de `erros[].mensagem` |
| 14 | Pendência (testes): cada `*IntegracaoTest` com `@DynamicPropertySource` próprio sobe um contexto Spring separado, e cada um mantém um pool Hikari de 10 conexões aberto até o fim do `verify` (8 contextos ≈ 80 das 100 do Postgres). Com o `db-test` compartilhado, duas execuções em paralelo deram `FATAL: sorry, too many clients already` (12/11/2026) | ✅ integrada (PR #14) | `src/test/resources/application.properties`: pool Hikari de 3 conexões nos testes |

Ao terminar uma entrega: atualize esta tabela e a seção "APIs públicas já prontas" no mesmo PR.
