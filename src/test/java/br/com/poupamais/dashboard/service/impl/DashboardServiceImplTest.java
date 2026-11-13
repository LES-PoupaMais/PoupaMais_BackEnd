package br.com.poupamais.dashboard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse;
import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse.MaiorDespesa;
import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse.MesResumo;
import br.com.poupamais.dashboard.service.ClassificacaoTaxa;
import br.com.poupamais.dashboard.service.MensagemSaldoFactory;
import br.com.poupamais.dashboard.service.RateioCategorias;
import br.com.poupamais.lancamento.publico.LancamentoFacade;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.shared.domain.Periodo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

  private static final Long USUARIO = 7L;
  private static final CategoriaResumo MORADIA = new CategoriaResumo(2L, "Moradia", true);
  private static final CategoriaResumo LAZER = new CategoriaResumo(5L, "Lazer", true);

  @Mock private LancamentoFacade facade;
  private DashboardServiceImpl service;
  private long proximoId = 1;

  @BeforeEach
  void criar() {
    service = new DashboardServiceImpl(facade, new RateioCategorias(), new MensagemSaldoFactory());
  }

  private LancamentoResumo lanc(
      TipoLancamento tipo, int mes, int ano, String valor, CategoriaResumo... cats) {
    long id = proximoId++;
    return new LancamentoResumo(
        id,
        tipo + "-" + id,
        tipo,
        mes,
        ano,
        new BigDecimal(valor),
        StatusLancamento.ATIVO,
        List.of(cats),
        null,
        null);
  }

  private DashboardResponse consultar(Periodo periodo, List<LancamentoResumo> retorno) {
    when(facade.listarAtivos(USUARIO, periodo.ultimos(4))).thenReturn(retorno);
    return service.consultar(USUARIO, periodo);
  }

  @Test
  void deveCalcularTotaisTaxaEMensagemComUmaUnicaConsulta() {
    Periodo agosto = new Periodo(8, 2026);
    List<LancamentoResumo> dados =
        List.of(
            lanc(TipoLancamento.RECEITA, 8, 2026, "9700.00", MORADIA),
            lanc(TipoLancamento.DESPESA, 8, 2026, "2100.00", MORADIA),
            lanc(TipoLancamento.DESPESA, 8, 2026, "2680.00", MORADIA, LAZER),
            lanc(TipoLancamento.RECEITA, 6, 2026, "50.00", LAZER));

    DashboardResponse r = consultar(agosto, dados);

    assertThat(r.mes()).isEqualTo(8);
    assertThat(r.ano()).isEqualTo(2026);
    assertThat(r.receitas()).isEqualByComparingTo("9700.00");
    assertThat(r.despesas()).isEqualByComparingTo("4780.00");
    assertThat(r.saldo()).isEqualByComparingTo("4920.00");
    assertThat(r.taxaGasto()).isEqualByComparingTo("49.28");
    assertThat(r.classificacaoTaxa()).isEqualTo(ClassificacaoTaxa.DENTRO_DO_LIMITE);
    assertThat(r.mensagem()).isEqualTo("Saldo positivo de R$ 4.920,00 em Agosto");
    assertThat(r.despesasPorCategoria())
        .extracting(d -> d.categoriaId() + "=" + d.valor().toPlainString())
        .containsExactly("2=3440.00", "5=1340.00");
    verify(facade).listarAtivos(USUARIO, agosto.ultimos(4));
    verifyNoMoreInteractions(facade);
  }

  @Test
  void deveMontarQuatroMesesAtravessandoOAnoComZeros() {
    Periodo fevereiro = new Periodo(2, 2026);
    List<LancamentoResumo> dados =
        List.of(
            lanc(TipoLancamento.RECEITA, 12, 2025, "100.00", LAZER),
            lanc(TipoLancamento.DESPESA, 12, 2025, "30.00", LAZER),
            lanc(TipoLancamento.DESPESA, 2, 2026, "10.00", LAZER));

    List<MesResumo> meses = consultar(fevereiro, dados).ultimosMeses();

    assertThat(meses)
        .extracting(m -> m.mes() + "/" + m.ano())
        .containsExactly("11/2025", "12/2025", "1/2026", "2/2026");
    assertThat(meses.get(0).receitas()).isEqualByComparingTo("0");
    assertThat(meses.get(0).saldo()).isEqualByComparingTo("0");
    assertThat(meses.get(1).saldo()).isEqualByComparingTo("70.00");
    assertThat(meses.get(2).despesas()).isEqualByComparingTo("0");
    assertThat(meses.get(3).saldo()).isEqualByComparingTo("-10.00");
  }

  @Test
  void deveDevolverTaxaNullSemReceitas() {
    DashboardResponse r =
        consultar(
            new Periodo(3, 2026), List.of(lanc(TipoLancamento.DESPESA, 3, 2026, "80.00", LAZER)));

    assertThat(r.taxaGasto()).isNull();
    assertThat(r.classificacaoTaxa()).isNull();
    assertThat(r.mensagem()).isEqualTo("As despesas superaram as receitas em R$ 80,00 em Março");
  }

  @Test
  void deveClassificarAcimaDoLimiteEArredondarTaxa() {
    DashboardResponse r =
        consultar(
            new Periodo(5, 2026),
            List.of(
                lanc(TipoLancamento.RECEITA, 5, 2026, "300.00", LAZER),
                lanc(TipoLancamento.DESPESA, 5, 2026, "301.00", LAZER)));

    assertThat(r.taxaGasto()).isEqualByComparingTo("100.33");
    assertThat(r.classificacaoTaxa()).isEqualTo(ClassificacaoTaxa.ACIMA_DO_LIMITE);
  }

  @Test
  void deveResponderMesVazioComZerosEListasVazias() {
    DashboardResponse r = consultar(new Periodo(1, 2026), List.of());

    assertThat(r.receitas()).isEqualByComparingTo("0");
    assertThat(r.saldo()).isEqualByComparingTo("0");
    assertThat(r.taxaGasto()).isNull();
    assertThat(r.mensagem()).isEqualTo("Nenhum lançamento ativo em Janeiro");
    assertThat(r.ultimosMeses()).hasSize(4);
    assertThat(r.despesasPorCategoria()).isEmpty();
    assertThat(r.maioresDespesas()).isEmpty();
  }

  @Test
  void deveListarAsCincoMaioresDespesasDoMesComDesempatePorId() {
    List<LancamentoResumo> dados = new ArrayList<>();
    dados.add(lanc(TipoLancamento.RECEITA, 8, 2026, "99999.00", LAZER));
    dados.add(lanc(TipoLancamento.DESPESA, 7, 2026, "5000.00", LAZER)); // outro mês
    LancamentoResumo empate1 = lanc(TipoLancamento.DESPESA, 8, 2026, "50.00", LAZER);
    LancamentoResumo maior = lanc(TipoLancamento.DESPESA, 8, 2026, "900.00", MORADIA, LAZER);
    LancamentoResumo empate2 = lanc(TipoLancamento.DESPESA, 8, 2026, "50.00", MORADIA);
    LancamentoResumo m3 = lanc(TipoLancamento.DESPESA, 8, 2026, "70.00", LAZER);
    LancamentoResumo m4 = lanc(TipoLancamento.DESPESA, 8, 2026, "60.00", LAZER);
    LancamentoResumo fora = lanc(TipoLancamento.DESPESA, 8, 2026, "10.00", LAZER);
    // facade devolve em ordem desc de id: a ordem de entrada não pode influenciar
    dados.addAll(List.of(fora, m4, m3, empate2, maior, empate1));

    List<MaiorDespesa> ranking = consultar(new Periodo(8, 2026), dados).maioresDespesas();

    assertThat(ranking)
        .extracting(MaiorDespesa::id)
        .containsExactly(maior.id(), m3.id(), m4.id(), empate1.id(), empate2.id());
    assertThat(ranking.get(0).categorias()).containsExactly("Moradia", "Lazer");
    assertThat(ranking.get(0).valor()).isEqualByComparingTo("900.00");
  }

  @Test
  void deveClassificarAtencaoComSaldoZero() {
    DashboardResponse r =
        consultar(
            new Periodo(4, 2026),
            List.of(
                lanc(TipoLancamento.RECEITA, 4, 2026, "500.00", LAZER),
                lanc(TipoLancamento.DESPESA, 4, 2026, "500.00", LAZER)));

    assertThat(r.taxaGasto()).isEqualByComparingTo("100.00");
    assertThat(r.classificacaoTaxa()).isEqualTo(ClassificacaoTaxa.ATENCAO);
    assertThat(r.mensagem())
        .isEqualTo("Receitas e despesas se igualaram em Abril: saldo de R$ 0,00");
  }
}
