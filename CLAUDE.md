# PoupaMais — BackEnd (API REST)

Aplicação web de gestão financeira pessoal e familiar (projeto de LES — Fatec Guarulhos, orientação Prof. Jadir Custódio Júnior).
O usuário registra **receitas e despesas** (lançamentos), organiza por **categorias**, importa/exporta em lote via **CSV/Excel**
e acompanha um **Dashboard** mensal. Foco: letramento financeiro e prevenção do superendividamento (ODS 1 e 8).

Este repositório é **só a API**. O front (Next.js) fica em `LES-PoupaMais/PoupaMais_FrontEnd` e chama esta API **pelo servidor do Next**
(BFF), não direto do navegador.
Documentação de origem (fora do repo, na pasta do workspace `../`): `../Documentação/DVP_PoupaMais_v1.md` (requisitos, UCs, RNs),
`../Banco de Dados/poupamais_modelo_fisico.sql` (DDL oficial), `../Gestão (Trello)/` (backlog exportado).

## Estado atual (12/11/2026)

- **Fonte da verdade:** este `CLAUDE.md`. Os `.md` do DVP em `../Documentação/md/` são uma versão anterior; se divergirem, vale este arquivo.
- **Pronto na `development`:**
  - Fundação: Spring Boot 4.1.2, Spring Modulith 2.1.2, `docker-compose.yml` (db, db-test, mailpit, api), Flyway (`db/migration`, 6 tabelas), `Dockerfile`, JaCoCo, Spotless.
  - `shared`: exceções + `GlobalExceptionHandler`, `Periodo`, `WebConfiguration` e **segurança JWT completa** (`shared.security`).
  - `notificacao`: `EmailFacade` (e-mail de recuperação de senha via SMTP).
  - `categoria`: CRUD completo + `CategoriaFacade`.
- **`usuario`:** auth, perfil, foto, troca/recuperação de senha, exclusão de conta; `SenhaAlteradaConsulta` implementada
  (filtro recusa token de usuário excluído) e Swagger por `SWAGGER_HABILITADO`.
- **`lancamento`:** CRUD, filtros (Specifications), status/lixeira e `LancamentoFacade` (ver "APIs públicas já prontas").
- **`dashboard`** (✅ na `development`): `GET /api/dashboard` completo (RN003, RN008–RN010); ver "Decisões do módulo `dashboard`".
- **`arquivo`** (✅ na `development`): importação CSV, exportação CSV/XLSX (Strategy + Factory) e template; decisões em "Formato CSV".
- **Exportação de dados do titular** (RF016, ✅ na `development`): `GET /api/usuarios/me/dados`; estrutura do JSON no contrato "Perfil e privacidade".
- **Escopo atual do backend: concluído.** Não há deploy: a apresentação e a entrega mostram o sistema **rodando localmente**
  (`docker compose up -d --build`; ver "Demonstração local" no README). Próximo passo do projeto: o front (design novo + telas).
- Entregas: Documento/Apresentação final em **12–13/11/2026**; código no GitHub até **26/11/2026**.
- Cards do Trello (Backlog) que são deste repo, em ordem sugerida:
  1. **Configurar Infraestrutura Local** — `docker-compose.yml` + Flyway (schema aplicado ao subir a API).
  2. **Camada de Domínio** — entidades JPA, enums, repositories, exceptions + handler global (módulo `shared` + `model` de cada módulo).
  3. **Serviços e Segurança** — services (interface + `impl`), `JwtService`, `SecurityConfiguration`, `WebConfiguration`.
  4. **Camada de API** — DTOs, mappers, resources (cadastro, login, CRUD de lançamentos e categorias, dashboard).
  5. **Importação e Exportação em lote** — módulo `arquivo`.
  6. **Template CSV** (`template.csv`) — ver "Formato CSV".
  7. ~~**Deploy**~~ — fora do escopo (decisão de 12/11/2026): demonstração local com Docker.

## Stack

| Item       | Escolha                                                                                                              |
| ---------- | -------------------------------------------------------------------------------------------------------------------- |
| Linguagem  | Java 21 (a máquina tem JDK 22 e compila para 21 sem problema)                                                         |
| Framework  | **Spring Boot 4.1.2** (Web MVC, Data JPA, Validation, Security, Mail) + **Spring Modulith 2.1.2** (só o núcleo, sem registro de eventos) |
| Build      | Maven **via wrapper** (`./mvnw`); não há `mvn` instalado globalmente                                                 |
| Banco      | PostgreSQL (schema `poupamais`)                                                                                      |
| Auth       | JWT stateless (`io.jsonwebtoken:jjwt-*`) + `BCryptPasswordEncoder`                                                   |
| Arquivos   | Apache Commons CSV (CSV) e Apache POI `poi-ooxml` (`.xlsx`)                                                          |
| E-mail     | `spring-boot-starter-mail` (SMTP). Local: **Mailpit** no docker-compose (UI em http://localhost:8025, SMTP na porta 1025) |
| Extras     | Lombok (só `@Getter/@Setter/@RequiredArgsConstructor/@Builder`; **sem `@Data` em entidade**), springdoc-openapi 3.2 (Swagger), jjwt 0.12.7, POI 5.5.1, Commons CSV 1.14.1 |
| Qualidade  | Spring Modulith `verify()` em teste, Spotless (google-java-format), JUnit 5 + Mockito + AssertJ, JaCoCo (piso de cobertura) |
| Infra      | Docker / docker-compose                                                                                              |

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

## Arquitetura: monólito modular + MVC em camadas

**Um único deploy (monólito)**, dividido em **módulos de negócio** com fronteiras explícitas.
Dentro de cada módulo, **MVC em camadas**: `api` (Controller + DTO = a "View" em JSON) → `service` (regras) → `model` (entidades + repositories).
As fronteiras são verificadas pelo **Spring Modulith**: um teste quebra o build se um módulo acessar o interior de outro.

### Módulos

Pacote raiz: `br.com.poupamais`. Cada pacote direto abaixo da raiz é um módulo.

| Módulo      | Responsabilidade                                                           | Depende de                     |
| ----------- | -------------------------------------------------------------------------- | ------------------------------ |
| `shared`    | Segurança/JWT, exceções + handler global, config, value objects comuns     | — (módulo aberto, todos usam)  |
| `usuario`   | Cadastro (RF009), login (RF010), perfil e foto (RF013), troca e recuperação de senha (RF012, RF014), LGPD: consentimento, exportação e exclusão de conta (RF015–RF017) | shared, categoria (categorias padrão), lancamento (exportar dados), notificacao |
| `notificacao` | Envio de e-mail (RNF020): `EmailFacade` + implementação SMTP              | shared                         |
| `categoria` | CRUD, inativar/reativar categorias (RF011, RN002)                          | shared                         |
| `lancamento`| CRUD, filtros, status ATIVO/INATIVO (RF001–RF005, RF008, RN001, RN004–RN006) | shared, categoria              |
| `dashboard` | Totalizadores, gráficos, ranking, mensagem (RF006, RN003, RN008–RN010)     | shared, lancamento, categoria  |
| `arquivo`   | Importação CSV (RF003, RN007) e exportação CSV/XLSX (RF007) + template     | shared, lancamento, categoria  |

Regras do grafo: **sem ciclos**; `usuario` não é dependência de ninguém (o id do usuário logado vem de `shared.security.UsuarioLogado`).
A exclusão de conta não precisa chamar os outros módulos: o banco apaga tudo em cascata a partir de `usuario`.

### Estrutura de um módulo (exemplo: `lancamento`)

```
br.com.poupamais
├── PoupaMaisApplication.java
├── shared/                                # package-info.java com @ApplicationModule(type = OPEN)
│   ├── config/        WebConfiguration (CORS)  (+ OpenApiConfig/JacksonConfig se precisar)
│   ├── security/      SecurityConfiguration, JwtService, JwtAuthenticationFilter, ErroAutenticacaoEntryPoint,
│   │                  UsuarioLogado, SenhaAlteradaConsulta
│   ├── exception/     RegraNegocioException, RecursoNaoEncontradoException, ConflitoException,
│   │                  ErroAutenticacaoException, ErroResponse, GlobalExceptionHandler
│   └── domain/        Periodo (value object mês/ano), NomeMes
├── lancamento/                            # exemplo completo; os outros módulos seguem o mesmo desenho
│   ├── package-info.java                  # @ApplicationModule (a raiz do módulo fica só com isto)
│   ├── publico/                           # @NamedInterface("publico"): ÚNICA parte visível aos outros módulos
│   │   ├── LancamentoFacade.java          #   interface (implementada no service/impl)
│   │   ├── dto/       LancamentoResumo, NovoLancamento, FiltroLancamento   (records)
│   │   └── enums/     TipoLancamento, StatusLancamento (ATIVO, INATIVO, DELETADO; podeIrPara)
│   ├── endpoints/                         # interno: adaptador de entrada HTTP
│   │   ├── LancamentoResource.java
│   │   ├── dto/       LancamentoRequest, LancamentoResponse, StatusRequest, LancamentoFiltroParams, CategoriaDoLancamentoResponse
│   │   └── mappers/   LancamentoMapper
│   ├── service/                           # interno: regras de negócio
│   │   ├── LancamentoService.java, LancamentoValidator.java, LancamentoResumoConversor.java
│   │   └── impl/      LancamentoServiceImpl   (também implementa LancamentoFacade)
│   └── model/                             # interno: persistência
│       ├── entidades/      Lancamento, CategoriaLancamento, CategoriaLancamentoId
│       ├── repositories/   LancamentoRepository
│       └── specifications/ LancamentoSpecifications
├── categoria/   publico/ (CategoriaFacade · dto/CategoriaResumo) · endpoints/ (+ dto/, mappers/) · service/ (+ impl/) ·
│                model/ (entidades/Categoria · repositories/CategoriaRepository)
├── usuario/     endpoints/ (AuthResource, PerfilResource, PrivacidadeResource + dto/, mappers/UsuarioMapper) ·
│                service/ (+ impl/, inclui SessaoUsuarioConsulta, ExportacaoDadosServiceImpl) · model/ (entidades/ ·
│                repositories/) · sem publico/
├── notificacao/ publico/ (EmailFacade · excecoes/EnvioEmailException) · service/impl/SmtpEmailService ·
│                templates em src/main/resources/templates/email/
├── dashboard/   endpoints/ (+ dto/) · service/ (ClassificacaoTaxa, RateioCategorias, MensagemSaldoFactory + impl/) ·
│                sem model/ e sem publico/ (só lê pelas facades)
└── arquivo/     endpoints/ (+ dto/) · service/importacao/ · service/exportacao/ · sem model/ e sem publico/
```

**Regra de ouro:** só a pasta **`publico/`** (e as subpastas dela) é visível aos outros módulos; `endpoints/`, `service/` e `model/`
são internos. Outro módulo **só** injeta a `XxxFacade` de `publico/` e usa os records/enums/exceções de `publico/dto`, `publico/enums`
e `publico/excecoes`. **Nunca** o repository ou a entidade de outro módulo. Dentro do mesmo módulo vale tudo
(ex.: a entidade `Lancamento` usa `publico/enums/StatusLancamento`).

**Como a fronteira é verificada (Spring Modulith 2.1.2):**
- Cada pasta pública tem `package-info.java` com `@NamedInterface("publico")`. **A anotação NÃO vale para subpacotes** (testado em
  12/11/2026): uma subpasta nova dentro de `publico/` precisa do **próprio** `package-info.java` com o mesmo nome, senão o
  `ModularidadeTest` acusa "depends on non-exposed type".
- Módulo sem nada a expor (`usuario`, `dashboard`, `arquivo`) não tem `publico/`.
- `shared` continua `@ApplicationModule(type = OPEN)`: todos usam tudo dele.
- Nomes de pastas: `publico` (não `public`, palavra reservada), `endpoints`, `dto`, `mappers`, `service`, `impl`, `model`, `entidades`,
  `repositories`, `specifications`, `enums`, `excecoes`. Testes ficam no **mesmo pacote** da classe testada.

### Relacionamento entre módulos no banco

As tabelas têm FKs reais, mas **no código as entidades se referenciam por id** entre módulos:

- `Lancamento.idUsuario` é `Long` (não `@ManyToOne Usuario`); o mesmo vale para `Categoria.idUsuario`.
- `CategoriaLancamento` (dentro de `lancamento`) guarda `idCategoria` (`Long`) e `idUsuario`. Para mostrar os nomes,
  o mapper chama `CategoriaFacade.buscarPorIds(idUsuario, ids)` **uma vez por listagem** (sem N+1).
- Validar que as categorias existem, são do usuário e estão ativas: `CategoriaFacade.validarAtivasDoUsuario(idUsuario, ids)`.

### APIs públicas já prontas (use; não recrie)

| Onde | API | Comportamento |
| --- | --- | --- |
| `shared.security.UsuarioLogado` | `static Long id()` | Id do usuário do token. Sem usuário → `ErroAutenticacaoException` (401). Única fonte do id (RN005). |
| `shared.security.JwtService` | `TokenGerado gerar(Long idUsuario)` → `(String token, Instant expiraEm)`; `Optional<TokenValido> validar(String token)` → `(idUsuario, emitidoEm, expiraEm)` | HS256, subject = id, `iat`/`exp` em segundos. `validar` nunca lança nem loga. App não sobe com `JWT_SECRET` < 32 bytes ou `JWT_EXPIRACAO_MIN` ≤ 0. |
| `shared.security.SenhaAlteradaConsulta` | `Optional<SituacaoUsuario> situacao(Long idUsuario)`; record `SituacaoUsuario(Instant senhaAlteradaEm)` (`null` = nunca trocou) | **Implementada no `usuario` (`SessaoUsuarioConsulta`, 1 consulta JPQL por requisição autenticada).** Vazio = usuário não existe mais → o filtro **recusa** o token. Também recusa `iat` < `senha_alterada_em` (comparação em segundos). Sem bean (ex.: `@WebMvcTest` de outro módulo) o filtro não checa. |
| `SecurityConfiguration` | propriedade `poupamais.swagger-habilitado` (`SWAGGER_HABILITADO`, padrão `true`) | `false` desliga o springdoc (`springdoc.api-docs.enabled`/`swagger-ui.enabled`) e tira as rotas do Swagger da lista pública (passam a exigir token). |
| `SecurityConfiguration` | bean `PasswordEncoder` (BCrypt) | Injete nos services; nunca instancie outro. |
| `shared.exception` | `RegraNegocioException` 422 · `RecursoNaoEncontradoException` 404 · `ConflitoException` 409 · `ErroAutenticacaoException` 401 | Mensagens em português prontas para o usuário. |
| `notificacao.publico.EmailFacade` | `void enviarRecuperacaoSenha(String emailDestino, String nome, String linkRedefinicao)` | Lança `notificacao.publico.excecoes.EnvioEmailException` em falha (sem logar destinatário/link). **Quem chama (RN014) captura e responde 202 igual.** |
| `lancamento.publico.LancamentoFacade` | `criar(idUsuario, NovoLancamento)`, `listar(idUsuario, FiltroLancamento)`, `listarAtivos(idUsuario, Collection<Periodo>)`, `listarTodos(idUsuario)`; records em `publico.dto` (`LancamentoResumo`, `FiltroLancamento`, `NovoLancamento`); enums em `publico.enums` (`TipoLancamento`, `StatusLancamento`) | Assinaturas exatamente como na seção abaixo. Detalhes em "Decisões do módulo `lancamento`". |
| `categoria.publico.CategoriaFacade` | `buscarPorIds(idUsuario, ids)`, `listarTodas(idUsuario)`, `validarAtivasDoUsuario(idUsuario, ids)`, `buscarPorDescricao(idUsuario, descricao)` (Optional, não cria), `buscarOuCriar(idUsuario, descricao)`, `criarPadroes(idUsuario)`; record `publico.dto.CategoriaResumo(id, descricao, ativa)` | Detalhes no contrato de Categorias. `validarAtivasDoUsuario`: 404 inexistente/de outro, 422 inativa. `listarTodas`: somente leitura, ativas e inativas (ativas primeiro, depois alfabética), usada pela exportação de dados (RF016). |

### API pública do módulo `lancamento` (✅ implementada; `dashboard`, `arquivo` e `usuario` usam exatamente isto)

Em `br.com.poupamais.lancamento.publico` (facade), `.publico.dto` (records) e `.publico.enums` (enums):

- `enum TipoLancamento { RECEITA, DESPESA }` e `enum StatusLancamento { ATIVO, INATIVO, DELETADO }` (com `podeIrPara(novo)`).
- `record LancamentoResumo(Long id, String descricao, TipoLancamento tipo, int mes, int ano, BigDecimal valor, StatusLancamento status,
  List<CategoriaResumo> categorias, LocalDate dataCadastro, LocalDate dataAtualizacao)` — `categorias` ordenadas por **id crescente**
  (a primeira é a "primeira categoria" da RN009).
- `record FiltroLancamento(String descricao, Integer mes, Integer ano, TipoLancamento tipo, StatusLancamento status, Long categoriaId)` —
  mesma semântica dos parâmetros do `GET /api/lancamentos` (sem `status` = ATIVO + INATIVO; nunca DELETADO sem pedir).
- `record NovoLancamento(String descricao, TipoLancamento tipo, Integer mes, Integer ano, BigDecimal valor, List<Long> categoriaIds)`.
- `interface LancamentoFacade`:
  - `LancamentoResumo criar(Long idUsuario, NovoLancamento novo)` — mesma validação do cadastro manual (`LancamentoValidator`, RN001 +
    `validarAtivasDoUsuario`); erro → `RegraNegocioException`/`RecursoNaoEncontradoException` com mensagem pronta (a importação usa como "motivo").
    `@Transactional` próprio: cada chamada é uma transação (RN007).
  - `List<LancamentoResumo> listar(Long idUsuario, FiltroLancamento filtro)` — mesmas Specifications e ordenação da listagem (exportação, RN013).
  - `List<LancamentoResumo> listarAtivos(Long idUsuario, Collection<Periodo> periodos)` — só ATIVO dos meses pedidos (dashboard, RN003/RN008).
  - `List<LancamentoResumo> listarTodos(Long idUsuario)` — todos os status (exportação de dados LGPD, RF016).

### Decisões do módulo `lancamento` (tomadas na implementação)

- `FiltroLancamento.vazio()` = sem filtros. `listar` com `categoriaId` que não é do usuário → `404` "Categoria não encontrada" (API e facade).
- `listarAtivos` com lista de períodos vazia/nula → lista vazia (não consulta). Todas as listagens usam a ordenação ano/mês/id desc.
- `LancamentoValidator.validar(NovoLancamento)` devolve os dados **normalizados**: descrição com `strip()`, valor `setScale(2, HALF_UP)`
  (arredondado ≤ 0 → 422; máximo `99999999999999.99`, limite do `numeric(16,2)`), `categoriaIds` sem nulos e sem repetidos (ordem preservada).
  Mensagens (422): "A descrição é obrigatória", "A descrição deve ter no máximo 100 caracteres", "O tipo é obrigatório (RECEITA ou DESPESA)",
  "O mês deve estar entre 1 e 12", "O ano deve estar entre 2000 e 2100", "O valor é obrigatório", "O valor deve ser maior que zero",
  "O valor excede o máximo permitido", "Informe pelo menos uma categoria". Na API o DTO já barra antes com `400` (`@Digits(14,2)` no valor).
- `StatusLancamento.podeIrPara(mesmo)` = `false`. Mas o `PATCH /status` com o status que o lançamento **já tem** (ATIVO/INATIVO) é
  idempotente: `200` sem mexer em `dataAtualizacao`. `PATCH` com `DELETADO` → `422` "Informe o status ATIVO ou INATIVO".
  Mensagens: "Lançamento excluído não pode ser editado. Restaure-o antes.", "O lançamento já está excluído",
  "Só é possível restaurar um lançamento excluído", "Não é possível alterar o status de X para Y", "Lançamento não encontrado".
- **Edição:** só as categorias **novas** precisam estar ativas; uma categoria que já estava no lançamento e foi inativada depois pode continuar.
- Os nomes das categorias são montados no `service` (`LancamentoResumoConversor`, 1 chamada a `CategoriaFacade.buscarPorIds` por lista),
  porque a facade também devolve `LancamentoResumo`; o `LancamentoMapper` da `api` só converte `LancamentoResumo` → DTO.
  A resposta traz `categorias: [{id, descricao}]` (sem `ativa`). O service da API devolve `LancamentoResumo`, não a entidade.
- Repository com `@EntityGraph("categorias")` no `findAll(spec, sort)` e no `findByIdAndIdUsuario` (sem N+1). Filtro por categoria usa
  subconsulta `exists` (não duplica linhas). `descricao` escapa `%` e `_`.

### Decisões do módulo `dashboard` (tomadas na implementação)

- Estrutura: `endpoints/DashboardResource` + `endpoints/dto/DashboardParams`, `DashboardResponse` · `service/DashboardService` →
  `impl/DashboardServiceImpl`, `ClassificacaoTaxa` (RN010, interna: nenhum outro módulo usa), `RateioCategorias` (RN009),
  `MensagemSaldoFactory` (RN010). Sem `model/` e sem `publico/`.
  Por ser só leitura e agregação, o service devolve direto o `DashboardResponse` (sem mapper).
- **Uma** chamada a `LancamentoFacade.listarAtivos(idUsuario, periodo.ultimos(4))` por requisição; o resto é calculado em memória.
- `mes`/`ano` opcionais (o que faltar vem de `Periodo.atual()`, fuso de Brasília); fora de 1–12 / 2000–2100 ou não numérico → `400`
  "Dados inválidos" com `erros[{campo, mensagem}]` ("O mês deve estar entre 1 e 12", "O ano deve estar entre 2000 e 2100").
- `taxaGasto` = despesas × 100 / receitas com 2 casas (`HALF_UP`); a classificação usa a taxa **já arredondada** (70,004 → 70,00 → dentro).
- Mensagens: saldo > 0 "Saldo positivo de R$ X em <Mês>"; saldo < 0 "As despesas superaram as receitas em R$ X em <Mês>" (X sem sinal);
  **saldo zero com lançamentos** "Receitas e despesas se igualaram em <Mês>: saldo de R$ 0,00"; **mês sem lançamento ativo**
  "Nenhum lançamento ativo em <Mês>". Moeda pt-BR `R$ 1.234,56` com espaço comum (não NBSP). Nunca recomenda investimento.
- `despesasPorCategoria`: parte = valor / N truncada em centavos; a sobra vai para a categoria de **menor id**. Ordem: valor desc, empate
  por `categoriaId` asc. Despesa sem categoria (não deveria existir) entra como `categoriaId: null`, "Sem categoria", para a soma fechar.
- `maioresDespesas`: top 5 por valor desc; **empate: menor id primeiro** (o cadastrado antes). `categorias` = nomes em ordem de id.

## Design patterns (onde e por quê)

| Pattern | Onde | Por quê |
| --- | --- | --- |
| **MVC em camadas** | `endpoints` → `service` → `model` em todo módulo | Controller fino (HTTP + validação de formato), regra no service, persistência no repository. |
| **Facade** | `CategoriaFacade`, `LancamentoFacade`, `EmailFacade` em `publico/` | É a fronteira do módulo: esconde o interior, que pode mudar sem quebrar os outros. |
| **DTO + Mapper** | `endpoints/dto` (records) + `endpoints/mappers/XxxMapper` (`@Component`, mapeamento manual) | Nunca expor entidade JPA; o contrato da API fica estável. |
| **Repository** | Spring Data `JpaRepository` em `model/repositories/` | Acesso a dados padronizado, sempre filtrando pelo dono (RN005). |
| **Specification** | `LancamentoSpecifications` (descrição, mês, ano, tipo, status, categoria) | Filtros combináveis do RF005 **reaproveitados pela exportação** ("exporta o que está filtrado"). |
| **Strategy + Factory** | `arquivo.exportacao`: `ExportadorLancamentos` (interface) → `ExportadorCsv`, `ExportadorXlsx`; `ExportadorFactory` recebe `List<ExportadorLancamentos>` e escolhe pelo `formato` | Adicionar PDF amanhã = 1 classe nova, sem `if/else` espalhado. |
| **Validator** | `LancamentoValidator` | RN001 em **um só lugar**, usado pelo cadastro manual **e** pela importação CSV. |
| **Value Object** | `shared.domain.Periodo(mes, ano)` com `anterior()`, `ultimos(4)` e `nomeMes()` | RN008 (4 meses atravessando a virada do ano) sem conta de data espalhada. |
| **Enum com comportamento** | `dashboard.service.ClassificacaoTaxa.de(BigDecimal taxa)` | RN010 (70% / 100%) num só lugar, testável. |
| **Máquina de estados simples** | `StatusLancamento.podeIrPara(novo)` + `Lancamento.alterarStatus(novo)` | RN006: transições ATIVO/INATIVO/DELETADO validadas num lugar só. |
| **Chain of Responsibility** | `JwtAuthenticationFilter` na filter chain do Spring Security | Autenticação fora dos controllers. |
| **Exception Handler centralizado** | `shared.exception.GlobalExceptionHandler` (`@RestControllerAdvice`) | Formato de erro único (RNF007); nenhum `try/catch` em controller. |
| **Injeção de dependência por construtor** | Todo bean (`private final` + `@RequiredArgsConstructor`) | Facilita teste unitário com mocks; nada de `@Autowired` em campo. |

**Não usar** (seria complexidade sem ganho aqui): eventos de domínio entre módulos (o dashboard só lê), CQRS, microsserviços, MapStruct.

### O que é proibido (evita código macarrônico)

- Regra de negócio em controller, ou controller chamando repository.
- Entidade JPA em request/response.
- Um módulo importando `endpoints/`, `service/` ou `model/` de outro (só `publico/` é permitido). O teste do Modulith falha.
- `double`/`float` para dinheiro (usar `BigDecimal`), `@Autowired` em campo, `try/catch` que engole exceção.
- Service "deus": se passar de ~200 linhas ou misturar assuntos, extraia (ex.: `RateioCategorias`, `MensagemSaldoFactory`).
- `id_usuario` vindo do body/query: vem **sempre** do `UsuarioLogado` (token).

## Banco de dados

DDL em `src/main/resources/db/migration` (Flyway): é a fonte da verdade; o Hibernate não cria tabelas.
O `V1__schema_inicial.sql` veio do DDL oficial (`../Banco de Dados/poupamais_modelo_fisico.sql`).

- Mudança de banco = **nova migração** `V<n>__descricao.sql`. **Nunca editar migração já aplicada**; só adicionar.
- `spring.flyway`: schema `poupamais` criado e gerenciado pelo Flyway, histórico em `poupamais.flyway_schema_history`.
- `spring.jpa.hibernate.ddl-auto=validate` e `spring.jpa.properties.hibernate.default_schema=poupamais`.
- Tabelas: `usuario`, `categoria`, `lancamento`, `categorias_lancamentos`.
- Tipos: `valor numeric(16,2)` → **`BigDecimal`**; `data_cadastro`/`data_atualizacao` são `date` → `LocalDate`;
  `tipo`/`status` são `varchar` → `@Enumerated(EnumType.STRING)`.
- Constraints que o banco já garante: e-mail único; descrição de categoria única por usuário **case-insensitive**
  (`lower(descricao)`); `mes` 1–12; `ano` 2000–2100; `valor > 0`; tipo/status nos valores dos enums.
  Mesmo assim **valide na aplicação antes**, para devolver mensagem amigável (RNF007) em vez de erro 500.
- **Atenção à tabela associativa:** `categorias_lancamentos` tem `id_usuario NOT NULL` replicado (FKs compostas
  `(id_categoria,id_usuario)` e `(id_lancamento,id_usuario)`). Um `@ManyToMany` + `@JoinTable` simples **não** preenche
  `id_usuario` e falha no insert. Mapeie como entidade `CategoriaLancamento` com `@EmbeddedId (idCategoria, idLancamento)`
  mais o campo `idUsuario`, e `Lancamento` com `@OneToMany(mappedBy, cascade = ALL, orphanRemoval = true)`.
- FK de categoria na associativa é `ON DELETE NO ACTION` **`DEFERRABLE INITIALLY DEFERRED`**: categoria com lançamentos não pode ser apagada
  (o erro só aparece no commit, por isso o service **verifica antes** e devolve 422), mas a exclusão da conta em cascata funciona.
  Testado em 06/11/2026 num Postgres 17: sem o `DEFERRABLE`, `DELETE FROM usuario` falhava.
- Tabelas da conta: `foto_perfil` (0..1 por usuário, `bytea` ≤ 2 MB, separada para não carregar a imagem em toda consulta) e
  `token_recuperacao_senha` (só o **hash SHA-256** do token, `expira_em`, `usado_em`).
- `usuario.aceite_termos_em` (LGPD) e `usuario.senha_alterada_em`: o `JwtAuthenticationFilter` recusa tokens com `iat` anterior a ela.
- E-mail sempre gravado e comparado em **minúsculas** (`trim().toLowerCase()`), porque o UNIQUE do banco diferencia maiúsculas.

## Regras de negócio (resumo do DVP — implementar no service do módulo indicado)

| RN    | Módulo | Regra |
| ----- | ------ | ----- |
| RN001 | lancamento | Obrigatórios: descrição (≤100), tipo, mês, ano, valor > 0 e **≥ 1 categoria**. |
| RN002 | categoria / arquivo | Lançamento pode ter N categorias. Categoria pertence a um usuário; descrição única por usuário sem diferenciar maiúsc./minúsc. No CSV, categoria inexistente em linha válida é **criada automaticamente** (`CategoriaFacade.buscarOuCriar`). Categoria vinculada a lançamento (inclusive DELETADO) **não pode ser excluída**, só inativada. Categoria inativa não pode ser usada em lançamento novo. **No cadastro do usuário**, criar as categorias padrão: Alimentação, Educação, Freelance, Investimentos, Lazer, Moradia, Outros, Salário, Saúde, Transporte, Vestuário. |
| RN003 | dashboard | Só lançamentos **ATIVOS** do mês/ano (padrão = mês atual). Saldo = Receitas − Despesas. Taxa = Despesas / Receitas × 100; se Receitas = 0 → `null` (não disponível). |
| RN004 | lancamento / dashboard | Três status. **ATIVO**: entra no dashboard. **INATIVO**: aparece na listagem, mas não entra no dashboard. **DELETADO**: exclusão lógica (lixeira); não aparece na listagem padrão nem no dashboard, só com o filtro `status=DELETADO`. Nada é apagado fisicamente. |
| RN005 | todos | Usuário só vê/edita/inativa/exporta/exclui **o que é dele**. |
| RN006 | lancamento | ATIVO/INATIVO: editáveis em qualquer mês e alternam livremente. DELETADO: **não pode ser editado nem alternado**; só **restaurar**, que devolve como **INATIVO**. Transições válidas: ATIVO⇄INATIVO, ATIVO/INATIVO→DELETADO, DELETADO→INATIVO. O "Desfazer" da exclusão é feito no front (a chamada só é enviada quando o toast fecha). |
| RN007 | arquivo | Importação parcial: salva as linhas válidas, ignora inválidas/em branco e devolve quais falharam e por quê. Nunca recusa o arquivo inteiro. |
| RN008 | dashboard | "Receitas vs Despesas" e "Saldo Mensal": 4 meses terminando no selecionado (`Periodo.ultimos(4)`). "Maiores Despesas": top 5 despesas ativas do mês, com categorias. |
| RN009 | dashboard | "Despesas por Categoria": só despesas ativas do mês. N categorias → valor dividido igualmente; **centavos de diferença vão para a primeira** (= menor `id_categoria`). Soma das fatias = total de despesas. |
| RN010 | dashboard | Taxa ≤ 70% "Dentro do limite"; > 70% e ≤ 100% "Atenção"; > 100% "Acima do limite". Mensagem descritiva: "Saldo positivo de R$ X em Agosto" / "As despesas superaram as receitas em R$ X em Agosto". **Nunca recomendar investimento** (RNI003). |
| RN011 | usuario | Cadastro: nome, e-mail válido e único, senha ≥ 6 com confirmação (a confirmação é validada no front). Senha só com hash BCrypt. A sessão expira (`JWT_EXPIRACAO_MIN`, padrão 480 = 8h). |
| RN012 | arquivo | Layout do CSV: ver "Formato CSV". **Máx. 2 MB e 5.000 linhas de dados**; acima disso, ou sem o cabeçalho, recusa o arquivo inteiro (422) antes de processar. Template para download. |
| RN013 | arquivo | Exportação CSV/XLSX respeita os filtros; DELETADO só sai com `status=DELETADO`; sem resultados → 422. |
| RN014 | usuario | Recuperação de senha: link por e-mail, token aleatório (32 bytes) guardado como hash, **30 min**, uso único; novo pedido invalida os anteriores; resposta **sempre igual** (não revela se o e-mail existe); ao redefinir, grava `senha_alterada_em` (derruba todas as sessões). |
| RN015 | usuario | Perfil: nome obrigatório (≤150); **e-mail não editável**; foto JPG/PNG/WEBP ≤ 2 MB (validar pelo conteúdo, não só pela extensão); sem foto, o front mostra a inicial. Troca de senha: exige a senha atual, a nova segue a RN011 e é ≠ da atual; grava `senha_alterada_em` e **devolve um token novo** (a sessão atual continua, as outras caem). |
| RN016 | usuario | LGPD: cadastro exige `aceiteTermos=true` (grava `aceite_termos_em`); exportar dados = JSON com perfil, categorias e todos os lançamentos (inclusive INATIVO e DELETADO); excluir conta exige a senha, é **física e irreversível** (`DELETE` em `usuario`, cascata). |

Fora de escopo (requisitos inversos): app nativo (RNI001), integração bancária/Open Finance (RNI002), recomendações de investimento (RNI003).

### Segurança e isolamento (RN005 / RNF002 / RNF004)

- O id do usuário vem **sempre do token JWT**, via `UsuarioLogado.id()`, nunca do body ou da query.
- Todo repository de dado do usuário filtra por dono: `findByIdAndIdUsuario(...)` e Specifications sempre com `doUsuario(id)`.
  Recurso de outro usuário → **404** (não revelar que existe).
- Rotas públicas: `POST /api/auth/cadastro`, `/login`, `/recuperar-senha`, `/redefinir-senha` e Swagger em dev. Todo o resto exige autenticação.
- Proteção de dados (RNF019): nunca logar senha, token ou valor financeiro; mensagens de erro sem detalhes internos.
- RNF006: **não** existe detecção de lançamento duplicado (dados iguais são válidos). A única proteção é contra reenvio técnico, feita no front.
- Senha: BCrypt, mínimo 6 caracteres (regra do protótipo). Nunca retornar `senha` em DTO.
- **Sessão de usuário excluído (feito):** `SenhaAlteradaConsulta.situacao(id)` devolve vazio quando o usuário não existe mais e o
  `JwtAuthenticationFilter` recusa o token (rota privada → 401). Com o módulo `usuario` presente, o bean sempre existe.
- **Swagger:** liberado só quando `SWAGGER_HABILITADO=true` (padrão `true` em dev, `false` em produção). Com `false`, o springdoc fica
  desligado (`springdoc.api-docs.enabled` e `springdoc.swagger-ui.enabled`) e as rotas do Swagger saem da lista pública.
- Segredo do JWT e credenciais via variável de ambiente (`JWT_SECRET`, `JWT_EXPIRACAO_MIN`, `DB_URL`, `DB_USER`, `DB_PASSWORD`,
  `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM`, `FRONT_URL` (monta o link de redefinição), `SWAGGER_HABILITADO`),
  com default só para dev.
- CORS: o navegador **não** chama esta API (o Next faz as chamadas pelo servidor). Mantenha o `WebConfiguration` com `CORS_ORIGINS`
  só para Swagger e testes locais; em produção, restrinja à URL do front.

## Contrato da API (combinado com o front: manter os dois CLAUDE.md em sincronia)

Prefixo `/api`. JSON em camelCase. Header `Authorization: Bearer <token>`.

**Coleções Bruno/Postman** com todos os endpoints: `doc/api/` (ver `doc/api/README.md`). **Todo endpoint novo entra nas duas
coleções por padrão**, no mesmo PR (ver "Convenções"); o `ColecaoApiTest` reprova o build se faltar algum.

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

Rotas públicas: `cadastro`, `login`, `recuperar-senha`, `redefinir-senha`.

Exportação de dados do titular (`GET /api/usuarios/me/dados`, decidido em `feat/exportacao-dados`):
- `200` com `Content-Type: application/json` e `Content-Disposition: attachment; filename="meus-dados-poupamais.json"`; sem token → `401`.
- camelCase; datas `LocalDate` como `"AAAA-MM-DD"` e instantes ISO-8601 em UTC (`"2026-11-12T18:00:00Z"`); `geradoEm` truncado em segundos.
- `perfil` traz **só** `id, nome, email, dataCadastro, aceiteTermosEm, possuiFoto`: nunca senha/hash, `senhaAlteradaEm` nem tokens.
  **A foto não vai no JSON** (só `possuiFoto`): imagem em Base64 incharia o arquivo e ela já pode ser baixada em `GET /api/usuarios/me/foto`.
- `categorias`: **todas** do usuário (ativas e inativas), ativas primeiro e depois alfabética (`CategoriaFacade.listarTodas`).
- `lancamentos`: **todos** os status (ATIVO, INATIVO e DELETADO), ordem ano/mês/id desc (`LancamentoFacade.listarTodos`), mesmo
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
      "mes": 8,
      "ano": 2026,
      "valor": 45.9,
      "status": "DELETADO",
      "categorias": [{ "id": 3, "descricao": "Lazer" }],
      "dataCadastro": "2026-08-02",
      "dataAtualizacao": "2026-08-03"
    }
  ]
}
```

**Categorias** (módulo `categoria`, RF011 / UC009). Não há tela própria no front: criar e editar são feitos por modais abertos
no dropdown de categorias do formulário de lançamento. Por isso o `POST` devolve a categoria criada, que o front já seleciona.

- `GET /api/categorias?ativa=true|false` (sem param = todas)
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
- O bloqueio do `DELETE` consulta `poupamais.categorias_lancamentos` por **query nativa** no `CategoriaRepository`
  (o módulo `categoria` não pode depender de `lancamento`). Corrida no índice único `lower(descricao)` → `409`.

**Lançamentos** (módulo `lancamento`, RF001–RF005, RF008 / UC001, UC002, UC004, UC010)

- `GET /api/lancamentos?descricao=&mes=&ano=&tipo=&status=&categoriaId=` → lista ordenada por **ano desc, mês desc, id desc**.
  `descricao` = contém, sem diferenciar maiúsculas (`lower(...) like`). Sem paginação. Categoria/lançamento de outro usuário → `404`.
  Sem `status` = ATIVO + INATIVO (**nunca** DELETADO). `status=ATIVO|INATIVO|DELETADO` filtra só aquele (DELETADO = "Lixeira").
- `GET /api/lancamentos/{id}`
- `POST /api/lancamentos` `{descricao, tipo, mes, ano, valor, categoriaIds: [..]}` → `201`
- `PUT /api/lancamentos/{id}` (mesmo body) → atualiza e seta `dataAtualizacao` · se DELETADO → `422`
- `PATCH /api/lancamentos/{id}/status` `{status: "ATIVO"|"INATIVO"}` → atualiza `dataAtualizacao` · se DELETADO → `422`
- `DELETE /api/lancamentos/{id}` → `204`; **não apaga**: muda o status para DELETADO
- `POST /api/lancamentos/{id}/restaurar` → `200` (lançamento com status INATIVO) · se não estiver DELETADO → `422`
- `POST`/`PUT`/`PATCH`/`restaurar` devolvem o lançamento no formato abaixo. `categoriaIds` repetidos são aceitos e gravados uma vez;
  categoria inativa → `422`; inexistente ou de outro usuário → `404` (vem do `validarAtivasDoUsuario`).
- `DELETE` de lançamento já DELETADO → `422`. Toda alteração (PUT, status, delete, restaurar) seta `dataAtualizacao = hoje`.

As transições ficam num método da entidade (`Lancamento.alterarStatus(novo)`, que lança `RegraNegocioException`),
não espalhadas pelo service.

Response de lançamento:

```json
{
  "id": 1,
  "descricao": "Salário mensal",
  "tipo": "RECEITA",
  "mes": 8,
  "ano": 2026,
  "valor": 8500.0,
  "status": "ATIVO",
  "categorias": [{ "id": 3, "descricao": "Salário" }],
  "dataCadastro": "2026-08-01",
  "dataAtualizacao": null
}
```

**Importação / exportação** (módulo `arquivo`, RF003, RF007 / UC003, UC006, UC007, UC008). As rotas ficam sob `/api/lancamentos`, mas o controller é do módulo `arquivo`.

- `POST /api/lancamentos/importar` (multipart, campo `arquivo`) → `200`
  `{ totalLinhas, importados, falhas: [{linha, conteudo, motivo}], categoriasCriadas: ["..."] }`
- `GET /api/lancamentos/exportar?formato=csv|xlsx&<mesmos filtros da listagem>` → arquivo com `Content-Disposition: attachment`
  · DELETADO só entra com `status=DELETADO` · sem lançamentos → `422` "Não há dados para exportação"
- `GET /api/lancamentos/template-csv` → baixa o `template.csv` (em `src/main/resources/template.csv`)

**Dashboard** (módulo `dashboard`, RF006 / UC005)

- `GET /api/dashboard?mes=8&ano=2026` (padrão: mês/ano atual). `classificacaoTaxa` pode ser `DENTRO_DO_LIMITE`, `ATENCAO`, `ACIMA_DO_LIMITE` ou `null`:

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
  "maioresDespesas": [{ "id": 9, "descricao": "Aluguel", "valor": 2100.0, "categorias": ["Moradia"] }]
}
```

`ultimosMeses` tem sempre 4 itens, do mais antigo ao selecionado; meses sem lançamento vêm com zeros.

**Padrão de erro** (RNF007, mensagens em português prontas para exibir):

```json
{
  "status": 400,
  "mensagem": "Dados inválidos",
  "erros": [{ "campo": "valor", "mensagem": "O valor deve ser maior que zero" }]
}
```

400 validação · 401 não autenticado · 404 não encontrado/não pertence ao usuário · 405 método não suportado · 409 duplicidade · 422 regra de negócio · 500 genérico (sem stacktrace).
Rota inexistente (com token válido) → 404 `"Recurso não encontrado"`; método HTTP não suportado → 405 `"Método não permitido para este recurso."`.

## Formato CSV (RN012 no DVP)

- Separador `;` (abre direto no Excel pt-BR), UTF-8 (aceitar BOM), 1ª linha = cabeçalho.
- Cabeçalho: `descricao;tipo;mes;ano;valor;categorias`
- `tipo`: `RECEITA`/`DESPESA` (sem diferenciar maiúsc./minúsc.); `valor`: `1234,56` ou `1234.56`;
  `categorias`: várias separadas por `|` (ex.: `Alimentação|Lazer`).
- Exemplo:
  ```
  descricao;tipo;mes;ano;valor;categorias
  Salário mensal;RECEITA;8;2026;8500,00;Salário
  iFood — pedidos do mês;DESPESA;8;2026;420,00;Alimentação|Lazer
  ```
- Limites: **2 MB** (`spring.servlet.multipart.max-file-size=2MB`) e **5.000 linhas de dados**. Excedeu ou sem cabeçalho → 422 com o arquivo inteiro recusado.
- Cada linha passa pelo mesmo `LancamentoValidator` (RN001). O método de importação **não** é `@Transactional`:
  cada linha válida é salva na sua própria transação (categorias novas + `LancamentoFacade.criar`), então uma falha não desfaz as outras (RN007).
- A exportação usa o **mesmo layout** (+ coluna `status`), para que um arquivo exportado possa ser reimportado.

Decisões da implementação (`feat/arquivo`):
- **Cabeçalho**: localizado pelo nome (trim, sem diferenciar maiúsc.), em qualquer ordem; as 6 colunas são obrigatórias e colunas
  extras (ex.: `status` do arquivo exportado) são ignoradas. Linhas em branco antes do cabeçalho são puladas.
- **Recusa do arquivo inteiro (422, nada é gravado)**: vazio/sem campo `arquivo`, > 2 MB, não é texto UTF-8 válido (ou tem byte nulo),
  cabeçalho ausente/incompleto, nenhuma linha de dados, > 5.000 linhas de dados não vazias.
- **Linha em branco**: ignorada; **não** conta em `totalLinhas` nem como falha. `totalLinhas` = linhas de dados não vazias.
  `linha` = número real no arquivo (cabeçalho = 1, linhas em branco contam na numeração).
- Linha com colunas faltando/sobrando ou aspas quebradas → falha da linha (campos com `;` vão entre aspas duplas; uma linha = um registro).
- **Valor**: aceita `1234,56`, `1234.56`, `1.234,56`, `1,234.56`, `R$ 10,5`. Com vírgula **e** ponto, o último é o decimal e o outro é milhar;
  com só um deles, ele é o decimal, salvo se repetido (`1.234.567` = milhar). Máximo 2 casas (`1,234` e `10,555` são falha). Negativo/zero → falha.
- `tipo`/`mes`/`ano`/`descricao`/categorias são checados **antes** de criar qualquer categoria (checagem prévia da RN001 no `ConversorLinha`);
  a validação definitiva continua no `LancamentoValidator` via `LancamentoFacade.criar`. Categorias repetidas na linha (ignorando maiúsc.) contam uma vez.
- **Categoria inativa** citada → falha da linha "A categoria "X" está inativa e não pode ser usada", sem criar as outras categorias novas da linha.
  Categoria nova (RN002) só é criada (`buscarOuCriar`) depois que a linha passou em tudo isso; `categoriasCriadas` lista as descrições criadas.
- **Transação por linha** (pendência 12): o `ImportacaoService` abre uma transação só da linha (`TransactionOperations`) com a criação
  das categorias novas e o `LancamentoFacade.criar`; as facades usam a propagação padrão (`REQUIRED`) e entram nela. Se `criar` recusar
  a linha (ou o commit falhar), a categoria criada para ela é desfeita junto: nenhuma categoria órfã, e `categoriasCriadas` (e o cache
  da importação) só recebem categorias de linhas **gravadas**.
- Erro inesperado numa linha → falha com "Não foi possível salvar esta linha. Tente novamente." (só o tipo da exceção vai para o log).
- **Exportação**: CSV em UTF-8 **com BOM**, `;`, CRLF, valor `8500,00`; XLSX (aba "Lançamentos", cabeçalho em negrito, mês/ano/valor numéricos,
  valor com formato `0.00`). Nome `lancamentos-AAAA-MM-DD.<formato>` (data de São Paulo). `formato` ausente ou diferente de `csv`/`xlsx` → 400
  "Formato inválido. Use: csv ou xlsx". Content types: `text/csv;charset=UTF-8` e
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`. Template baixa como `template.csv` (`text/csv;charset=UTF-8`).

## Convenções

- Código, classes, endpoints e mensagens em **português** (domínio do projeto); sem acento em identificadores.
- Nomes: `XxxResource` (controller), `XxxService` + `XxxServiceImpl`, `XxxRepository`, `XxxRequest`/`XxxResponse` (records), `XxxMapper`, `XxxFacade`.
- Validação de formato com Bean Validation nos DTOs (`@NotBlank`, `@Positive`, `@NotEmpty`, `@Size`…);
  regra de negócio no service, lançando as exceções de `shared.exception`.
- `@Transactional` nos métodos de escrita do service; `@Transactional(readOnly = true)` nas consultas.
- Dinheiro com `BigDecimal` e `RoundingMode` sempre explícito.
- Datas de auditoria: `dataCadastro` em `@PrePersist`; `dataAtualizacao` em todo update e troca de status.
- Toda funcionalidade nasce **com testes** (ver "Testes"). Sem teste, não está pronta.
- **Todo endpoint novo ou alterado entra por padrão nas coleções do Postman e do Bruno** (`doc/api/`), no mesmo PR: acrescente a
  requisição (corpo de exemplo, status esperado e, se criar recurso, o script que guarda o id) na lista `REQUISICOES` de
  `doc/api/gerar_colecoes.py` e rode `python3 doc/api/gerar_colecoes.py`. Nunca edite os arquivos gerados à mão.
  O `ColecaoApiTest` compara os controllers com as duas coleções e falha se faltar endpoint ou sobrar um que não existe mais.
- Git: **Gitflow**. Funcionalidade nova só entra assim: branch `feat/<card>` criada da `development` → PR para a **`development`** →
  depois PR de `development` para a **`main`** (release). Nunca PR de `feat/*` direto para a `main`, nem commit direto em `development`/`main`.
  Conventional Commits (`feat(lancamento): ...`, `fix(dashboard): ...`).

## Testes

Três níveis obrigatórios, mais a verificação de arquitetura. Guia detalhado: skill `testes-backend`.

| Nível | Classe | O que prova | Banco |
|---|---|---|---|
| **Unitário** | `XxxServiceTest`, `PeriodoTest`, `RateioCategoriasTest`… | Regras de negócio isoladas (RN001–RN016), com Mockito nas dependências | Nenhum |
| **Web** | `XxxResourceTest` (`@WebMvcTest`) | Status HTTP, validação (400 + `erros[]`), 401 sem token, 404 para recurso de outro usuário | Nenhum |
| **Integração** | `XxxIntegracaoTest` (`@SpringBootTest`) | O que só o banco prova: cascata da exclusão de conta, CHECK de status, FK `DEFERRABLE`, Specifications, importação ponta a ponta | **PostgreSQL real** |
| **Arquitetura** | `ModularidadeTest` | Nenhum módulo acessa o interior de outro (Spring Modulith) | Nenhum |
| **Coleções da API** | `ColecaoApiTest` | As coleções Postman e Bruno de `doc/api/` têm exatamente os endpoints dos controllers | Nenhum |

- Integração roda só com `POUPAMAIS_TEST_DB_URL` definida (`@EnabledIfEnvironmentVariable`); no CI ela está **sempre** definida.
  Localmente: `docker compose up -d db-test` (porta 5433, nasce vazio; o Flyway cria o schema ao subir o contexto) e
  `export POUPAMAIS_TEST_DB_URL=jdbc:postgresql://localhost:5433/poupamais_test`.
- **Cobertura (JaCoCo)**: `./mvnw verify` falha abaixo do piso do `pom.xml`: **80% de linhas e 70% de branches** no início
  (o Previna usa 95/90; subimos o piso junto quando a cobertura subir). Excluídos: `PoupaMaisApplication` e os `dto/` (records sem lógica).
- Nomes de teste em português descrevendo o comportamento: `deveRecusarArquivoComMaisDe5000Linhas()`.

## Pipeline (CI: GitHub Actions)

Arquivo: `.github/workflows/ci.yml` (mesmo desenho da pipeline do Previna, adaptado).

- **Quando roda:** push na `development` e na `main`, e todo Pull Request. Um push novo no mesmo PR cancela a execução anterior (`concurrency`).
- **Permissões:** só leitura (`contents: read`).

| Job | Passos | Reprova se |
|---|---|---|
| `testes` (Testes e cobertura) | 1. checkout · 2. Java 21 Temurin com cache Maven · 3. `spotless:check` · 4. `./mvnw -B -ntp verify` (o Flyway cria o schema no Postgres de serviço, vazio) · 5. publica o relatório JaCoCo (sempre) e o do Surefire (se falhar) | formatação, qualquer teste, `ModularidadeTest` ou cobertura abaixo do piso |
| `compose` (Docker Compose) | valida `docker compose config -q` | compose inválido |

- Serviço do job `testes`: `postgres:17-alpine` com `POUPAMAIS_TEST_DB_URL` apontando para ele, então **os testes de integração sempre rodam no CI**.
- Rodar o mesmo localmente: skill `pipeline-local`.
- **Regra da equipe:** PR só é integrado com o CI verde. Configurar no GitHub: Settings → Branches → proteção da `development` e da `main`
  exigindo o check "Testes e cobertura" e 1 aprovação.
- Sem deploy (decisão de 12/11/2026): não há job de imagem nem publicação; a entrega roda localmente com `docker compose`.

## Definição de pronto

Nenhuma tarefa é dada como finalizada sem rodar o passo a passo **completo** da pipeline local (skill `pipeline-local`:
`spotless:check`, `db-test` no ar, `verify` com os testes de integração rodando, `docker compose config -q`) com tudo verde.
Mexeu em endpoint? As coleções de `doc/api/` foram atualizadas no mesmo PR (o `verify` roda o `ColecaoApiTest`).
Se algum passo não rodou (Docker parado, rede, teste pulado), a tarefa **não está pronta**: diga exatamente o que faltou.

## Claude Code: skills e plugins

Skills do repo (`.claude/skills/`), usadas automaticamente pelo Claude quando o assunto aparece:

| Skill | Quando |
|---|---|
| `feature-backend` | Criar ou alterar qualquer funcionalidade da API (módulo certo, camadas, testes, contrato) |
| `testes-backend` | Escrever ou corrigir testes; cobertura do JaCoCo reprovando |
| `pipeline-local` | Antes de dizer "pronto", commitar ou abrir PR; CI falhou |
| `mudanca-de-regra` (na raiz do workspace) | O pedido contradiz o DVP, o DDL ou o contrato |

Plugins oficiais recomendados (instalar com `/plugin`): `feature-dev`, `pr-review-toolkit`, `commit-commands`, `security-guidance`, `context7` (documentação atualizada de Spring/JPA).

## Plano de entregas e pendências

Uma branch `feat/<nome>` por item, saindo da `development`; o dev para no **push da branch**, e o PR é aberto e integrado pela equipe.

| # | Entrega | Situação | Inclui (além do contrato) |
| --- | --- | --- | --- |
| 1 | Segurança JWT | ✅ na `development` | — |
| 2 | `notificacao` | ✅ na `development` | — |
| 3 | `categoria` | ✅ na `development` | — |
| 4 | `usuario` (cadastro, login, recuperação/redefinição, perfil, foto, troca de senha, exclusão de conta) | ✅ na `development` | `SenhaAlteradaConsulta` + recusa de usuário excluído ✅; Swagger por `SWAGGER_HABILITADO` ✅; `EnvioEmailException` capturada ✅ |
| 5 | `lancamento` | ✅ na `development` | `LancamentoFacade` exatamente como definida acima |
| 6 | Exportação de dados `GET /api/usuarios/me/dados` (RF016) | ✅ na `development` | usa `LancamentoFacade.listarTodos` e `CategoriaFacade.listarTodas` (nova, só leitura); estrutura do JSON no contrato. Pendente: replicar o contrato no `CLAUDE.md` do front |
| 7 | `dashboard` | ✅ na `development` | — |
| 8 | `arquivo` (importação, exportação, template) | ✅ na `development` | `CategoriaFacade.buscarPorDescricao` adicionada (categoria inativa e RN002 sem criar antes da hora); decisões em "Formato CSV" |
| 10 | Correção: rota inexistente responde `500` (o `GlobalExceptionHandler` não trata `NoResourceFoundException`) | ✅ na `development` | — |
| 9 | Deploy | ❌ fora do escopo (12/11/2026) | apresentação e entrega rodando localmente com `docker compose up -d --build` |
| 11 | Flyway (schema em `db/migration`) + Docker da API (`Dockerfile`, serviço `api` no compose) | ✅ na `development` | — |
| 12 | Pendência (`arquivo`): caso raro em que a importação cria a categoria nova e depois `LancamentoFacade.criar` recusa a linha; a categoria fica sem lançamentos | ✅ na `development` | categorias novas + lançamento na mesma transação por linha (`TransactionOperations` no `arquivo`); ver "Formato CSV" |
| 13 | Reorganização de pastas: `publico/` (`@NamedInterface`), `endpoints/` (+ `dto/`, `mappers/`), `model/` (`entidades/`, `repositories/`, `specifications/`) | ✅ na `development` | só organização; nenhuma regra ou contrato mudou |
| 14 | Pendência (testes): cada `*IntegracaoTest` com `@DynamicPropertySource` próprio sobe um contexto Spring separado, e cada um mantém um pool Hikari de 10 conexões aberto até o fim do `verify` (8 contextos ≈ 80 das 100 do Postgres). Com o `db-test` compartilhado, duas execuções em paralelo deram `FATAL: sorry, too many clients already` (12/11/2026) | ✅ na branch `chore/fechamento-backend`, aguardando PR | `src/test/resources/application.properties`: pool Hikari de 3 conexões nos testes |

Ao terminar uma entrega: atualize esta tabela e a seção "APIs públicas já prontas" no mesmo PR.
**Débito técnico:** cada dev faz só a sua entrega. O que achar fora do escopo dela não corrige: registra aqui como pendência
(linha nova nesta tabela ou na coluna "Inclui" da entrega que vai tratar daquilo), para quem pegar a próxima tarefa. Evite acumular muito.

## Direção futura (ideias a discutir)

Leia `doc/IDEIAS_FUTURAS.md` antes de propor funcionalidade nova: são ideias em aberto, levantadas com base em projetos acadêmicos
antigos, para a equipe discutir. Primeiro o back é concluído com o escopo atual; se o escopo crescer, os requisitos são reescritos antes.
Nada disso é para implementar sem decisão da equipe.

## Pontos em aberto (decidir com a equipe antes de implementar)

1. Paginação da listagem: não é exigida; a lista atual devolve tudo. Adicionar `page/size` se o volume crescer.
