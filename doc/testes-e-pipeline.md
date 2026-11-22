# Testes e pipeline

> Detalhe do guia (antes no `CLAUDE.md`). A definição de pronto fica no `AGENTS.md`.

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
  (subimos o piso junto quando a cobertura subir). Excluídos: `PoupaMaisApplication` e os `dto/` (records sem lógica).
- Nomes de teste em português descrevendo o comportamento: `deveRecusarArquivoComMaisDe5000Linhas()`.

## Pipeline (CI: GitHub Actions)

Arquivo: `.github/workflows/ci.yml`.

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
- Publicação fora do CI: o Render faz o build do `Dockerfile` e publica sozinho a cada merge na `main` (21/11/2026); o CI não
  gera imagem.
