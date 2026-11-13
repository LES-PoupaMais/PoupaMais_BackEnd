package br.com.poupamais.arquivo.service.importacao;

import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import java.math.BigDecimal;
import java.util.List;

/** Campos de uma linha já convertidos; {@code categorias} sem repetição (ignorando maiúsculas). */
public record DadosLinha(
    String descricao,
    TipoLancamento tipo,
    int mes,
    int ano,
    BigDecimal valor,
    List<String> categorias) {}
