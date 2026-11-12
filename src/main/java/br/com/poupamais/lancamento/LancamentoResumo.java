package br.com.poupamais.lancamento;

import br.com.poupamais.categoria.CategoriaResumo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Visão pública de um lançamento. {@code categorias} vem ordenada por id crescente: a primeira é a
 * "primeira categoria" da RN009.
 */
public record LancamentoResumo(
    Long id,
    String descricao,
    TipoLancamento tipo,
    int mes,
    int ano,
    BigDecimal valor,
    StatusLancamento status,
    List<CategoriaResumo> categorias,
    LocalDate dataCadastro,
    LocalDate dataAtualizacao) {}
