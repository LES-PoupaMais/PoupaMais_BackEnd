# Decisões

Um arquivo por decisão que **não está escrita no código**: por que escolhemos um caminho, o que pesquisamos e o que descartamos.
Serve para a equipe e para qualquer agente de IA não refazer a mesma discussão.

- Nome: `AAAA-MM-DD-assunto-curto.md` (ex.: `2026-11-21-guia-dos-agentes-em-doc.md`).
- Entra no mesmo PR da mudança. Decisão revista: crie um arquivo novo e aponte para o antigo (não apague o histórico).
- Curto: cabe em uma tela.

## Modelo

```markdown
# <Título da decisão>

- Data: AAAA-MM-DD · Quem decidiu: <papel>
- Situação: aceita | substituída por <arquivo>

## Contexto
O problema e o que levou à decisão.

## Decisão
O que foi escolhido.

## Alternativas descartadas
- <opção>: por que não.

## Consequências
O que muda, o que fica mais difícil, o que conferir depois.
```
