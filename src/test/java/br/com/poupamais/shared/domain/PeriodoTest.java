package br.com.poupamais.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PeriodoTest {

  @ParameterizedTest
  @CsvSource({"0, 2026", "13, 2026"})
  void deveRecusarMesForaDe1a12(int mes, int ano) {
    assertThatThrownBy(() -> new Periodo(mes, ano))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("O mês deve estar entre 1 e 12.");
  }

  @ParameterizedTest
  @CsvSource({"8, 1999", "8, 2101"})
  void deveRecusarAnoForaDe2000a2100(int mes, int ano) {
    assertThatThrownBy(() -> new Periodo(mes, ano))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("O ano deve estar entre 2000 e 2100.");
  }

  @Test
  void anteriorDeJaneiroEhDezembroDoAnoAnterior() {
    assertThat(new Periodo(1, 2026).anterior()).isEqualTo(new Periodo(12, 2025));
  }

  @Test
  void ultimosQuatroMesesAtravessamAViradaDoAno() {
    assertThat(new Periodo(2, 2026).ultimos(4))
        .containsExactly(
            new Periodo(11, 2025),
            new Periodo(12, 2025),
            new Periodo(1, 2026),
            new Periodo(2, 2026));
  }

  @Test
  void ultimosDeUmMesEhOProprioMes() {
    assertThat(new Periodo(8, 2026).ultimos(1)).containsExactly(new Periodo(8, 2026));
  }

  @Test
  void deveRecusarQuantidadeMenorQueUm() {
    assertThatThrownBy(() -> new Periodo(8, 2026).ultimos(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A quantidade de meses deve ser pelo menos 1.");
  }

  @Test
  void nomeDoMesEmPortuguesComInicialMaiuscula() {
    assertThat(new Periodo(8, 2026).nomeMes()).isEqualTo("Agosto");
    assertThat(new Periodo(3, 2026).nomeMes()).isEqualTo("Março");
  }

  @Test
  void atualUsaOFusoDeBrasilia() {
    assertThat(Periodo.atual())
        .isEqualTo(Periodo.de(YearMonth.now(ZoneId.of("America/Sao_Paulo"))));
  }

  @Test
  void ordenaPorAnoEDepoisPorMes() {
    assertThat(new Periodo(12, 2025)).isLessThan(new Periodo(1, 2026));
    assertThat(new Periodo(5, 2026).compareTo(new Periodo(5, 2026))).isZero();
  }
}
