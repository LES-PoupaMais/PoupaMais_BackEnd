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

Cada módulo expõe apenas uma Facade pública; `api` (controllers e DTOs), `service` e `model` são internos.
As fronteiras são verificadas automaticamente pelo Spring Modulith (`ModularidadeTest`).
Padrões usados: Facade, DTO + Mapper, Repository, Specification, Strategy + Factory, Validator, Value Object e Exception Handler central.

## Como rodar

Pré-requisitos: **JDK 21+** e **Docker**. O Maven vem no wrapper (`./mvnw`).

```bash
cp .env.example .env               # ajuste se quiser (os valores padrão servem para desenvolvimento)
docker compose up -d db mailpit    # PostgreSQL (schema criado pelo docker/init.sql) + Mailpit
./mvnw spring-boot:run             # API em http://localhost:8080
```

| Endereço | O que é |
|---|---|
| http://localhost:8080/swagger-ui.html | Documentação interativa da API |
| http://localhost:8025 | Mailpit: caixa de entrada dos e-mails enviados em desenvolvimento |

> O `docker/init.sql` só roda com o volume vazio. Se o schema mudar: `docker compose down -v && docker compose up -d db`.

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
