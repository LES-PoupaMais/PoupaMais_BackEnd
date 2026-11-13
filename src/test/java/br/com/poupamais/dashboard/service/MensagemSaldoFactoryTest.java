package br.com.poupamais.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.poupamais.shared.domain.Periodo;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MensagemSaldoFactoryTest {

  private final MensagemSaldoFactory fabrica = new MensagemSaldoFactory();

  @Test
  void deveDescreverSaldoPositivoEmReais() {
    assertThat(fabrica.criar(new Periodo(8, 2026), new BigDecimal("4920.00"), true))
        .isEqualTo("Saldo positivo de R$ 4.920,00 em Agosto");
  }

  @Test
  void deveDescreverSaldoNegativoSemSinal() {
    assertThat(fabrica.criar(new Periodo(3, 2026), new BigDecimal("-1234567.5"), true))
        .isEqualTo("As despesas superaram as receitas em R$ 1.234.567,50 em Março");
  }

  @Test
  void deveDescreverSaldoZero() {
    assertThat(fabrica.criar(new Periodo(12, 2025), new BigDecimal("0.00"), true))
        .isEqualTo("Receitas e despesas se igualaram em Dezembro: saldo de R$ 0,00");
  }

  @Test
  void deveAvisarQuandoNaoHaLancamentos() {
    assertThat(fabrica.criar(new Periodo(1, 2026), BigDecimal.ZERO, false))
        .isEqualTo("Nenhum lançamento ativo em Janeiro");
  }

  @ParameterizedTest
  @CsvSource({"1, Janeiro", "2, Fevereiro", "3, Março", "6, Junho", "11, Novembro"})
  void deveUsarONomeDoMesEmPortugues(int mes, String nome) {
    assertThat(fabrica.criar(new Periodo(mes, 2026), BigDecimal.ONE, true)).endsWith("em " + nome);
  }

  @Test
  void naoDeveRecomendarInvestimento() {
    assertThat(fabrica.criar(new Periodo(8, 2026), new BigDecimal("99999"), true))
        .doesNotContainIgnoringCase("invest");
  }

  @Test
  void deveFormatarCentavosEArredondar() {
    assertThat(MensagemSaldoFactory.moeda(new BigDecimal("0.5"))).isEqualTo("R$ 0,50");
    assertThat(MensagemSaldoFactory.moeda(new BigDecimal("999.995"))).isEqualTo("R$ 1.000,00");
  }
}
