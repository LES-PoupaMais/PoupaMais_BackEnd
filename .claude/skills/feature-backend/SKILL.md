---
name: feature-backend
description: Use ao criar ou alterar qualquer funcionalidade da API do PoupaMais (endpoint, regra de negócio, entidade, consulta, importação/exportação, e-mail). Guia o passo a passo no monólito modular com MVC em camadas: módulo certo, DTO, mapper, service, resource, testes unitários e de integração, contrato nos dois CLAUDE.md e conferência com o DVP.
---

# Nova funcionalidade no backend

Leia antes: `CLAUDE.md` deste repo (arquitetura, patterns, contrato, regras RN001–RN016).

## 1. Conferir a regra

- Ache o RF/RN correspondente no `CLAUDE.md` (e, se existir o workspace, em `../Documentação/DVP_PoupaMais_v1.md`).
- Se o pedido **contradiz** uma regra documentada: pare e use a skill `mudanca-de-regra` (na raiz do workspace). Não implemente antes da decisão.

## 2. Escolher o módulo

| Assunto | Módulo |
|---|---|
| Conta, login, perfil, senha, LGPD | `usuario` |
| Categorias | `categoria` |
| Lançamentos, filtros, status/lixeira | `lancamento` |
| Indicadores e gráficos | `dashboard` |
| CSV/XLSX, template | `arquivo` |
| E-mail | `notificacao` |
| Algo usado por todos (segurança, erro, `Periodo`) | `shared` |

Precisa de dado de outro módulo? Use a **Facade** pública dele (raiz do módulo). Se o método não existe, acrescente-o na Facade:
**nunca** importe `api/`, `service/` ou `model/` de outro módulo (o `ModularidadeTest` quebra).

## 3. Implementar de baixo para cima

1. **model/**: entidade (se a tabela mudou, o DDL muda primeiro: `docker/init.sql`, a partir do DDL oficial), repository,
   Specification se tiver filtro. Toda consulta filtra por `idUsuario`.
2. **service/**: interface + `impl/`. Regra de negócio aqui; lança exceções de `shared.exception` com mensagem em português pronta
   para o usuário. `@Transactional` em escrita, `readOnly = true` em leitura. Dinheiro em `BigDecimal`.
3. **api/dto/**: `record` de request (Bean Validation com mensagens em português) e de response. Nunca expor entidade.
4. **api/XxxMapper**: `@Component`, conversão manual.
5. **api/XxxResource**: fino; pega o usuário com `UsuarioLogado.id()`, chama o service, devolve o status do contrato.

## 4. Testar (obrigatório; o CI reprova abaixo do piso de cobertura)

- **Unitário** do service (JUnit 5 + Mockito + AssertJ): caminho feliz, cada exceção da regra, limites (ex.: 70%/100%, 2 MB).
- **`@WebMvcTest`** do resource: status HTTP, validação (400 com `erros[]`), 401 sem token, 404 para recurso de outro usuário.
- **Integração** (`*IntegracaoTest`) quando a regra depende do banco: cascata, CHECK, FK `DEFERRABLE`, consulta com Specification.
- Detalhes e exemplos: skill `testes-backend`.

## 5. Fechar

- Endpoint novo ou alterado → atualize o **contrato** no `CLAUDE.md` deste repo **e** no `../PoupaMais_FrontEnd/CLAUDE.md`.
- Rode a skill `pipeline-local` (os mesmos passos do CI) antes de dizer que terminou.
