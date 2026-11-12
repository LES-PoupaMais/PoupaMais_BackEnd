---
name: pipeline-local
description: Use antes de dizer que uma tarefa do backend do PoupaMais terminou, antes de commitar ou abrir PR, ou quando o CI do GitHub Actions falhar. Roda localmente os mesmos passos da pipeline (.github/workflows/ci.yml), incluindo os testes de integração com PostgreSQL descartável.
---

# Pipeline local do backend

**Definição de pronto:** nenhuma tarefa está finalizada sem rodar TODOS os passos abaixo com tudo verde.
Se algum passo não rodou ou algum teste de integração foi pulado, a tarefa **não está pronta**.

Mesmos passos do `.github/workflows/ci.yml`, na mesma ordem. O `db-test` nasce vazio: o Flyway cria o schema
(`src/main/resources/db/migration`) quando os testes sobem o contexto.

```bash
./mvnw spotless:apply                         # 0. formata
./mvnw -B -ntp spotless:check                 # 1. formatação
docker compose up -d db-test                  # 2. banco descartável dos testes de integração (porta 5433)
export POUPAMAIS_TEST_DB_URL=jdbc:postgresql://localhost:5433/poupamais_test
export DB_USER=poupamais DB_PASSWORD=poupamais
./mvnw -B -ntp verify                         # 3. unitários + integração + ModularidadeTest + piso do JaCoCo
docker compose config -q                      # 4. compose válido (job "Docker Compose" do CI)
```

- Falhou o `spotless:check`: rode `./mvnw spotless:apply` e confira o diff.
- Falhou o `ModularidadeTest`: algum módulo importou o interior de outro. Use a Facade (skill `feature-backend`).
- Falhou o JaCoCo (`Coverage checks have not been met`): veja `target/site/jacoco/index.html` e escreva os testes que faltam.
- Testes de integração pulados localmente: confira se `POUPAMAIS_TEST_DB_URL` está exportada (pulado = não pronto).
- Flyway reclamou de checksum (`Validate failed`): alguém editou migração já aplicada. Desfaça e crie `V<n>__...`;
  no banco local, `docker compose down -v` e suba de novo.
- Mudou `Dockerfile`/compose: confira também `docker compose up -d --build` (API saudável) e `docker compose down`.

Só relate "pronto" com tudo verde. Se algo falhar por motivo externo (Docker parado, rede), diga exatamente o que não rodou
e que, por isso, a tarefa não está pronta.
