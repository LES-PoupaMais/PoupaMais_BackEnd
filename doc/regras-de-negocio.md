# Regras de negócio e segurança

> Detalhe do guia (antes no `CLAUDE.md`). Índice no `AGENTS.md`.

## Regras de negócio (resumo do DVP — implementar no service do módulo indicado)

| RN    | Módulo | Regra |
| ----- | ------ | ----- |
| RN001 | lancamento | Obrigatórios: descrição (≤100), tipo, **data** (dia que exista, entre 01/01/2000 e 31/12/2100), valor > 0 e **≥ 1 categoria**. Revista em 18/11/2026: antes eram mês (1–12) e ano (2000–2100). Listagens e dashboard continuam filtrados por mês/ano. |
| RN002 | categoria / arquivo | Lançamento pode ter N categorias. Categoria pertence a um usuário; descrição única por usuário sem diferenciar maiúsc./minúsc. No CSV, categoria inexistente em linha válida é **criada automaticamente** (`CategoriaFacade.buscarOuCriar`). Categoria vinculada a lançamento (inclusive DELETADO) **não pode ser excluída**, só inativada. Categoria inativa não pode ser usada em lançamento novo. **No cadastro do usuário**, criar as categorias padrão: Alimentação, Educação, Freelance, Investimentos, Lazer, Moradia, Outros, Salário, Saúde, Transporte, Vestuário. |
| RN003 | dashboard | Só lançamentos **ATIVOS** cuja data cai no mês/ano (padrão = mês atual). Saldo = Receitas − Despesas. Taxa = Despesas / Receitas × 100; se Receitas = 0 → `null` (não disponível). |
| RN004 | lancamento / dashboard | Três status. **ATIVO**: entra no dashboard. **INATIVO**: aparece na listagem, mas não entra no dashboard. **DELETADO**: exclusão lógica (lixeira); não aparece na listagem padrão nem no dashboard, só com o filtro `status=DELETADO`. Nada é apagado fisicamente. |
| RN005 | todos | Usuário só vê/edita/inativa/exporta/exclui **o que é dele**. |
| RN006 | lancamento | ATIVO/INATIVO: editáveis em qualquer mês e alternam livremente; o status pode ir **junto** no POST/PUT (sem PATCH depois). DELETADO: **não pode ser editado nem alternado**; só **restaurar**, que devolve ao **status que tinha antes da exclusão** (ATIVO ou INATIVO; itens excluídos antes de 19/11/2026, sem esse registro, voltam como INATIVO). Transições válidas: ATIVO⇄INATIVO, ATIVO/INATIVO→DELETADO, DELETADO→ATIVO/INATIVO (só pelo restaurar). O "Desfazer" da exclusão (revisto em 19/11/2026): o front manda o `DELETE` **na hora** e o "Desfazer" chama o `restaurar`. |
| RN007 | arquivo | Importação parcial: salva as linhas válidas, ignora inválidas/em branco e devolve quais falharam e por quê. Nunca recusa o arquivo inteiro. |
| RN008 | dashboard | "Receitas vs Despesas" e "Saldo Mensal": 4 meses terminando no selecionado (`Periodo.ultimos(4)`), agrupando pelo mês da data. "Maiores Despesas": top 5 despesas ativas do mês, com categorias. |
| RN009 | dashboard | "Despesas por Categoria": só despesas ativas do mês. N categorias → valor dividido igualmente; **centavos de diferença vão para a primeira** (= menor `id_categoria`). Soma das fatias = total de despesas. |
| RN010 | dashboard | Taxa ≤ 70% "Dentro do limite"; > 70% e ≤ 100% "Atenção"; > 100% "Acima do limite". Mensagem descritiva: "Saldo positivo de R$ X em Agosto" / "As despesas superaram as receitas em R$ X em Agosto". **Nunca recomendar investimento** (RNI003). |
| RN011 | usuario | Cadastro: nome, e-mail válido e único, senha ≥ 6 com confirmação (a confirmação é validada no front). Senha só com hash BCrypt. A sessão expira (`JWT_EXPIRACAO_MIN`, padrão 480 = 8h). |
| RN012 | arquivo | Layout do CSV: ver "Formato CSV" (desde 18/11/2026 com a coluna `data` em `dd/mm/aaaa` no lugar de `mes`/`ano`). **Máx. 2 MB e 5.000 linhas de dados**; acima disso, ou sem o cabeçalho, recusa o arquivo inteiro (422) antes de processar. Template para download. |
| RN013 | arquivo | Exportação CSV/XLSX respeita os filtros; DELETADO só sai com `status=DELETADO`; sem resultados → 422. |
| RN014 | usuario | Recuperação de senha: link por e-mail, token aleatório (32 bytes) guardado como hash, **30 min**, uso único; novo pedido invalida os anteriores; resposta **sempre igual** (não revela se o e-mail existe); ao redefinir, grava `senha_alterada_em` (derruba todas as sessões). |
| RN015 | usuario | Perfil: nome obrigatório (≤150); **e-mail não editável**; foto JPG/PNG/WEBP ≤ 2 MB (validar pelo conteúdo, não só pela extensão); sem foto, o front mostra a inicial. Troca de senha: exige a senha atual, a nova segue a RN011 e é ≠ da atual; grava `senha_alterada_em` e **devolve um token novo** (a sessão atual continua, as outras caem). |
| RN016 | usuario | LGPD: cadastro exige `aceiteTermos=true` (grava `aceite_termos_em`); exportar dados = JSON com perfil, categorias e todos os lançamentos (inclusive INATIVO e DELETADO); excluir conta exige a senha, é **física e irreversível** (`DELETE` em `usuario`, cascata). |

Fora de escopo (requisitos inversos): app nativo (RNI001), integração bancária/Open Finance (RNI002), recomendações de investimento (RNI003).

### Segurança e isolamento (RN005 / RNF002 / RNF004)

- O id do usuário vem **sempre do token JWT**, via `UsuarioLogado.id()`, nunca do body ou da query.
- Todo repository de dado do usuário filtra por dono: `findByIdAndIdUsuario(...)` e Specifications sempre com `doUsuario(id)`.
  Recurso de outro usuário → **404** (não revelar que existe).
- Rotas públicas: `POST /api/auth/cadastro`, `/login`, `/recuperar-senha`, `/redefinir-senha`, `GET /actuator/health` e Swagger em dev. Todo o resto exige autenticação.
- **Actuator:** só o `health` é exposto (`management.endpoints.web.exposure.include=health`, página `/actuator` desligada), sem
  detalhes para ninguém (`show-details`/`show-components: never`). Qualquer outro `/actuator/**` exige token e responde `404`.
- Proteção de dados (RNF019): nunca logar senha, token ou valor financeiro; mensagens de erro sem detalhes internos.
- RNF006: **não** existe detecção de lançamento duplicado (dados iguais são válidos). A única proteção é contra reenvio técnico, feita no front.
- Senha: BCrypt, mínimo 6 caracteres (regra do protótipo). Nunca retornar `senha` em DTO.
- **Sessão de usuário excluído (feito):** `SenhaAlteradaConsulta.situacao(id)` devolve vazio quando o usuário não existe mais e o
  `JwtAuthenticationFilter` recusa o token (rota privada → 401). Com o módulo `usuario` presente, o bean sempre existe.
- **Swagger:** liberado só quando `SWAGGER_HABILITADO=true` (padrão `true` em dev, `false` em produção). Com `false`, o springdoc fica
  desligado (`springdoc.api-docs.enabled` e `springdoc.swagger-ui.enabled`) e as rotas do Swagger saem da lista pública.
- Segredo do JWT e credenciais via variável de ambiente (`JWT_SECRET`, `JWT_EXPIRACAO_MIN`, `DB_URL`, `DB_USER`, `DB_PASSWORD`,
  `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM`, `FRONT_URL` (monta o link de redefinição), `SWAGGER_HABILITADO`),
  com default só para dev.
- CORS: o navegador **não** chama esta API (o Next faz as chamadas pelo servidor). Mantenha o `WebConfiguration` com `CORS_ORIGINS`
  só para Swagger e testes locais; em produção, restrinja à URL do front.
