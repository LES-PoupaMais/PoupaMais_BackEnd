package br.com.poupamais.arquivo.endpoints.dto;

import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;

/** Query de {@code GET /api/lancamentos/exportar}: o formato + os filtros da listagem. */
public record ExportacaoParams(
    String formato,
    String descricao,
    Integer mes,
    Integer ano,
    TipoLancamento tipo,
    StatusLancamento status,
    Long categoriaId) {}
