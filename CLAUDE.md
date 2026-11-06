# PoupaMais — BackEnd (API REST)

Aplicação web de gestão financeira pessoal e familiar (projeto de LES — Fatec Guarulhos, orientação Prof. Jadir Custódio Júnior).
O usuário registra **receitas e despesas** (lançamentos), organiza por **categorias**, importa/exporta em lote via **CSV/Excel**
e acompanha um **Dashboard** mensal. Foco: letramento financeiro e prevenção do superendividamento (ODS 1 e 8).

Este repositório é **só a API**. O front (Next.js) fica em `LES-PoupaMais/PoupaMais_FrontEnd` e chama esta API **pelo servidor do Next**
(BFF), não direto do navegador.
Documentação de origem (fora do repo, na pasta do workspace `../`): `../Documentação/DVP_PoupaMais_v1.md` (requisitos, UCs, RNs),
`../Banco de Dados/poupamais_modelo_fisico.sql` (DDL oficial), `../Gestão (Trello)/` (backlog exportado).

## Estado atual (05/11/2026)

- **Fundação pronta (06/11/2026):** Spring Boot 4.1.2, Spring Modulith 2.1.2, pacotes dos 7 módulos (`package-info.java`), `shared`
  (exceções + `GlobalExceptionHandler`, `Periodo`, `SecurityConfiguration` provisória sem JWT, `WebConfiguration`), `docker-compose.yml`
  (db, db-test, mailpit), JaCoCo e Spotless. Ainda **não há** entidades nem endpoints de negócio.
- Entregas: Documento/Apresentação final em **12–13/11/2026**; código no GitHub até **26/11/2026**.
- Cards do Trello (Backlog) que são deste repo, em ordem sugerida:
  1. **Configurar Infraestrutura Local** — `docker-compose.yml` + `init.sql` (Postgres sobe com o schema pronto).
  2. **Camada de Domínio** — entidades JPA, enums, repositories, exceptions + handler global (módulo `shared` + `model` de cada módulo).
  3. **Serviços e Segurança** — services (interface + `impl`), `JwtService`, `SecurityConfiguration`, `WebConfiguration`.
  4. **Camada de API** — DTOs, mappers, resources (cadastro, login, CRUD de lançamentos e categorias, dashboard).
  5. **Importação e Exportação em lote** — módulo `arquivo`.
  6. **Template CSV** (`template.csv`) — ver "Formato CSV".
  7. **Deploy** — Postgres + API em nuvem (ex.: Render), variáveis de ambiente.

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
docker compose up -d db mailpit    # Postgres (executa docker/init.sql na 1ª vez) + Mailpit (e-mails em http://localhost:8025)
./mvnw spring-boot:run             # API em http://localhost:8080 · Swagger em /swagger-ui.html
./mvnw test                        # testes unitários e web (integração é pulada sem POUPAMAIS_TEST_DB_URL)
./mvnw verify                      # tudo o que o CI roda: testes + ModularidadeTest + piso do JaCoCo
./mvnw spotless:apply              # formata o código: rode antes de commitar
./mvnw clean package -DskipTests   # gera o jar
docker compose down -v             # derruba e APAGA o volume (reaplica init.sql na próxima subida)
```

> `init.sql` só roda quando o volume está vazio. Mudou o DDL? `docker compose down -v` e suba de novo.

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
│   ├── config/        OpenApiConfig, WebConfiguration (CORS), JacksonConfig
│   ├── security/      SecurityConfiguration, JwtService, JwtAuthenticationFilter, UsuarioLogado
│   ├── exception/     RegraNegocioException, RecursoNaoEncontradoException, ConflitoException,
│   │                  ErroAutenticacaoException, ErroResponse, GlobalExceptionHandler
│   └── domain/        Periodo (value object mês/ano), NomeMes
├── lancamento/
│   ├── LancamentoFacade.java              # ← API PÚBLICA do módulo (interface); a única coisa que outros módulos usam
│   ├── LancamentoResumo.java              # ← records públicos devolvidos pela facade
│   ├── TipoLancamento.java                # ← enums públicos (outros módulos precisam)
│   ├── StatusLancamento.java          #   ATIVO, INATIVO, DELETADO
│   ├── api/                               # interno: Controller + DTO + Mapper
│   │   ├── LancamentoResource.java
│   │   ├── LancamentoMapper.java
│   │   └── dto/  LancamentoRequest, LancamentoResponse, StatusRequest, LancamentoFiltroParams
│   ├── service/                           # interno: regras de negócio
│   │   ├── LancamentoService.java
│   │   ├── LancamentoValidator.java
│   │   └── impl/ LancamentoServiceImpl.java   (também implementa LancamentoFacade)
│   └── model/                             # interno: persistência
│       ├── Lancamento.java, CategoriaLancamento.java, CategoriaLancamentoId.java
│       ├── LancamentoRepository.java
│       └── LancamentoSpecifications.java
├── usuario/   (api/: AuthResource, PerfilResource, PrivacidadeResource · service/: AuthService, PerfilService,
│               RecuperacaoSenhaService, PrivacidadeService · model/: Usuario, FotoPerfil, TokenRecuperacaoSenha)
├── notificacao/ (EmailFacade · service/impl/SmtpEmailService · templates/ do e-mail)
├── categoria/ (CategoriaFacade, CategoriaResumo · api/ service/ model/)
├── dashboard/ (api/ service/ · sem model: só lê pelos facades)
└── arquivo/   (api/ · importacao/ · exportacao/)
```

**Regra de ouro:** o que está na **raiz do módulo** é público; `api/`, `service/` e `model/` são internos.
Outro módulo **só** injeta a `XxxFacade` e usa os records/enums da raiz. **Nunca** o repository ou a entidade de outro módulo.

### Relacionamento entre módulos no banco

As tabelas têm FKs reais, mas **no código as entidades se referenciam por id** entre módulos:

- `Lancamento.idUsuario` é `Long` (não `@ManyToOne Usuario`); o mesmo vale para `Categoria.idUsuario`.
- `CategoriaLancamento` (dentro de `lancamento`) guarda `idCategoria` (`Long`) e `idUsuario`. Para mostrar os nomes,
  o mapper chama `CategoriaFacade.buscarPorIds(ids)` **uma vez por listagem** (sem N+1).
- Validar que as categorias existem, são do usuário e estão ativas: `CategoriaFacade.validarAtivasDoUsuario(ids)`.

## Design patterns (onde e por quê)

| Pattern | Onde | Por quê |
| --- | --- | --- |
| **MVC em camadas** | `api` → `service` → `model` em todo módulo | Controller fino (HTTP + validação de formato), regra no service, persistência no repository. |
| **Facade** | `CategoriaFacade`, `LancamentoFacade` na raiz do módulo | É a fronteira do módulo: esconde o interior, que pode mudar sem quebrar os outros. |
| **DTO + Mapper** | `api/dto` (records) + `XxxMapper` (`@Component`, mapeamento manual) | Nunca expor entidade JPA; o contrato da API fica estável. |
| **Repository** | Spring Data `JpaRepository` em `model/` | Acesso a dados padronizado, sempre filtrando pelo dono (RN005). |
| **Specification** | `LancamentoSpecifications` (descrição, mês, ano, tipo, status, categoria) | Filtros combináveis do RF005 **reaproveitados pela exportação** ("exporta o que está filtrado"). |
| **Strategy + Factory** | `arquivo.exportacao`: `ExportadorLancamentos` (interface) → `ExportadorCsv`, `ExportadorXlsx`; `ExportadorFactory` recebe `List<ExportadorLancamentos>` e escolhe pelo `formato` | Adicionar PDF amanhã = 1 classe nova, sem `if/else` espalhado. |
| **Validator** | `LancamentoValidator` | RN001 em **um só lugar**, usado pelo cadastro manual **e** pela importação CSV. |
| **Value Object** | `shared.domain.Periodo(mes, ano)` com `anterior()`, `ultimos(4)` e `nomeMes()` | RN008 (4 meses atravessando a virada do ano) sem conta de data espalhada. |
| **Enum com comportamento** | `dashboard.ClassificacaoTaxa.de(BigDecimal taxa)` | RN010 (70% / 100%) num só lugar, testável. |
| **Máquina de estados simples** | `StatusLancamento.podeIrPara(novo)` + `Lancamento.alterarStatus(novo)` | RN006: transições ATIVO/INATIVO/DELETADO validadas num lugar só. |
| **Chain of Responsibility** | `JwtAuthenticationFilter` na filter chain do Spring Security | Autenticação fora dos controllers. |
| **Exception Handler centralizado** | `shared.exception.GlobalExceptionHandler` (`@RestControllerAdvice`) | Formato de erro único (RNF007); nenhum `try/catch` em controller. |
| **Injeção de dependência por construtor** | Todo bean (`private final` + `@RequiredArgsConstructor`) | Facilita teste unitário com mocks; nada de `@Autowired` em campo. |

**Não usar** (seria complexidade sem ganho aqui): eventos de domínio entre módulos (o dashboard só lê), CQRS, microsserviços, MapStruct.

### O que é proibido (evita código macarrônico)

- Regra de negócio em controller, ou controller chamando repository.
- Entidade JPA em request/response.
- Um módulo importando `api/`, `service/` ou `model/` de outro. O teste do Modulith falha.
- `double`/`float` para dinheiro (usar `BigDecimal`), `@Autowired` em campo, `try/catch` que engole exceção.
- Service "deus": se passar de ~200 linhas ou misturar assuntos, extraia (ex.: `RateioCategorias`, `MensagemSaldoFactory`).
- `id_usuario` vindo do body/query: vem **sempre** do `UsuarioLogado` (token).

## Banco de dados

DDL oficial: `../Banco de Dados/poupamais_modelo_fisico.sql` → copiar para `docker/init.sql`. É a fonte da verdade; o Hibernate não cria tabelas.

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
- Segredo do JWT e credenciais via variável de ambiente (`JWT_SECRET`, `JWT_EXPIRACAO_MIN`, `DB_URL`, `DB_USER`, `DB_PASSWORD`,
  `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM`, `FRONT_URL` (monta o link de redefinição)), com default só para dev.
- CORS: o navegador **não** chama esta API (o Next faz as chamadas pelo servidor). Mantenha o `WebConfiguration` com `CORS_ORIGINS`
  só para Swagger e testes locais; em produção, restrinja à URL do front.

## Contrato da API (combinado com o front: manter os dois CLAUDE.md em sincronia)

Prefixo `/api`. JSON em camelCase. Header `Authorization: Bearer <token>`.

**Auth / usuário** (módulo `usuario`)

- `POST /api/auth/cadastro` `{nome, email, senha, aceiteTermos: true}` → `201` `{id, nome, email}` · e-mail duplicado → `409` · sem aceite → `400` (RF009, RF015)
- `POST /api/auth/login` `{email, senha}` → `200` `{token, expiraEm, usuario: {id, nome, email}}` · inválido → `401` (RF010)
- `POST /api/auth/recuperar-senha` `{email}` → `202` **sempre** (RF012, RN014)
- `POST /api/auth/redefinir-senha` `{token, novaSenha}` → `204` · token inválido, expirado ou usado → `422`

**Perfil e privacidade** (módulo `usuario`, RF013–RF017)

- `GET /api/usuarios/me` → `{id, nome, email, possuiFoto, dataCadastro}`
- `PUT /api/usuarios/me` `{nome}` → `200` (perfil)
- `PUT /api/usuarios/me/senha` `{senhaAtual, novaSenha}` → `200 {token, expiraEm}` · senha atual errada → `422` (não 401, para não derrubar a sessão)
- `GET /api/usuarios/me/foto` → bytes da imagem (`Content-Type` correto, `Cache-Control: no-cache`) · sem foto → `404`
- `PUT /api/usuarios/me/foto` (multipart, campo `foto`) → `204` · tipo/tamanho inválido → `422`
- `DELETE /api/usuarios/me/foto` → `204`
- `GET /api/usuarios/me/dados` → arquivo `meus-dados-poupamais.json` (RF016)
- `POST /api/usuarios/me/excluir` `{senha}` → `204` (RF017) · senha errada → `422`. É POST porque DELETE com body não é confiável em proxies.

Rotas públicas: `cadastro`, `login`, `recuperar-senha`, `redefinir-senha`.

**Categorias** (módulo `categoria`, RF011 / UC009). Não há tela própria no front: criar e editar são feitos por modais abertos
no dropdown de categorias do formulário de lançamento. Por isso o `POST` devolve a categoria criada, que o front já seleciona.

- `GET /api/categorias?ativa=true|false` (sem param = todas)
- `POST /api/categorias` `{descricao}` → `201 {id, descricao, ativa}` · duplicada (case-insensitive) → `409`
- `PUT /api/categorias/{id}` `{descricao}`
- `PATCH /api/categorias/{id}/status` `{ativa: boolean}`
- `DELETE /api/categorias/{id}` → `204`; se tiver lançamentos → `422` "Categoria vinculada a lançamentos, apenas inative"

**Lançamentos** (módulo `lancamento`, RF001–RF005, RF008 / UC001, UC002, UC004, UC010)

- `GET /api/lancamentos?descricao=&mes=&ano=&tipo=&status=&categoriaId=` → lista ordenada por ano/mês desc.
  Sem `status` = ATIVO + INATIVO (**nunca** DELETADO). `status=ATIVO|INATIVO|DELETADO` filtra só aquele (DELETADO = "Lixeira").
- `GET /api/lancamentos/{id}`
- `POST /api/lancamentos` `{descricao, tipo, mes, ano, valor, categoriaIds: [..]}` → `201`
- `PUT /api/lancamentos/{id}` (mesmo body) → atualiza e seta `dataAtualizacao` · se DELETADO → `422`
- `PATCH /api/lancamentos/{id}/status` `{status: "ATIVO"|"INATIVO"}` → atualiza `dataAtualizacao` · se DELETADO → `422`
- `DELETE /api/lancamentos/{id}` → `204`; **não apaga**: muda o status para DELETADO
- `POST /api/lancamentos/{id}/restaurar` → `200` (lançamento com status INATIVO) · se não estiver DELETADO → `422`

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

400 validação · 401 não autenticado · 404 não encontrado/não pertence ao usuário · 409 duplicidade · 422 regra de negócio · 500 genérico (sem stacktrace).

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
  cada linha válida é salva na sua própria transação (`LancamentoFacade.criar`), então uma falha não desfaz as outras (RN007).
- A exportação usa o **mesmo layout** (+ coluna `status`), para que um arquivo exportado possa ser reimportado.

## Convenções

- Código, classes, endpoints e mensagens em **português** (domínio do projeto); sem acento em identificadores.
- Nomes: `XxxResource` (controller), `XxxService` + `XxxServiceImpl`, `XxxRepository`, `XxxRequest`/`XxxResponse` (records), `XxxMapper`, `XxxFacade`.
- Validação de formato com Bean Validation nos DTOs (`@NotBlank`, `@Positive`, `@NotEmpty`, `@Size`…);
  regra de negócio no service, lançando as exceções de `shared.exception`.
- `@Transactional` nos métodos de escrita do service; `@Transactional(readOnly = true)` nas consultas.
- Dinheiro com `BigDecimal` e `RoundingMode` sempre explícito.
- Datas de auditoria: `dataCadastro` em `@PrePersist`; `dataAtualizacao` em todo update e troca de status.
- Toda funcionalidade nasce **com testes** (ver "Testes"). Sem teste, não está pronta.
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

- Integração roda só com `POUPAMAIS_TEST_DB_URL` definida (`@EnabledIfEnvironmentVariable`); no CI ela está **sempre** definida.
  Localmente: `docker compose up -d db-test` (porta 5433, schema do `docker/init.sql`) e
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
| `testes` (Testes e cobertura) | 1. checkout · 2. Java 21 Temurin com cache Maven · 3. aplica `docker/init.sql` no Postgres de serviço · 4. `spotless:check` · 5. `./mvnw -B -ntp verify` · 6. publica o relatório JaCoCo (sempre) e o do Surefire (se falhar) | formatação, qualquer teste, `ModularidadeTest` ou cobertura abaixo do piso |
| `compose` (Docker Compose) | valida `docker compose config -q` | compose inválido |

- Serviço do job `testes`: `postgres:17-alpine` com `POUPAMAIS_TEST_DB_URL` apontando para ele, então **os testes de integração sempre rodam no CI**.
- Rodar o mesmo localmente: skill `pipeline-local`.
- **Regra da equipe:** PR só é integrado com o CI verde. Configurar no GitHub: Settings → Branches → proteção da `development` e da `main`
  exigindo o check "Testes e cobertura" e 1 aprovação.
- Futuro (card de Deploy/Dockerfiles): job que constrói a imagem Docker e o deploy automático após o merge.

## Claude Code: skills e plugins

Skills do repo (`.claude/skills/`), usadas automaticamente pelo Claude quando o assunto aparece:

| Skill | Quando |
|---|---|
| `feature-backend` | Criar ou alterar qualquer funcionalidade da API (módulo certo, camadas, testes, contrato) |
| `testes-backend` | Escrever ou corrigir testes; cobertura do JaCoCo reprovando |
| `pipeline-local` | Antes de dizer "pronto", commitar ou abrir PR; CI falhou |
| `mudanca-de-regra` (na raiz do workspace) | O pedido contradiz o DVP, o DDL ou o contrato |

Plugins oficiais recomendados (instalar com `/plugin`): `feature-dev`, `pr-review-toolkit`, `commit-commands`, `security-guidance`, `context7` (documentação atualizada de Spring/JPA).

## Pontos em aberto (decidir com a equipe antes de implementar)

1. Paginação da listagem: não é exigida; a lista atual devolve tudo. Adicionar `page/size` se o volume crescer.
