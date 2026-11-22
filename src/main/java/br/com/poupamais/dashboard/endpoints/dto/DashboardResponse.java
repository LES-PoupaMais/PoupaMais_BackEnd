package br.com.poupamais.dashboard.endpoints.dto;

import br.com.poupamais.dashboard.service.ClassificacaoTaxa;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Corpo de {@code GET /api/dashboard} (contrato em doc/contrato-api.md). */
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
    List<MaiorDespesa> maioresDespesas,
    PesoMaioresDespesas pesoMaioresDespesas) {

  public record MesResumo(
      int mes, int ano, BigDecimal receitas, BigDecimal despesas, BigDecimal saldo) {}

  public record DespesaCategoria(Long categoriaId, String descricao, BigDecimal valor) {}

  /** Uma das 5 maiores despesas do mês; {@code data} = data do lançamento ("AAAA-MM-DD"). */
  public record MaiorDespesa(
      Long id, String descricao, LocalDate data, BigDecimal valor, List<String> categorias) {}

  /**
   * Soma das {@code maioresDespesas} e o quanto ela pesa no total de despesas do mês (2 casas); sem
   * despesas: {@code valor} 0 e {@code percentual} {@code null}, como a {@code taxaGasto}.
   */
  public record PesoMaioresDespesas(BigDecimal valor, BigDecimal percentual) {}
}
