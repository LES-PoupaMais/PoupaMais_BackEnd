package br.com.poupamais.lancamento.api.dto;

import br.com.poupamais.lancamento.StatusLancamento;
import br.com.poupamais.lancamento.TipoLancamento;

/** Parâmetros de query de {@code GET /api/lancamentos}; todos opcionais. */
public record LancamentoFiltroParams(
    String descricao,
    Integer mes,
    Integer ano,
    TipoLancamento tipo,
    StatusLancamento status,
    Long categoriaId) {}
