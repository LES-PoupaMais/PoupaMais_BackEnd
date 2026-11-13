# PoupaMais — BackEnd

API REST do **PoupaMais**, aplicação web de gestão financeira pessoal e familiar focada em letramento financeiro e na prevenção
do superendividamento (ODS 1 e 8). Projeto da disciplina de Laboratório de Engenharia de Software (LES) da Fatec Guarulhos.

**Equipe:** Bruno Sonoda · Leonardo Hideki · Leonardo Nascimento · Thalia Soares — **Orientação:** Prof. Jadir Custódio Júnior

O frontend (Next.js) fica em [LES-PoupaMais/PoupaMais_FrontEnd](https://github.com/LES-PoupaMais/PoupaMais_FrontEnd).

## Funcionalidades

- Cadastro, login, recuperação e troca de senha, perfil com foto
- Receitas e despesas com uma ou mais categorias; status Ativo, Inativo e Deletado (lixeira)
- Filtros, importação em lote por CSV (com tolerância a linhas inválidas) e exportação em CSV/Excel
- Dashboard mensal: receitas, despesas, saldo, taxa de gasto, últimos 4 meses, despesas por categoria e maiores despesas
- LGPD: aceite da Política de Privacidade, exportação dos próprios dados e exclusão da conta

## Tecnologias

Java 21 · Spring Boot (Web, Data JPA, Validation, Security, Mail) · **Spring Modulith** · PostgreSQL 17 · JWT · Apache POI e
Commons CSV · Maven Wrapper · Docker Compose · JUnit 5, Mockito, AssertJ e JaCoCo · GitHub Actions

## Arquitetura

**Monólito modular** com **MVC em camadas** dentro de cada módulo:

```
br.com.poupamais
├── shared        segurança (JWT), exceções e handler global, configurações, value objects
├── usuario       conta, autenticação, perfil, senha e LGPD
├── categoria     categorias do usuário
├── lancamento    receitas e despesas, filtros e status
├── dashboard     indicadores e gráficos
├── arquivo       importação CSV e exportação CSV/XLSX
└── notificacao   envio de e-mails
```

Cada módulo expõe só a pasta `publico/` (Facade, DTOs, enums e exceções públicas, marcada com `@NamedInterface`); `endpoints/`
(controllers, DTOs e mappers), `service/` e `model/` (`entidades/`, `repositories/`, `specifications/`) são internos.
As fronteiras são verificadas automaticamente pelo Spring Modulith (`ModularidadeTest`).
Padrões usados: Facade, DTO + Mapper, Repository, Specification, Strategy + Factory, Validator, Value Object e Exception Handler central.

## Como rodar

O schema do banco é criado pelo **Flyway** quando a API sobe (migrações em `src/main/resources/db/migration`).

**Opção 1 — só Docker** (não precisa de JDK):

```bash
cp .env.example .env               # opcional: os valores padrão servem para desenvolvimento
docker compose up -d --build       # PostgreSQL + Mailpit + API em http://localhost:8080
docker compose logs -f api         # acompanhar a API
docker compose down                # parar (mantém os dados)
```

**Opção 2 — Docker para a infra + API no Maven** (para desenvolver; pré-requisito **JDK 21+**, o Maven vem no wrapper):

```bash
docker compose up -d db mailpit    # PostgreSQL + Mailpit
./mvnw spring-boot:run             # API em http://localhost:8080 (o Flyway aplica as migrações)
```

| Endereço | O que é |
|---|---|
| http://localhost:8080/swagger-ui.html | Documentação interativa da API |
| http://localhost:8025 | Mailpit: caixa de entrada dos e-mails enviados em desenvolvimento |

### Banco de dados: migrações e reset

- Mudança de banco = **nova migração** `src/main/resources/db/migration/V<n>__descricao.sql` (ex.: `V2__adiciona_meta.sql`);
  ela é aplicada na próxima subida da API. **Nunca edite uma migração já aplicada**, só adicione novas.
- Resetar o banco de desenvolvimento (apaga os dados): `docker compose down -v && docker compose up -d --build`
  (ou `docker compose up -d db` e `./mvnw spring-boot:run`).

## Demonstração local

Não há deploy: a apresentação roda na máquina, com Docker.

1. `docker compose down -v && docker compose up -d --build` — banco zerado, schema criado pelo Flyway, API em http://localhost:8080.
2. Confira a API no Swagger (http://localhost:8080/swagger-ui.html) e os e-mails no Mailpit (http://localhost:8025).
3. Suba o front (ver o README do `PoupaMais_FrontEnd`) apontando `API_URL=http://localhost:8080`.
4. Roteiro: criar categoria → lançamento com duas categorias → importar CSV com uma linha inválida (modelo em
   `GET /api/lancamentos/template-csv`) → filtrar e inativar → dashboard → exportar.

## Testes

```bash
./mvnw test      # unitários e de controller
./mvnw verify    # tudo o que o CI roda, incluindo o piso de cobertura do JaCoCo
```

Testes de integração (PostgreSQL real) rodam quando `POUPAMAIS_TEST_DB_URL` está definida:

```bash
docker compose up -d db-test
export POUPAMAIS_TEST_DB_URL=jdbc:postgresql://localhost:5433/poupamais_test DB_USER=poupamais DB_PASSWORD=poupamais
./mvnw verify
```

Relatório de cobertura: `target/site/jacoco/index.html`.

## Pipeline (CI)

O GitHub Actions (`.github/workflows/ci.yml`) roda em todo Pull Request e em push na `development` e na `main`:

1. **Testes e cobertura**: formatação (Spotless), testes unitários, de controller, de integração (com PostgreSQL de serviço) e de
   arquitetura, e piso de cobertura do JaCoCo.
2. **Docker Compose**: valida o `docker-compose.yml`.

PR só é integrado (na `development` ou na `main`) com o CI verde.

## Variáveis de ambiente

| Variável | Uso | Padrão (dev) |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexão com o PostgreSQL | `jdbc:postgresql://localhost:5432/poupamais`, `poupamais`, `poupamais` |
| `JWT_SECRET`, `JWT_EXPIRACAO_MIN` | Assinatura e validade do token | segredo de dev, `480` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP | Mailpit (`localhost:1025`) |
| `FRONT_URL` | Monta o link de redefinição de senha | `http://localhost:3000` |
| `CORS_ORIGINS` | Origens liberadas (Swagger/dev) | `http://localhost:3000` |
| `SWAGGER_HABILITADO` | Liga o Swagger/OpenAPI e libera suas rotas sem token (`false` em produção) | `true` |
| `POUPAMAIS_TEST_DB_URL` | Liga os testes de integração | — |

## Fluxo de branches (Gitflow)

O projeto usa **Gitflow**, e toda funcionalidade nova entra **somente** por este caminho:

```
feat/nome-do-card  ──PR──►  development  ──PR (release)──►  main
```

1. Crie a branch a partir da `development`: `git switch development && git pull && git switch -c feat/importacao-csv`.
2. Ao terminar, abra um Pull Request de `feat/...` **para a `development`** (nunca direto para a `main`).
3. Com o CI verde e 1 aprovação, o PR é integrado na `development`.
4. Quando a `development` estiver estável para uma entrega, abre-se um Pull Request de `development` **para a `main`**.

Ninguém faz commit direto na `development` nem na `main`. A `main` contém só versões entregáveis.

## Contribuindo

- Uma branch `feat/...` por card do Trello, Conventional Commits (`feat(lancamento): ...`) e Pull Request para a `development` (ver Gitflow acima).
- Toda funcionalidade vem com testes. Rode `./mvnw spotless:apply` e `./mvnw verify` antes do PR.
- Regras de negócio, contrato da API e padrões de código: [`CLAUDE.md`](CLAUDE.md).

## Licença

[MIT](LICENSE)
