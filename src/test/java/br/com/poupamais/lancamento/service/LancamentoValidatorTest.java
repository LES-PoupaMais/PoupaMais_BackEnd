package br.com.poupamais.lancamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LancamentoValidatorTest {

  private static final LocalDate DATA = LocalDate.of(2026, 8, 15);

  private final LancamentoValidator validator = new LancamentoValidator();

  private static NovoLancamento com(
      String descricao, TipoLancamento tipo, LocalDate data, String valor, List<Long> categorias) {
    return new NovoLancamento(
        descricao, tipo, data, valor == null ? null : new BigDecimal(valor), categorias);
  }

  private static NovoLancamento valido() {
    return com("Mercado", TipoLancamento.DESPESA, DATA, "10.5", List.of(1L));
  }

  private void deveRecusar(NovoLancamento dados, String mensagem) {
    assertThatThrownBy(() -> validator.validar(dados))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage(mensagem);
  }

  @Test
  void deveNormalizarDadosValidos() {
    NovoLancamento dados =
        com("  Mercado  ", TipoLancamento.DESPESA, DATA, "10.5", List.of(3L, 1L, 3L));

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
        com(null, TipoLancamento.DESPESA, DATA, "1", List.of(1L)), "A descrição é obrigatória");
    deveRecusar(
        com("   ", TipoLancamento.DESPESA, DATA, "1", List.of(1L)), "A descrição é obrigatória");
  }

  @Test
  void deveAceitarDescricaoCom100CaracteresERecusarCom101() {
    assertThat(
            validator
                .validar(com("a".repeat(100), TipoLancamento.RECEITA, DATA, "1", List.of(1L)))
                .descricao())
        .hasSize(100);
    deveRecusar(
        com("a".repeat(101), TipoLancamento.RECEITA, DATA, "1", List.of(1L)),
        "A descrição deve ter no máximo 100 caracteres");
  }

  @Test
  void deveExigirTipo() {
    deveRecusar(
        com("x", null, DATA, "1", List.of(1L)), "O tipo é obrigatório (RECEITA ou DESPESA)");
  }

  @Test
  void deveExigirData() {
    deveRecusar(com("x", TipoLancamento.RECEITA, null, "1", List.of(1L)), "A data é obrigatória");
  }

  @Test
  void deveAceitarDataEntre01012000E31122100ERecusarFora() {
    String msg = "A data deve estar entre 01/01/2000 e 31/12/2100";
    deveRecusar(
        com("x", TipoLancamento.RECEITA, LocalDate.of(1999, 12, 31), "1", List.of(1L)), msg);
    deveRecusar(com("x", TipoLancamento.RECEITA, LocalDate.of(2101, 1, 1), "1", List.of(1L)), msg);
    assertThat(
            validator
                .validar(
                    com("x", TipoLancamento.RECEITA, LocalDate.of(2000, 1, 1), "1", List.of(1L)))
                .data())
        .isEqualTo(LocalDate.of(2000, 1, 1));
    assertThat(
            validator
                .validar(
                    com("x", TipoLancamento.RECEITA, LocalDate.of(2100, 12, 31), "1", List.of(1L)))
                .data())
        .isEqualTo(LocalDate.of(2100, 12, 31));
  }

  @Test
  void devePreservarODiaDaData() {
    LocalDate bissexto = LocalDate.of(2028, 2, 29);

    NovoLancamento normalizado =
        validator.validar(com("x", TipoLancamento.RECEITA, bissexto, "1", List.of(1L)));

    assertThat(normalizado.data()).isEqualTo(bissexto);
  }

  @Test
  void deveExigirValorPositivo() {
    deveRecusar(com("x", TipoLancamento.RECEITA, DATA, null, List.of(1L)), "O valor é obrigatório");
    deveRecusar(
        com("x", TipoLancamento.RECEITA, DATA, "0", List.of(1L)),
        "O valor deve ser maior que zero");
    deveRecusar(
        com("x", TipoLancamento.RECEITA, DATA, "-5", List.of(1L)),
        "O valor deve ser maior que zero");
    deveRecusar(
        com("x", TipoLancamento.RECEITA, DATA, "0.004", List.of(1L)),
        "O valor deve ser maior que zero");
    assertThat(
            validator.validar(com("x", TipoLancamento.RECEITA, DATA, "0.01", List.of(1L))).valor())
        .isEqualByComparingTo("0.01");
  }

  @Test
  void deveArredondarParaDuasCasasEValidarMaximo() {
    assertThat(
            validator.validar(com("x", TipoLancamento.RECEITA, DATA, "1.005", List.of(1L))).valor())
        .isEqualByComparingTo("1.01");
    assertThat(
            validator
                .validar(com("x", TipoLancamento.RECEITA, DATA, "99999999999999.99", List.of(1L)))
                .valor())
        .isEqualByComparingTo(LancamentoValidator.VALOR_MAXIMO);
    deveRecusar(
        com("x", TipoLancamento.RECEITA, DATA, "100000000000000", List.of(1L)),
        "O valor excede o máximo permitido");
  }

  @Test
  void deveExigirPeloMenosUmaCategoria() {
    String msg = "Informe pelo menos uma categoria";
    deveRecusar(com("x", TipoLancamento.RECEITA, DATA, "1", null), msg);
    deveRecusar(com("x", TipoLancamento.RECEITA, DATA, "1", List.of()), msg);
    deveRecusar(com("x", TipoLancamento.RECEITA, DATA, "1", Arrays.asList(null, null)), msg);
  }

  @Test
  void deveDescartarIdsNulosEntreValidos() {
    NovoLancamento dados = com("x", TipoLancamento.RECEITA, DATA, "1", Arrays.asList(null, 4L));

    assertThat(validator.validar(dados).categoriaIds()).containsExactly(4L);
  }

  @Test
  void devePreservarTipoInformado() {
    assertThat(validator.validar(valido()).tipo()).isEqualTo(TipoLancamento.DESPESA);
  }
}
