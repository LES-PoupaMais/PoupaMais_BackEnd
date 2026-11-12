package br.com.poupamais.lancamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.poupamais.lancamento.NovoLancamento;
import br.com.poupamais.lancamento.TipoLancamento;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LancamentoValidatorTest {

  private final LancamentoValidator validator = new LancamentoValidator();

  private static NovoLancamento com(
      String descricao,
      TipoLancamento tipo,
      Integer mes,
      Integer ano,
      String valor,
      List<Long> categorias) {
    return new NovoLancamento(
        descricao, tipo, mes, ano, valor == null ? null : new BigDecimal(valor), categorias);
  }

  private static NovoLancamento valido() {
    return com("Mercado", TipoLancamento.DESPESA, 8, 2026, "10.5", List.of(1L));
  }

  private void deveRecusar(NovoLancamento dados, String mensagem) {
    assertThatThrownBy(() -> validator.validar(dados))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage(mensagem);
  }

  @Test
  void deveNormalizarDadosValidos() {
    NovoLancamento dados =
        com("  Mercado  ", TipoLancamento.DESPESA, 8, 2026, "10.5", List.of(3L, 1L, 3L));

    NovoLancamento normalizado = validator.validar(dados);

    assertThat(normalizado.descricao()).isEqualTo("Mercado");
    assertThat(normalizado.valor()).isEqualByComparingTo("10.50");
    assertThat(normalizado.valor().scale()).isEqualTo(2);
    assertThat(normalizado.categoriaIds()).containsExactly(3L, 1L);
  }

  @Test
  void deveRecusarDadosNulos() {
    deveRecusar(null, "A descrição é obrigatória");
  }

  @Test
  void deveExigirDescricao() {
    deveRecusar(
        com(null, TipoLancamento.DESPESA, 8, 2026, "1", List.of(1L)), "A descrição é obrigatória");
    deveRecusar(
        com("   ", TipoLancamento.DESPESA, 8, 2026, "1", List.of(1L)), "A descrição é obrigatória");
  }

  @Test
  void deveAceitarDescricaoCom100CaracteresERecusarCom101() {
    assertThat(
            validator
                .validar(com("a".repeat(100), TipoLancamento.RECEITA, 8, 2026, "1", List.of(1L)))
                .descricao())
        .hasSize(100);
    deveRecusar(
        com("a".repeat(101), TipoLancamento.RECEITA, 8, 2026, "1", List.of(1L)),
        "A descrição deve ter no máximo 100 caracteres");
  }

  @Test
  void deveExigirTipo() {
    deveRecusar(
        com("x", null, 8, 2026, "1", List.of(1L)), "O tipo é obrigatório (RECEITA ou DESPESA)");
  }

  @Test
  void deveValidarMesEntre1E12() {
    deveRecusar(
        com("x", TipoLancamento.RECEITA, null, 2026, "1", List.of(1L)),
        "O mês deve estar entre 1 e 12");
    deveRecusar(
        com("x", TipoLancamento.RECEITA, 0, 2026, "1", List.of(1L)),
        "O mês deve estar entre 1 e 12");
    deveRecusar(
        com("x", TipoLancamento.RECEITA, 13, 2026, "1", List.of(1L)),
        "O mês deve estar entre 1 e 12");
    assertThat(validator.validar(com("x", TipoLancamento.RECEITA, 1, 2026, "1", List.of(1L))).mes())
        .isEqualTo(1);
    assertThat(
            validator.validar(com("x", TipoLancamento.RECEITA, 12, 2026, "1", List.of(1L))).mes())
        .isEqualTo(12);
  }

  @Test
  void deveValidarAnoEntre2000E2100() {
    String msg = "O ano deve estar entre 2000 e 2100";
    deveRecusar(com("x", TipoLancamento.RECEITA, 1, null, "1", List.of(1L)), msg);
    deveRecusar(com("x", TipoLancamento.RECEITA, 1, 1999, "1", List.of(1L)), msg);
    deveRecusar(com("x", TipoLancamento.RECEITA, 1, 2101, "1", List.of(1L)), msg);
    assertThat(validator.validar(com("x", TipoLancamento.RECEITA, 1, 2000, "1", List.of(1L))).ano())
        .isEqualTo(2000);
    assertThat(validator.validar(com("x", TipoLancamento.RECEITA, 1, 2100, "1", List.of(1L))).ano())
        .isEqualTo(2100);
  }

  @Test
  void deveExigirValorPositivo() {
    deveRecusar(
        com("x", TipoLancamento.RECEITA, 1, 2026, null, List.of(1L)), "O valor é obrigatório");
    deveRecusar(
        com("x", TipoLancamento.RECEITA, 1, 2026, "0", List.of(1L)),
        "O valor deve ser maior que zero");
    deveRecusar(
        com("x", TipoLancamento.RECEITA, 1, 2026, "-5", List.of(1L)),
        "O valor deve ser maior que zero");
    deveRecusar(
        com("x", TipoLancamento.RECEITA, 1, 2026, "0.004", List.of(1L)),
        "O valor deve ser maior que zero");
    assertThat(
            validator
                .validar(com("x", TipoLancamento.RECEITA, 1, 2026, "0.01", List.of(1L)))
                .valor())
        .isEqualByComparingTo("0.01");
  }

  @Test
  void deveArredondarParaDuasCasasEValidarMaximo() {
    assertThat(
            validator
                .validar(com("x", TipoLancamento.RECEITA, 1, 2026, "1.005", List.of(1L)))
                .valor())
        .isEqualByComparingTo("1.01");
    assertThat(
            validator
                .validar(
                    com("x", TipoLancamento.RECEITA, 1, 2026, "99999999999999.99", List.of(1L)))
                .valor())
        .isEqualByComparingTo(LancamentoValidator.VALOR_MAXIMO);
    deveRecusar(
        com("x", TipoLancamento.RECEITA, 1, 2026, "100000000000000", List.of(1L)),
        "O valor excede o máximo permitido");
  }

  @Test
  void deveExigirPeloMenosUmaCategoria() {
    String msg = "Informe pelo menos uma categoria";
    deveRecusar(com("x", TipoLancamento.RECEITA, 1, 2026, "1", null), msg);
    deveRecusar(com("x", TipoLancamento.RECEITA, 1, 2026, "1", List.of()), msg);
    deveRecusar(com("x", TipoLancamento.RECEITA, 1, 2026, "1", Arrays.asList(null, null)), msg);
  }

  @Test
  void deveDescartarIdsNulosEntreValidos() {
    NovoLancamento dados = com("x", TipoLancamento.RECEITA, 1, 2026, "1", Arrays.asList(null, 4L));

    assertThat(validator.validar(dados).categoriaIds()).containsExactly(4L);
  }

  @Test
  void devePreservarTipoInformado() {
    assertThat(validator.validar(valido()).tipo()).isEqualTo(TipoLancamento.DESPESA);
  }
}
