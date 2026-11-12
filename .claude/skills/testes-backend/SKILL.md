---
name: testes-backend
description: Use ao escrever, corrigir ou revisar testes da API do PoupaMais (JUnit 5, Mockito, AssertJ, @WebMvcTest, testes de integração com PostgreSQL real, ModularidadeTest do Spring Modulith) ou quando a cobertura do JaCoCo reprovar o build.
---

# Testes do backend

## Tipos e onde ficam

| Tipo | Nome da classe | Ferramentas | Roda quando |
|---|---|---|---|
| Unitário | `XxxServiceTest`, `PeriodoTest`, `RateioCategoriasTest` | JUnit 5, Mockito, AssertJ | Sempre (`./mvnw verify`) |
| Web (controller) | `XxxResourceTest` | `@WebMvcTest`, `MockMvc`, `@MockitoBean` | Sempre |
| Integração | `XxxIntegracaoTest` | `@SpringBootTest` + PostgreSQL real | Só com `POUPAMAIS_TEST_DB_URL` definida (no CI, sempre) |
| Arquitetura | `ModularidadeTest` | Spring Modulith `ApplicationModules.verify()` | Sempre |

O pacote do teste espelha o da classe testada (ex.: `br.com.poupamais.dashboard.service.impl`).

## Integração com banco real

```java
// Contra PostgreSQL real: cascata, CHECK e FK DEFERRABLE só se provam no banco.
// Sem POUPAMAIS_TEST_DB_URL o teste é pulado. Ver README.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class ExclusaoContaIntegracaoTest {
    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("POUPAMAIS_TEST_DB_URL"));
    }
}
```

- O banco **não** é limpo entre testes: gere dados únicos (e-mail com sufixo aleatório, por exemplo).
- Localmente: `docker compose up -d db-test` e `export POUPAMAIS_TEST_DB_URL=jdbc:postgresql://localhost:5433/poupamais_test`.
- O `db-test` nasce vazio: o Flyway aplica as mesmas migrações (`db/migration`) do desenvolvimento ao subir o contexto.

## Regras de escrita

- Nome do método em português descrevendo o comportamento: `deveRecusarArquivoComMaisDe5000Linhas()`.
- Um comportamento por teste; padrão Arrange / Act / Assert com linhas em branco entre as partes.
- Dinheiro: compare `BigDecimal` com `isEqualByComparingTo`.
- Teste a **mensagem** das exceções de regra: é o que o usuário vê (RNF007).
- Não teste getters/setters nem records sem lógica (o JaCoCo já os exclui).
- Casos que **não podem faltar**:
  - dashboard: receita zero (taxa `null`), virada de ano nos 4 meses, rateio de centavos (RN009), limites de 70% e 100%;
  - importação: linha inválida no meio, linha em branco, categoria nova, vírgula e ponto decimal, 2 MB e 5.000 linhas;
  - status: todas as transições válidas e inválidas (ATIVO, INATIVO, DELETADO);
  - isolamento (RN005): recurso de outro usuário retorna 404 em todo endpoint;
  - conta: token de recuperação expirado/usado, token JWT anterior a `senha_alterada_em`, exclusão de conta em cascata (integração).

## Cobertura

`./mvnw verify` gera `target/site/jacoco/index.html` e **falha abaixo do piso** definido no `pom.xml`
(`cobertura.linhas`, `cobertura.branches`). Abaixo do piso: escreva o teste que falta, não baixe o piso.
Quando a cobertura subir de forma estável, suba o piso junto.
