package br.com.poupamais.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse.DespesaCategoria;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateioCategoriasTest {

  private final RateioCategorias rateio = new RateioCategorias();

  private static CategoriaResumo cat(long id) {
    return new CategoriaResumo(id, "C" + id, true);
  }

  private static LancamentoResumo despesa(long id, String valor, CategoriaResumo... cats) {
    return new LancamentoResumo(
        id,
        "D" + id,
        TipoLancamento.DESPESA,
        8,
        2026,
        new BigDecimal(valor),
        StatusLancamento.ATIVO,
        List.of(cats),
        null,
        null);
  }

  private static BigDecimal soma(List<DespesaCategoria> fatias) {
    return fatias.stream().map(DespesaCategoria::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Test
  void deveDarOsCentavosQueSobramParaAMenorCategoriaComTresCategorias() {
    // categorias fora de ordem: a "primeira" é a de menor id
    List<DespesaCategoria> fatias =
        rateio.ratear(List.of(despesa(1, "100.00", cat(9), cat(2), cat(5))));

    assertThat(fatias).extracting(DespesaCategoria::categoriaId).containsExactly(2L, 5L, 9L);
    assertThat(fatias.get(0).valor()).isEqualByComparingTo("33.34");
    assertThat(fatias.get(1).valor()).isEqualByComparingTo("33.33");
    assertThat(fatias.get(2).valor()).isEqualByComparingTo("33.33");
    assertThat(soma(fatias)).isEqualByComparingTo("100.00");
  }

  @Test
  void deveDividirComCentavosEmDuasCategorias() {
    List<DespesaCategoria> fatias = rateio.ratear(List.of(despesa(1, "0.05", cat(1), cat(2))));

    assertThat(fatias.get(0).categoriaId()).isEqualTo(1L);
    assertThat(fatias.get(0).valor()).isEqualByComparingTo("0.03");
    assertThat(fatias.get(1).valor()).isEqualByComparingTo("0.02");
  }

  @Test
  void deveAtribuirTudoAUmaCategoriaSo() {
    List<DespesaCategoria> fatias = rateio.ratear(List.of(despesa(1, "420.00", cat(4))));

    assertThat(fatias).containsExactly(new DespesaCategoria(4L, "C4", new BigDecimal("420.00")));
  }

  @Test
  void deveAcumularVariasDespesasOrdenarPorValorDecrescenteEFecharOTotal() {
    List<LancamentoResumo> despesas =
        List.of(
            despesa(1, "10.00", cat(1), cat(2), cat(3)),
            despesa(2, "2100.00", cat(3)),
            despesa(3, "7.01", cat(2), cat(1)),
            despesa(4, "5.00", cat(1)));

    List<DespesaCategoria> fatias = rateio.ratear(despesas);

    assertThat(fatias).extracting(DespesaCategoria::categoriaId).containsExactly(3L, 1L, 2L);
    assertThat(fatias.get(0).valor()).isEqualByComparingTo("2103.33");
    assertThat(fatias.get(1).valor()).isEqualByComparingTo("11.85"); // 3.34 + 3.51 + 5.00
    assertThat(fatias.get(2).valor()).isEqualByComparingTo("6.83"); // 3.33 + 3.50
    assertThat(soma(fatias)).isEqualByComparingTo("2122.01");
  }

  @Test
  void deveDesempatarPorIdDaCategoriaEAgruparSemCategoriaNoFim() {
    List<DespesaCategoria> fatias =
        rateio.ratear(
            List.of(despesa(1, "5.00"), despesa(2, "5.00", cat(8)), despesa(3, "5", cat(3))));

    assertThat(fatias).extracting(DespesaCategoria::categoriaId).containsExactly(3L, 8L, null);
    assertThat(fatias.get(2).descricao()).isEqualTo(RateioCategorias.SEM_CATEGORIA);
    assertThat(soma(fatias)).isEqualByComparingTo("15.00");
  }

  @Test
  void deveDevolverListaVaziaSemDespesas() {
    assertThat(rateio.ratear(List.of())).isEmpty();
  }
}
