---
name: pipeline-local
description: Use antes de dizer que uma tarefa do backend do PoupaMais terminou, antes de commitar ou abrir PR, ou quando o CI do GitHub Actions falhar. Roda localmente os mesmos passos da pipeline (.github/workflows/ci.yml), incluindo os testes de integração com PostgreSQL descartável.
---

# Pipeline local do backend

Mesmos passos do `.github/workflows/ci.yml`, na mesma ordem:

```bash
./mvnw -B -ntp spotless:check                 # 1. formatação (corrigir com ./mvnw spotless:apply)
docker compose up -d db-test                  # 2. banco descartável dos testes de integração (porta 5433)
export POUPAMAIS_TEST_DB_URL=jdbc:postgresql://localhost:5433/poupamais_test
export DB_USER=poupamais DB_PASSWORD=poupamais
./mvnw -B -ntp verify                         # 3. unitários + integração + ModularidadeTest + piso do JaCoCo
```

- Falhou o `spotless:check`: rode `./mvnw spotless:apply` e confira o diff.
- Falhou o `ModularidadeTest`: algum módulo importou o interior de outro. Use a Facade (skill `feature-backend`).
- Falhou o JaCoCo (`Coverage checks have not been met`): veja `target/site/jacoco/index.html` e escreva os testes que faltam.
- Testes de integração pulados localmente: confira se `POUPAMAIS_TEST_DB_URL` está exportada.

Só relate "pronto" com tudo verde. Se algo falhar por motivo externo (Docker parado, rede), diga exatamente o que não rodou.
