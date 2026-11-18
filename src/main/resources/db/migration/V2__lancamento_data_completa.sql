-- RN001 revista (18/11/2026): o lançamento passa a ter a data completa (dia, mês e ano) no lugar de mes/ano.
-- As listagens e o dashboard continuam filtrando por mês e ano (intervalo de datas do mês).
-- Auditoria com hora: data_cadastro/data_atualizacao viram timestamptz e entra data_exclusao (lixeira).
-- NUNCA edite uma migração já aplicada: crie V<n>__descricao.sql.

-- 1. Data completa. Os lançamentos antigos (só mês/ano) ficam no dia 1 do mês.
ALTER TABLE poupamais.lancamento ADD COLUMN data date NULL;

UPDATE poupamais.lancamento SET data = make_date(ano, mes, 1);

ALTER TABLE poupamais.lancamento ALTER COLUMN data SET NOT NULL;

ALTER TABLE poupamais.lancamento
    ADD CONSTRAINT lancamento_data_check CHECK (data BETWEEN DATE '2000-01-01' AND DATE '2100-12-31');

-- 2. Índice do dashboard e das listagens: usuário + intervalo de datas + status
DROP INDEX poupamais.lancamento_dashboard_idx;

CREATE INDEX lancamento_dashboard_idx
    ON poupamais.lancamento (id_usuario, data, status);

-- 3. mes/ano saem (os checks caem junto com as colunas; o DROP CONSTRAINT deixa isso explícito)
ALTER TABLE poupamais.lancamento DROP CONSTRAINT lancamento_mes_check;
ALTER TABLE poupamais.lancamento DROP CONSTRAINT lancamento_ano_check;
ALTER TABLE poupamais.lancamento DROP COLUMN mes;
ALTER TABLE poupamais.lancamento DROP COLUMN ano;

-- 4. Auditoria com hora. Os valores antigos (só data) viram meia-noite de Brasília.
ALTER TABLE poupamais.lancamento
    ALTER COLUMN data_cadastro DROP DEFAULT,
    ALTER COLUMN data_cadastro TYPE timestamptz USING data_cadastro::timestamp AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN data_cadastro SET DEFAULT now(),
    ALTER COLUMN data_atualizacao TYPE timestamptz USING data_atualizacao::timestamp AT TIME ZONE 'America/Sao_Paulo';

-- data_exclusao: preenchida ao mover para a lixeira (DELETADO) e limpa ao restaurar
ALTER TABLE poupamais.lancamento ADD COLUMN data_exclusao timestamptz NULL;
