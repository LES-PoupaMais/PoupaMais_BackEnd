# Banco de dados

> Detalhe do guia (antes no `CLAUDE.md`). Índice no `AGENTS.md`.

## Banco de dados

DDL em `src/main/resources/db/migration` (Flyway): é a fonte da verdade; o Hibernate não cria tabelas.
O `V1__schema_inicial.sql` veio do DDL oficial (`../Banco de Dados/poupamais_modelo_fisico.sql`). O `V2__lancamento_data_completa.sql`
(18/11/2026) troca `mes`/`ano` por `data date` (lançamentos antigos ficam no dia 1 do mês: `make_date(ano, mes, 1)`), troca o índice
`lancamento_dashboard_idx` para `(id_usuario, data, status)`, passa a auditoria do lançamento para `timestamptz` (valores antigos =
meia-noite de Brasília) e cria `data_exclusao timestamptz`. O `V3__lancamento_status_anterior.sql` (19/11/2026) cria
`status_anterior varchar(20) NULL` (CHECK `ATIVO`/`INATIVO`, e só preenchido com `status = 'DELETADO'`); itens que já estavam na
lixeira ficam com `NULL` e voltam como INATIVO.

- Mudança de banco = **nova migração** `V<n>__descricao.sql`. **Nunca editar migração já aplicada**; só adicionar.
- `spring.flyway`: schema `poupamais` criado e gerenciado pelo Flyway, histórico em `poupamais.flyway_schema_history`.
- `spring.jpa.hibernate.ddl-auto=validate` e `spring.jpa.properties.hibernate.default_schema=poupamais`.
- Tabelas: `usuario`, `categoria`, `lancamento`, `categorias_lancamentos`.
- Tipos: `valor numeric(16,2)` → **`BigDecimal`**; `lancamento.data` é `date` → `LocalDate`; `lancamento.data_cadastro`/`data_atualizacao`/
  `data_exclusao` são `timestamptz` → `OffsetDateTime` (`usuario`/`categoria.data_cadastro` continuam `date` → `LocalDate`);
  `tipo`/`status` são `varchar` → `@Enumerated(EnumType.STRING)`.
- Constraints que o banco já garante: e-mail único; descrição de categoria única por usuário **case-insensitive**
  (`lower(descricao)`); `data` entre 2000-01-01 e 2100-12-31; `valor > 0`; tipo/status nos valores dos enums.
  Mesmo assim **valide na aplicação antes**, para devolver mensagem amigável (RNF007) em vez de erro 500.
- **Atenção à tabela associativa:** `categorias_lancamentos` tem `id_usuario NOT NULL` replicado (FKs compostas
  `(id_categoria,id_usuario)` e `(id_lancamento,id_usuario)`). Um `@ManyToMany` + `@JoinTable` simples **não** preenche
  `id_usuario` e falha no insert. Mapeie como entidade `CategoriaLancamento` com `@EmbeddedId (idCategoria, idLancamento)`
  mais o campo `idUsuario`, e `Lancamento` com `@OneToMany(mappedBy, cascade = ALL, orphanRemoval = true)`.
- FK de categoria na associativa é `ON DELETE NO ACTION` **`DEFERRABLE INITIALLY DEFERRED`**: categoria com lançamentos não pode ser apagada
  (o erro só aparece no commit, por isso o service **verifica antes** e devolve 422), mas a exclusão da conta em cascata funciona.
  Testado em 06/11/2026 num Postgres 17: sem o `DEFERRABLE`, `DELETE FROM usuario` falhava.
- Tabelas da conta: `foto_perfil` (0..1 por usuário, `bytea` ≤ 2 MB, separada para não carregar a imagem em toda consulta) e
  `token_recuperacao_senha` (só o **hash SHA-256** do token, `expira_em`, `usado_em`).
- `usuario.aceite_termos_em` (LGPD) e `usuario.senha_alterada_em`: o `JwtAuthenticationFilter` recusa tokens com `iat` anterior a ela.
- E-mail sempre gravado e comparado em **minúsculas** (`trim().toLowerCase()`), porque o UNIQUE do banco diferencia maiúsculas.
