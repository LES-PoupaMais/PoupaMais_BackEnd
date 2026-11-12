package br.com.poupamais.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClassificacaoTaxaTest {

  @ParameterizedTest
  @CsvSource({
    "0, DENTRO_DO_LIMITE",
    "70, DENTRO_DO_LIMITE",
    "70.00, DENTRO_DO_LIMITE",
    "70.01, ATENCAO",
    "100, ATENCAO",
    "100.01, ACIMA_DO_LIMITE",
    "250, ACIMA_DO_LIMITE"
  })
  void deveClassificarNosLimitesExatos(String taxa, ClassificacaoTaxa esperada) {
    assertThat(ClassificacaoTaxa.de(new BigDecimal(taxa))).isEqualTo(esperada);
  }

  @Test
  void deveDevolverNullSemTaxa() {
    assertThat(ClassificacaoTaxa.de(null)).isNull();
  }
}
