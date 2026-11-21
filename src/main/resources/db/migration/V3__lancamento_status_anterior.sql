-- RN006 revista (19/11/2026): restaurar da lixeira devolve o lançamento ao status que ele tinha antes
-- de ser excluído (ATIVO ou INATIVO). O status anterior é gravado ao mover para a lixeira (DELETADO)
-- e limpo ao restaurar. Itens que já estavam na lixeira antes desta migração ficam com NULL e voltam
-- como INATIVO (comportamento anterior).
-- NUNCA edite uma migração já aplicada: crie V<n>__descricao.sql.

ALTER TABLE poupamais.lancamento ADD COLUMN status_anterior varchar(20) NULL;

ALTER TABLE poupamais.lancamento
    ADD CONSTRAINT lancamento_status_anterior_check CHECK (status_anterior IN ('ATIVO', 'INATIVO'));

-- Só faz sentido enquanto o lançamento está na lixeira
ALTER TABLE poupamais.lancamento
    ADD CONSTRAINT lancamento_status_anterior_lixeira_check
        CHECK (status = 'DELETADO' OR status_anterior IS NULL);
