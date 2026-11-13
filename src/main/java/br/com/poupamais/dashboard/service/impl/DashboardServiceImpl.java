package br.com.poupamais.dashboard.service.impl;

import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse;
import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse.MaiorDespesa;
import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse.MesResumo;
import br.com.poupamais.dashboard.service.ClassificacaoTaxa;
import br.com.poupamais.dashboard.service.DashboardService;
import br.com.poupamais.dashboard.service.MensagemSaldoFactory;
import br.com.poupamais.dashboard.service.RateioCategorias;
import br.com.poupamais.lancamento.publico.LancamentoFacade;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.shared.domain.Periodo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Monta o dashboard com uma única consulta à {@link LancamentoFacade} para os 4 meses (RN008), só
 * com lançamentos ATIVOS (RN003).
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

  static final int MESES_NO_GRAFICO = 4;
  static final int TAMANHO_RANKING = 5;
  private static final BigDecimal CEM = new BigDecimal("100");

  /** Maior valor primeiro; empate: menor id (o cadastrado antes). */
  private static final Comparator<LancamentoResumo> RANKING =
      Comparator.comparing(LancamentoResumo::valor).reversed().thenComparing(LancamentoResumo::id);

  private final LancamentoFacade lancamentos;
  private final RateioCategorias rateio;
  private final MensagemSaldoFactory mensagens;

  @Override
  public DashboardResponse consultar(Long idUsuario, Periodo periodo) {
    List<Periodo> periodos = periodo.ultimos(MESES_NO_GRAFICO);
    Map<Periodo, List<LancamentoResumo>> porMes =
        lancamentos.listarAtivos(idUsuario, periodos).stream()
            .collect(Collectors.groupingBy(l -> new Periodo(l.mes(), l.ano())));

    List<MesResumo> ultimosMeses =
        periodos.stream().map(p -> resumir(p, porMes.getOrDefault(p, List.of()))).toList();
    MesResumo selecionado = ultimosMeses.getLast();
    List<LancamentoResumo> doMes = porMes.getOrDefault(periodo, List.of());
    List<LancamentoResumo> despesas =
        doMes.stream().filter(l -> l.tipo() == TipoLancamento.DESPESA).toList();

    BigDecimal taxa = taxaGasto(selecionado.receitas(), selecionado.despesas());
    return new DashboardResponse(
        periodo.mes(),
        periodo.ano(),
        selecionado.receitas(),
        selecionado.despesas(),
        selecionado.saldo(),
        taxa,
        ClassificacaoTaxa.de(taxa),
        mensagens.criar(periodo, selecionado.saldo(), !doMes.isEmpty()),
        ultimosMeses,
        rateio.ratear(despesas),
        despesas.stream().sorted(RANKING).limit(TAMANHO_RANKING).map(this::maior).toList());
  }

  private MesResumo resumir(Periodo periodo, List<LancamentoResumo> doMes) {
    BigDecimal receitas = somar(doMes, TipoLancamento.RECEITA);
    BigDecimal despesas = somar(doMes, TipoLancamento.DESPESA);
    return new MesResumo(
        periodo.mes(), periodo.ano(), receitas, despesas, receitas.subtract(despesas));
  }

  private static BigDecimal somar(List<LancamentoResumo> doMes, TipoLancamento tipo) {
    return doMes.stream()
        .filter(l -> l.tipo() == tipo)
        .map(LancamentoResumo::valor)
        .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
  }

  /** Despesas / Receitas × 100 com 2 casas; {@code null} se não houver receita (RN003). */
  static BigDecimal taxaGasto(BigDecimal receitas, BigDecimal despesas) {
    if (receitas.signum() == 0) {
      return null;
    }
    return despesas.multiply(CEM).divide(receitas, 2, RoundingMode.HALF_UP);
  }

  private MaiorDespesa maior(LancamentoResumo despesa) {
    return new MaiorDespesa(
        despesa.id(),
        despesa.descricao(),
        despesa.valor(),
        despesa.categorias().stream().map(CategoriaResumo::descricao).toList());
  }
}
