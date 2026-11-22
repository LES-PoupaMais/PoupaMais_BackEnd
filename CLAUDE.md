@AGENTS.md

## Só para o Claude Code: skills e plugins

O guia do projeto é o `AGENTS.md` (importado acima), que vale para qualquer IA. Aqui fica só o que é do Claude Code.

Skills do repo (`.claude/skills/`), usadas automaticamente pelo Claude quando o assunto aparece:

| Skill | Quando |
|---|---|
| `feature-backend` | Criar ou alterar qualquer funcionalidade da API (módulo certo, camadas, testes, contrato) |
| `testes-backend` | Escrever ou corrigir testes; cobertura do JaCoCo reprovando |
| `pipeline-local` | Antes de dizer "pronto", commitar ou abrir PR; CI falhou |
| `mudanca-de-regra` (na raiz do workspace) | O pedido contradiz o DVP, o DDL ou o contrato |

Plugins oficiais recomendados (instalar com `/plugin`): `feature-dev`, `pr-review-toolkit`, `commit-commands`, `security-guidance`, `context7` (documentação atualizada de Spring/JPA).
