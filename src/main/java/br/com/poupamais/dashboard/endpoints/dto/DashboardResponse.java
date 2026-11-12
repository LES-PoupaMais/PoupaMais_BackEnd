package br.com.poupamais.dashboard.endpoints.dto;

import br.com.poupamais.dashboard.service.ClassificacaoTaxa;
import java.math.BigDecimal;
import java.util.List;

/** Corpo de {@code GET /api/dashboard} (contrato no CLAUDE.md). */
public record DashboardResponse(
    int mes,
    int ano,
    BigDecimal receitas,
    BigDecimal despesas,
    BigDecimal saldo,
    BigDecimal taxaGasto,
    ClassificacaoTaxa classificacaoTaxa,
    String mensagem,
    List<MesResumo> ultimosMeses,
    List<DespesaCategoria> despesasPorCategoria,
    List<MaiorDespesa> maioresDespesas) {

  public record MesResumo(
      int mes, int ano, BigDecimal receitas, BigDecimal despesas, BigDecimal saldo) {}

  public record DespesaCategoria(Long categoriaId, String descricao, BigDecimal valor) {}

  public record MaiorDespesa(
      Long id, String descricao, BigDecimal valor, List<String> categorias) {}
}
