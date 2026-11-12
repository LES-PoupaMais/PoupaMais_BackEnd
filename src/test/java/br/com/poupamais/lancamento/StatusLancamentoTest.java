package br.com.poupamais.lancamento;

import static br.com.poupamais.lancamento.StatusLancamento.ATIVO;
import static br.com.poupamais.lancamento.StatusLancamento.DELETADO;
import static br.com.poupamais.lancamento.StatusLancamento.INATIVO;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatusLancamentoTest {

  @ParameterizedTest(name = "{0} -> {1} = {2}")
  @CsvSource({
    "ATIVO, INATIVO, true",
    "ATIVO, DELETADO, true",
    "ATIVO, ATIVO, false",
    "INATIVO, ATIVO, true",
    "INATIVO, DELETADO, true",
    "INATIVO, INATIVO, false",
    "DELETADO, INATIVO, true",
    "DELETADO, ATIVO, false",
    "DELETADO, DELETADO, false"
  })
  void deveSeguirAsTransicoesDaRn006(
      StatusLancamento atual, StatusLancamento novo, boolean permitido) {
    assertThat(atual.podeIrPara(novo)).isEqualTo(permitido);
  }

  @ParameterizedTest
  @CsvSource({"ATIVO", "INATIVO", "DELETADO"})
  void naoDevePermitirIrParaNulo(StatusLancamento atual) {
    assertThat(atual.podeIrPara(null)).isFalse();
  }

  @Test
  void deletadoSoVoltaComoInativo() {
    assertThat(DELETADO.podeIrPara(INATIVO)).isTrue();
    assertThat(DELETADO.podeIrPara(ATIVO)).isFalse();
  }
}
