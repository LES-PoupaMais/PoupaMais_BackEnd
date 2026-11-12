package br.com.poupamais.lancamento.api.dto;

import br.com.poupamais.lancamento.StatusLancamento;
import br.com.poupamais.lancamento.TipoLancamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LancamentoResponse(
    Long id,
    String descricao,
    TipoLancamento tipo,
    int mes,
    int ano,
    BigDecimal valor,
    StatusLancamento status,
    List<CategoriaDoLancamentoResponse> categorias,
    LocalDate dataCadastro,
    LocalDate dataAtualizacao) {}
