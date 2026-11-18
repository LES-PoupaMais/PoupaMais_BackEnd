package br.com.poupamais.lancamento.publico.dto;

import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Dados de um lançamento a criar ou editar (RN001). */
public record NovoLancamento(
    String descricao,
    TipoLancamento tipo,
    LocalDate data,
    BigDecimal valor,
    List<Long> categoriaIds) {}
