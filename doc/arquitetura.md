# Arquitetura do backend

> Detalhe do guia (antes no `CLAUDE.md`). As regras curtas e o que é proibido ficam no `AGENTS.md`.

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
| `lancamento`| CRUD com data completa, filtros por mês/ano, status ATIVO/INATIVO/lixeira (RF001–RF005, RF008, RN001, RN004–RN006) | shared, categoria              |
| `dashboard` | Totalizadores, gráficos, ranking, mensagem (RF006, RN003, RN008–RN010)     | shared, lancamento, categoria  |
| `arquivo`   | Importação CSV (RF003, RN007) e exportação CSV/XLSX (RF007) + template     | shared, lancamento, categoria  |

Regras do grafo: **sem ciclos**; `usuario` não é dependência de ninguém (o id do usuário logado vem de `shared.security.UsuarioLogado`).
A exclusão de conta não precisa chamar os outros módulos: o banco apaga tudo em cascata a partir de `usuario`.

### Estrutura de um módulo

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
| `shared.exception` | `RegraNegocioException` 422 · `RecursoNaoEncontradoException` 404 · `ConflitoException` 409 · `ErroAutenticacaoException` 401, todas com construtor `(CodigoErro codigo, String mensagem)` | Mensagens em português prontas para o usuário; o `codigo` (enum `CodigoErro`, obrigatório, nunca `null`) vai no campo `codigo` da resposta. Código novo: constante no `CodigoErro` + linha na tabela de "Padrão de erro" + teste (o `CodigoErroTest` reprova código sem teste). |
| `notificacao.publico.EmailFacade` | `void enviarRecuperacaoSenha(String emailDestino, String nome, String linkRedefinicao)` | Lança `notificacao.publico.excecoes.EnvioEmailException` em falha (sem logar destinatário/link). **Quem chama (RN014) captura e responde 202 igual.** |
| `lancamento.publico.LancamentoFacade` | `criar(idUsuario, NovoLancamento)`, `listar(idUsuario, FiltroLancamento)`, `listarAtivos(idUsuario, Collection<Periodo>)`, `listarTodos(idUsuario)`; records em `publico.dto` (`LancamentoResumo`, `FiltroLancamento`, `NovoLancamento`); enums em `publico.enums` (`TipoLancamento`, `StatusLancamento`) | Assinaturas exatamente como na seção abaixo. Detalhes em "Decisões do módulo `lancamento`". |
| `shared.endpoints.dto.PeriodoParams` | `record PeriodoParams(Integer mes, Integer ano)` com `Periodo paraPeriodo()` | Query `mes`/`ano` opcionais do `GET /dashboard` e do `GET /categorias` (`@Valid @ModelAttribute`): fora de 1–12 / 2000–2100 → `400 VALIDACAO` no campo ("O mês deve estar entre 1 e 12", "O ano deve estar entre 2000 e 2100"); o que faltar vem de `Periodo.atual()`. Use em toda rota nova com mês/ano validados; não repita as anotações. |
| `categoria.publico.CategoriaFacade` | `buscarPorIds(idUsuario, ids)`, `listarTodas(idUsuario)`, `validarAtivasDoUsuario(idUsuario, ids)`, `buscarPorDescricao(idUsuario, descricao)` (Optional, não cria), `buscarOuCriar(idUsuario, descricao)`, `criarPadroes(idUsuario)`; record `publico.dto.CategoriaResumo(id, descricao, ativa)` | Detalhes no contrato de Categorias. `validarAtivasDoUsuario`: 404 inexistente/de outro, 422 inativa. `listarTodas`: somente leitura, ativas e inativas (ativas primeiro, depois alfabética), usada pela exportação de dados (RF016). |

### API pública do módulo `lancamento` (✅ implementada; `dashboard`, `arquivo` e `usuario` usam exatamente isto)

Em `br.com.poupamais.lancamento.publico` (facade), `.publico.dto` (records) e `.publico.enums` (enums):

- `enum TipoLancamento { RECEITA, DESPESA }` e `enum StatusLancamento { ATIVO, INATIVO, DELETADO }` (com `podeIrPara(novo)`).
- `record LancamentoResumo(Long id, String descricao, TipoLancamento tipo, LocalDate data, BigDecimal valor, StatusLancamento status,
  List<CategoriaResumo> categorias, OffsetDateTime dataCadastro, OffsetDateTime dataAtualizacao, OffsetDateTime dataExclusao)` —
  `data` = data do lançamento (RN001); auditoria com hora, em UTC; `dataExclusao` só preenchida enquanto DELETADO.
  `categorias` ordenadas por **id crescente** (a primeira é a "primeira categoria" da RN009).
- `record FiltroLancamento(String descricao, Integer mes, Integer ano, TipoLancamento tipo, StatusLancamento status, Long categoriaId)` —
  mesma semântica dos parâmetros do `GET /api/lancamentos` (sem `status` = ATIVO + INATIVO; nunca DELETADO sem pedir). O filtro
  continua por **mês/ano** e vira intervalo de datas sobre `data` (ver "Decisões do módulo `lancamento`").
- `record NovoLancamento(String descricao, TipoLancamento tipo, LocalDate data, BigDecimal valor, List<Long> categoriaIds)`.
- `interface LancamentoFacade`:
  - `LancamentoResumo criar(Long idUsuario, NovoLancamento novo)` — mesma validação do cadastro manual (`LancamentoValidator`, RN001 +
    `validarAtivasDoUsuario`); erro → `RegraNegocioException`/`RecursoNaoEncontradoException` com mensagem pronta (a importação usa como "motivo").
    `@Transactional` próprio: cada chamada é uma transação (RN007).
  - `List<LancamentoResumo> listar(Long idUsuario, FiltroLancamento filtro)` — mesmas Specifications e ordenação da listagem (exportação, RN013).
  - `List<LancamentoResumo> listarAtivos(Long idUsuario, Collection<Periodo> periodos)` — só ATIVO dos meses pedidos (dashboard, RN003/RN008):
    um único intervalo, do dia 1 do mês mais antigo ao último dia do mais recente (o dashboard pede meses seguidos).
  - `List<LancamentoResumo> listarTodos(Long idUsuario)` — todos os status (exportação de dados LGPD, RF016).

### Decisões do módulo `lancamento` (tomadas na implementação)

- `FiltroLancamento.vazio()` = sem filtros. `listar` com `categoriaId` que não é do usuário → `404` "Categoria não encontrada" (API e facade).
- `listarAtivos` com lista de períodos vazia/nula → lista vazia (não consulta). Todas as listagens usam a ordenação **`data` desc, `id` desc**
  (`LancamentoRepository.ORDENACAO`).
- **Data completa (RN001 revista em 18/11/2026):** o lançamento tem `data` (`LocalDate`) no lugar de `mes`/`ano`. As listagens, a
  exportação e o dashboard **continuam filtrando por mês/ano** (`LancamentoSpecifications.doPeriodo(mes, ano)`): com os dois, intervalo
  `Periodo.inicio()`…`Periodo.fim()` (dia 1 ao último dia do mês); só o ano, 01/01…31/12; só o mês, aquele mês de qualquer ano
  (`extract(month)`); mês fora de 1–12 ou ano fora de 2000–2100 → lista vazia (como antes). O dashboard agrupa por `Periodo.de(data)`.
- **Auditoria com hora:** `dataCadastro` (`@PrePersist`), `dataAtualizacao` (toda edição/troca de status) e `dataExclusao` são
  `OffsetDateTime` em UTC, truncados em segundos. `dataExclusao` é gravada ao ir para DELETADO e volta a `null` ao restaurar
  (`Lancamento.alterarStatus` / `Lancamento.restaurar`).
- **Status anterior (RN006 revista em 19/11/2026):** ao ir para DELETADO, `Lancamento.statusAnterior` (coluna `status_anterior`)
  guarda o status que ele tinha (ATIVO/INATIVO); `Lancamento.restaurar()` volta a ele e limpa o campo. Nulo (item excluído antes
  da V3) → volta como INATIVO. `podeIrPara` aceita DELETADO→ATIVO/INATIVO, mas `alterarStatus` recusa sair da lixeira: só o
  `restaurar()` faz isso (o `PATCH /status` num DELETADO → `422` "Não é possível alterar o status de DELETADO para X").
- **`status` no POST/PUT:** opcional (`LancamentoRequest.status`, `@StatusAtivoOuInativo`). POST sem ele = ATIVO; PUT sem ele
  = mantém o atual; com o mesmo status que já tem, só os dados mudam. `DELETADO` → `400` "Dados inválidos" com
  `erros[{campo: "status", mensagem: "Informe o status ATIVO ou INATIVO"}]` (o service também recusa com `422`, por defesa).
  `LancamentoService.criar/editar` recebem o status à parte; o `NovoLancamento` e a `LancamentoFacade` não mudaram (a importação
  continua criando ATIVO).
- `LancamentoValidator.validar(NovoLancamento)` devolve os dados **normalizados**: descrição com `strip()`, valor `setScale(2, HALF_UP)`
  (arredondado ≤ 0 → 422; máximo `99999999999999.99`, limite do `numeric(16,2)`), `categoriaIds` sem nulos e sem repetidos (ordem preservada).
  Mensagens (422): "A descrição é obrigatória", "A descrição deve ter no máximo 100 caracteres", "O tipo é obrigatório (RECEITA ou DESPESA)",
  "A data é obrigatória", "A data deve estar entre 01/01/2000 e 31/12/2100", "O valor é obrigatório", "O valor deve ser maior que zero",
  "O valor excede o máximo permitido", "Informe pelo menos uma categoria". Na API o DTO já barra antes com `400` (`@Digits(14,2)` no valor,
  `@NotNull` na data); a faixa da data é conferida no validator (`422`).
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

- Estrutura: `endpoints/DashboardResource` + `endpoints/dto/DashboardResponse` (query em `shared.endpoints.dto.PeriodoParams`) · `service/DashboardService` →
  `impl/DashboardServiceImpl`, `ClassificacaoTaxa` (RN010, interna: nenhum outro módulo usa), `RateioCategorias` (RN009),
  `MensagemSaldoFactory` (RN010). Sem `model/` e sem `publico/`.
  Por ser só leitura e agregação, o service devolve direto o `DashboardResponse` (sem mapper).
- **Uma** chamada a `LancamentoFacade.listarAtivos(idUsuario, periodo.ultimos(4))` por requisição; o resto é calculado em memória.
  Os lançamentos são agrupados pelo mês da data (`Periodo.de(l.data())`).
- `mes`/`ano` opcionais (o que faltar vem de `Periodo.atual()`, fuso de Brasília); fora de 1–12 / 2000–2100 ou não numérico → `400`
  "Dados inválidos" com `erros[{campo, mensagem}]` ("O mês deve estar entre 1 e 12", "O ano deve estar entre 2000 e 2100").
- `taxaGasto` = despesas × 100 / receitas com 2 casas (`HALF_UP`); a classificação usa a taxa **já arredondada** (70,004 → 70,00 → dentro).
- Mensagens: saldo > 0 "Saldo positivo de R$ X em <Mês>"; saldo < 0 "As despesas superaram as receitas em R$ X em <Mês>" (X sem sinal);
  **saldo zero com lançamentos** "Receitas e despesas se igualaram em <Mês>: saldo de R$ 0,00"; **mês sem lançamento ativo**
  "Nenhum lançamento ativo em <Mês>". Moeda pt-BR `R$ 1.234,56` com espaço comum (não NBSP). Nunca recomenda investimento.
- `despesasPorCategoria`: parte = valor / N truncada em centavos; a sobra vai para a categoria de **menor id**. Ordem: valor desc, empate
  por `categoriaId` asc. Despesa sem categoria (não deveria existir) entra como `categoriaId: null`, "Sem categoria", para a soma fechar.
- `maioresDespesas`: top 5 por valor desc; **empate: menor id primeiro** (o cadastrado antes). `categorias` = nomes em ordem de id.
  Cada item traz `data` (data do lançamento, ISO `"AAAA-MM-DD"`, como na response de lançamento; v1.2.0); ela não entra na ordenação nem no peso.

## Design patterns (onde e por quê)

| Pattern | Onde | Por quê |
| --- | --- | --- |
| **MVC em camadas** | `endpoints` → `service` → `model` em todo módulo | Controller fino (HTTP + validação de formato), regra no service, persistência no repository. |
| **Facade** | `CategoriaFacade`, `LancamentoFacade`, `EmailFacade` em `publico/` | É a fronteira do módulo: esconde o interior, que pode mudar sem quebrar os outros. |
| **DTO + Mapper** | `endpoints/dto` (records) + `endpoints/mappers/XxxMapper` (`@Component`, mapeamento manual) | Nunca expor entidade JPA; o contrato da API fica estável. |
| **Repository** | Spring Data `JpaRepository` em `model/repositories/` | Acesso a dados padronizado, sempre filtrando pelo dono (RN005). |
| **Specification** | `LancamentoSpecifications` (descrição, mês/ano como intervalo de datas, tipo, status, categoria) | Filtros combináveis do RF005 **reaproveitados pela exportação** ("exporta o que está filtrado"). |
| **Strategy + Factory** | `arquivo.exportacao`: `ExportadorLancamentos` (interface) → `ExportadorCsv`, `ExportadorXlsx`; `ExportadorFactory` recebe `List<ExportadorLancamentos>` e escolhe pelo `formato` | Adicionar PDF amanhã = 1 classe nova, sem `if/else` espalhado. |
| **Validator** | `LancamentoValidator` | RN001 em **um só lugar**, usado pelo cadastro manual **e** pela importação CSV. |
| **Value Object** | `shared.domain.Periodo(mes, ano)` com `anterior()`, `ultimos(4)`, `nomeMes()`, `inicio()`/`fim()` e `de(LocalDate)` | RN008 (4 meses atravessando a virada do ano) e o intervalo de datas do filtro de mês sem conta de data espalhada. |
| **Enum com comportamento** | `dashboard.service.ClassificacaoTaxa.de(BigDecimal taxa)` | RN010 (70% / 100%) num só lugar, testável. |
| **Máquina de estados simples** | `StatusLancamento.podeIrPara(novo)` + `Lancamento.alterarStatus(novo)` | RN006: transições ATIVO/INATIVO/DELETADO validadas num lugar só. |
| **Chain of Responsibility** | `JwtAuthenticationFilter` na filter chain do Spring Security | Autenticação fora dos controllers. |
| **Exception Handler centralizado** | `shared.exception.GlobalExceptionHandler` (`@RestControllerAdvice`) | Formato de erro único (RNF007); nenhum `try/catch` em controller. |
| **Injeção de dependência por construtor** | Todo bean (`private final` + `@RequiredArgsConstructor`) | Facilita teste unitário com mocks; nada de `@Autowired` em campo. |

**Não usar** (seria complexidade sem ganho aqui): eventos de domínio entre módulos (o dashboard só lê), CQRS, microsserviços, MapStruct.
