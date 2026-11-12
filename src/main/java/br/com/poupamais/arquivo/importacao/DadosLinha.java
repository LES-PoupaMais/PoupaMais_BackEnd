package br.com.poupamais.arquivo.importacao;

import br.com.poupamais.lancamento.TipoLancamento;
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
