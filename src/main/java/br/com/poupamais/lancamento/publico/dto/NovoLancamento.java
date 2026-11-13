package br.com.poupamais.lancamento.publico.dto;

import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import java.math.BigDecimal;
import java.util.List;

/** Dados de um lançamento a criar ou editar (RN001). */
public record NovoLancamento(
    String descricao,
    TipoLancamento tipo,
    Integer mes,
    Integer ano,
    BigDecimal valor,
    List<Long> categoriaIds) {}
