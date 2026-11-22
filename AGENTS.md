# PoupaMais — BackEnd (API REST)

Guia para qualquer agente de IA (Claude Code, Codex, opencode, Cursor…) e para quem programa. É curto de propósito: ele entra
no contexto em toda sessão. O detalhe fica em `doc/`, para ler **só quando a tarefa pedir** (índice abaixo).

Aplicação web de gestão financeira pessoal e familiar (projeto de LES — Fatec Guarulhos, orientação Prof. Jadir Custódio Júnior).
O usuário registra **receitas e despesas** (lançamentos), organiza por **categorias**, importa/exporta em lote via **CSV/Excel**
e acompanha um **Dashboard** mensal. Foco: letramento financeiro e prevenção do superendividamento (ODS 1 e 8).

Este repositório é **só a API**. O front (Next.js) fica em `LES-PoupaMais/PoupaMais_FrontEnd` e chama esta API **pelo servidor do Next**
(BFF), não direto do navegador.
Documentação de origem (fora do repo, na pasta do workspace `../`): `../Documentação/DVP_PoupaMais_v1.md` (requisitos, UCs, RNs),
`../Banco de Dados/poupamais_modelo_fisico.sql` (DDL oficial), `../Gestão (Trello)/` (backlog exportado).

**Fonte da verdade:** este guia e os arquivos de `doc/`. Os `.md` do DVP em `../Documentação/md/` são uma versão anterior; se
divergirem, vale o que está aqui. Escopo atual do backend: concluído e no ar (Render + Neon + Brevo); estado e entregas em
`doc/historico.md`.

## Onde está cada coisa (leia sob demanda)

| Arquivo | Quando ler |
| --- | --- |
| `doc/contrato-api.md` | Qualquer mudança em endpoint, DTO, status ou erro (tabela "Padrão de erro"). **Fonte única do contrato** |
| `doc/arquitetura.md` | Módulos, estrutura de pastas, APIs públicas já prontas (use; não recrie), decisões de `lancamento`/`dashboard`, patterns |
| `doc/regras-de-negocio.md` | RN001–RN016, segurança e isolamento por usuário |
| `doc/banco-de-dados.md` | Migração, entidade, tipos, constraints, tabela associativa |
| `doc/formato-csv.md` | Importação e exportação (CSV/XLSX) |
| `doc/testes-e-pipeline.md` | Níveis de teste, banco de teste, cobertura, CI |
| `doc/historico.md` | Estado atual, entregas e pendências (débito técnico) |
| `doc/decisoes/` | Por que algo foi feito de um jeito (decisões, pesquisas, alternativas descartadas) |
| `doc/api/` | Coleções Bruno/Postman (geradas por `doc/api/gerar_colecoes.py`) |
| `doc/IDEIAS_FUTURAS.md` | Antes de propor funcionalidade nova |

## Stack

- Build com Maven **via wrapper** (`./mvnw`); não há `mvn` instalado globalmente. Java 21 (a máquina tem JDK 22 e compila para 21).
- Lombok só `@Getter/@Setter/@RequiredArgsConstructor/@Builder`; **sem `@Data` em entidade**. Versões e dependências: `pom.xml`.

## Comandos

```bash
docker compose up -d --build       # tudo no Docker: Postgres + Mailpit + API (http://localhost:8080, Flyway aplica o schema)
docker compose up -d db mailpit    # só a infra: Postgres + Mailpit (e-mails em http://localhost:8025)
./mvnw spring-boot:run             # API em http://localhost:8080 · Swagger em /swagger-ui.html (Flyway aplica as migrações)
./mvnw test                        # testes unitários e web (integração é pulada sem POUPAMAIS_TEST_DB_URL)
./mvnw verify                      # tudo o que o CI roda: testes + ModularidadeTest + piso do JaCoCo
./mvnw spotless:apply              # formata o código: rode antes de commitar
./mvnw clean package -DskipTests   # gera o jar
docker compose down -v             # derruba e APAGA o volume (o Flyway recria o schema na próxima subida)
```

## Arquitetura em uma linha

Monólito modular (Spring Modulith) com MVC em camadas em cada módulo: `endpoints` → `service` → `model`. Só a pasta `publico/`
de um módulo é visível aos outros (Facade + records/enums/exceções); o `ModularidadeTest` quebra o build se isso for violado.
Detalhes em `doc/arquitetura.md`.

### O que é proibido (evita código macarrônico)

- Regra de negócio em controller, ou controller chamando repository.
- Entidade JPA em request/response.
- Um módulo importando `endpoints/`, `service/` ou `model/` de outro (só `publico/` é permitido). O teste do Modulith falha.
- `double`/`float` para dinheiro (usar `BigDecimal`), `@Autowired` em campo, `try/catch` que engole exceção.
- Service "deus": se passar de ~200 linhas ou misturar assuntos, extraia (ex.: `RateioCategorias`, `MensagemSaldoFactory`).
- `id_usuario` vindo do body/query: vem **sempre** do `UsuarioLogado` (token).

## Convenções

- Código, classes, endpoints e mensagens em **português** (domínio do projeto); sem acento em identificadores.
- Nomes: `XxxResource` (controller), `XxxService` + `XxxServiceImpl`, `XxxRepository`, `XxxRequest`/`XxxResponse` (records), `XxxMapper`, `XxxFacade`.
- Validação de formato com Bean Validation nos DTOs (`@NotBlank`, `@Positive`, `@NotEmpty`, `@Size`…);
  regra de negócio no service, lançando as exceções de `shared.exception`.
- `@Transactional` nos métodos de escrita do service; `@Transactional(readOnly = true)` nas consultas.
- Dinheiro com `BigDecimal` e `RoundingMode` sempre explícito.
- Datas de auditoria: `dataCadastro` em `@PrePersist`; `dataAtualizacao` em todo update e troca de status; no lançamento, com hora
  (`OffsetDateTime` UTC) e `dataExclusao` ao ir para a lixeira.
- Toda funcionalidade nasce **com testes** (ver `doc/testes-e-pipeline.md`). Sem teste, não está pronta.
- **Todo endpoint novo ou alterado entra por padrão nas coleções do Postman e do Bruno** (`doc/api/`), no mesmo PR: acrescente a
  requisição (corpo de exemplo, status esperado e, se criar recurso, o script que guarda o id) na lista `REQUISICOES` de
  `doc/api/gerar_colecoes.py` e rode `python3 doc/api/gerar_colecoes.py`. Nunca edite os arquivos gerados à mão.
  O `ColecaoApiTest` compara os controllers com as duas coleções e falha se faltar endpoint ou sobrar um que não existe mais.
- Git: **Gitflow**. Funcionalidade nova só entra assim: branch `feat/<card>` criada da `development` → PR para a **`development`** →
  depois PR de `development` para a **`main`** (release). Nunca PR de `feat/*` direto para a `main`, nem commit direto em `development`/`main`.
  Conventional Commits (`feat(lancamento): ...`, `fix(dashboard): ...`).

## Definição de pronto

Nenhuma tarefa é dada como finalizada sem rodar o passo a passo **completo** da pipeline local (`spotless:check`, `db-test` no ar,
`verify` com os testes de integração rodando, `docker compose config -q`) com tudo verde.
Mexeu em endpoint? As coleções de `doc/api/` foram atualizadas no mesmo PR (o `verify` roda o `ColecaoApiTest`), e o
`doc/contrato-api.md` também (e o resumo do front, `doc/contrato-api.md` do PoupaMais_FrontEnd).
Se algum passo não rodou (Docker parado, rede, teste pulado), a tarefa **não está pronta**: diga exatamente o que faltou.

**Débito técnico:** cada dev faz só a sua entrega. O que achar fora do escopo dela não corrige: registra como pendência em
`doc/historico.md` (linha nova na tabela ou na coluna "Inclui" da entrega que vai tratar daquilo), para quem pegar a próxima
tarefa. Evite acumular muito.

## Memória do projeto (vale para qualquer IA)

- **O que não virou código também se registra.** Decisão tomada, pesquisa feita, alternativa descartada e o motivo: um arquivo em
  `doc/decisoes/` (modelo em `doc/decisoes/README.md`), no mesmo PR. Armadilha nova (erro estranho, configuração que enganou)
  vai para o `doc/` do assunto.
- **Troca de agente ou sessão longa:** antes de encerrar, escreva `HANDOFF.md` na raiz (estado, decisões, próximos passos,
  dúvidas abertas). Quem continua lê e apaga. O arquivo não vai para o Git (`.gitignore`).
- Este guia fica curto: regra nova entra aqui só se valer para toda tarefa; o resto vai para `doc/`.

## Direção futura (ideias a discutir)

Leia `doc/IDEIAS_FUTURAS.md` antes de propor funcionalidade nova: são ideias em aberto, levantadas com base em projetos acadêmicos
antigos, para a equipe discutir. Primeiro o back é concluído com o escopo atual; se o escopo crescer, os requisitos são reescritos
antes. Toda regra nova vira service no módulo certo, com Facade, testes e contrato. Nada disso é para implementar sem decisão da equipe.

## Pontos em aberto (decidir com a equipe antes de implementar)

Nenhum no momento.
