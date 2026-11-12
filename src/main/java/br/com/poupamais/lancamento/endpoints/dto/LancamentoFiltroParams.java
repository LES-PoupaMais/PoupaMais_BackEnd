package br.com.poupamais.lancamento.endpoints.dto;

import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;

/** Parâmetros de query de {@code GET /api/lancamentos}; todos opcionais. */
public record LancamentoFiltroParams(
    String descricao,
    Integer mes,
    Integer ano,
    TipoLancamento tipo,
    StatusLancamento status,
    Long categoriaId) {}
