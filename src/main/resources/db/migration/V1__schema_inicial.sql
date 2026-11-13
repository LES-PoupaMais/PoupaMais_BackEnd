-- Schema inicial do PoupaMais (6 tabelas). O schema poupamais é criado pelo Flyway (spring.flyway).
-- NUNCA edite uma migração já aplicada: crie V<n>__descricao.sql.

-- ---------------------------------------------------------------------
-- USUARIO
-- email: gravado sempre em minúsculas pela aplicação (login sem diferenciar maiúsculas)
-- aceite_termos_em: data/hora do aceite da Política de Privacidade (LGPD, RF015)
-- senha_alterada_em: tokens JWT emitidos antes desta data/hora são recusados (RN014/RN015)
-- ---------------------------------------------------------------------
CREATE TABLE poupamais.usuario (
    id                bigserial    NOT NULL,
    nome              varchar(150) NOT NULL,
    email             varchar(100) NOT NULL,
    senha             varchar(255) NOT NULL,
    data_cadastro     date         NOT NULL DEFAULT now(),
    aceite_termos_em  timestamptz  NOT NULL,
    senha_alterada_em timestamptz  NULL,
    CONSTRAINT usuario_pkey     PRIMARY KEY (id),
    CONSTRAINT usuario_email_uk UNIQUE (email)
);

-- ---------------------------------------------------------------------
-- FOTO_PERFIL (0..1 por usuário; sem foto, a interface mostra a inicial do nome)
-- Tabela separada para a imagem não ser carregada junto com o usuário em toda requisição
-- ---------------------------------------------------------------------
CREATE TABLE poupamais.foto_perfil (
    id_usuario       bigint      NOT NULL,
    conteudo         bytea       NOT NULL,
    tipo_conteudo    varchar(30) NOT NULL,
    data_atualizacao timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT foto_perfil_pkey       PRIMARY KEY (id_usuario),
    CONSTRAINT foto_perfil_usuario_fk FOREIGN KEY (id_usuario)
        REFERENCES poupamais.usuario (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT foto_perfil_tipo_check CHECK (tipo_conteudo IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT foto_perfil_tamanho_check CHECK (octet_length(conteudo) <= 2097152)
);

-- ---------------------------------------------------------------------
-- TOKEN_RECUPERACAO_SENHA (RF012 / RN014)
-- Guarda só o hash (SHA-256) do token enviado por e-mail; uso único, expira em 30 minutos
-- ---------------------------------------------------------------------
CREATE TABLE poupamais.token_recuperacao_senha (
    id            bigserial   NOT NULL,
    id_usuario    bigint      NOT NULL,
    token_hash    varchar(64) NOT NULL,
    expira_em     timestamptz NOT NULL,
    usado_em      timestamptz NULL,
    data_cadastro timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT token_recuperacao_senha_pkey       PRIMARY KEY (id),
    CONSTRAINT token_recuperacao_senha_hash_uk    UNIQUE (token_hash),
    CONSTRAINT token_recuperacao_senha_usuario_fk FOREIGN KEY (id_usuario)
        REFERENCES poupamais.usuario (id) ON DELETE CASCADE ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- CATEGORIA (pertence a um único usuário)
-- ---------------------------------------------------------------------
CREATE TABLE poupamais.categoria (
    id            bigserial    NOT NULL,
    id_usuario    bigint       NOT NULL,
    descricao     varchar(255) NOT NULL,
    ativa         boolean      NOT NULL DEFAULT true,
    data_cadastro date         NOT NULL DEFAULT now(),
    CONSTRAINT categoria_pkey          PRIMARY KEY (id),
    CONSTRAINT categoria_id_usuario_uk UNIQUE (id, id_usuario),
    CONSTRAINT categoria_usuario_fk    FOREIGN KEY (id_usuario)
        REFERENCES poupamais.usuario (id) ON DELETE CASCADE ON UPDATE CASCADE
);

-- descrição única por usuário, sem diferenciar maiúsculas de minúsculas
CREATE UNIQUE INDEX categoria_usuario_descricao_uk
    ON poupamais.categoria (id_usuario, lower(descricao));

-- ---------------------------------------------------------------------
-- LANCAMENTO
-- status: ATIVO    = entra nos cálculos do Dashboard
--         INATIVO  = fica na listagem, mas não entra nos cálculos do Dashboard
--         DELETADO = exclusão lógica (lixeira): some da listagem padrão, não entra
--                    no Dashboard, não pode ser editado; restaurar volta para INATIVO
-- ---------------------------------------------------------------------
CREATE TABLE poupamais.lancamento (
    id               bigserial     NOT NULL,
    id_usuario       bigint        NOT NULL,
    descricao        varchar(100)  NOT NULL,
    mes              int4          NOT NULL,
    ano              int4          NOT NULL,
    valor            numeric(16,2) NOT NULL,
    tipo             varchar(20)   NOT NULL,
    status           varchar(20)   NOT NULL DEFAULT 'ATIVO',
    data_cadastro    date          NOT NULL DEFAULT now(),
    data_atualizacao date          NULL,
    CONSTRAINT lancamento_pkey          PRIMARY KEY (id),
    CONSTRAINT lancamento_id_usuario_uk UNIQUE (id, id_usuario),
    CONSTRAINT lancamento_usuario_fk    FOREIGN KEY (id_usuario)
        REFERENCES poupamais.usuario (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT lancamento_mes_check     CHECK (mes BETWEEN 1 AND 12),
    CONSTRAINT lancamento_ano_check     CHECK (ano BETWEEN 2000 AND 2100),
    CONSTRAINT lancamento_valor_check   CHECK (valor > 0),
    CONSTRAINT lancamento_tipo_check    CHECK (tipo IN ('RECEITA', 'DESPESA')),
    CONSTRAINT lancamento_status_check  CHECK (status IN ('ATIVO', 'INATIVO', 'DELETADO'))
);

-- apoio às consultas do Dashboard (usuário + mês/ano + status)
CREATE INDEX lancamento_dashboard_idx
    ON poupamais.lancamento (id_usuario, ano, mes, status);

-- ---------------------------------------------------------------------
-- CATEGORIAS_LANCAMENTOS (associativa N:N)
-- id_usuario replicado: garante que lançamento e categoria sejam do mesmo usuário
-- A regra "todo lançamento tem pelo menos uma categoria" é validada na aplicação
-- ---------------------------------------------------------------------
CREATE TABLE poupamais.categorias_lancamentos (
    id_categoria  bigint NOT NULL,
    id_lancamento bigint NOT NULL,
    id_usuario    bigint NOT NULL,
    CONSTRAINT categorias_lancamentos_pkey PRIMARY KEY (id_categoria, id_lancamento),
    CONSTRAINT categorias_lancamentos_categoria_fk FOREIGN KEY (id_categoria, id_usuario)
        REFERENCES poupamais.categoria (id, id_usuario) ON DELETE NO ACTION ON UPDATE CASCADE
        DEFERRABLE INITIALLY DEFERRED, -- checada no fim da transação: permite excluir a conta (cascata), mas
                                       -- continua impedindo excluir categoria vinculada a lançamento (RN002)
    CONSTRAINT categorias_lancamentos_lancamento_fk FOREIGN KEY (id_lancamento, id_usuario)
        REFERENCES poupamais.lancamento (id, id_usuario) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX categorias_lancamentos_lancamento_idx
    ON poupamais.categorias_lancamentos (id_lancamento);
