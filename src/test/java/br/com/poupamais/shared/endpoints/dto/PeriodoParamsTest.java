package br.com.poupamais.shared.endpoints.dto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.poupamais.shared.domain.Periodo;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class PeriodoParamsTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void deveUsarOMesEOAnoInformados() {
    assertThat(new PeriodoParams(8, 2026).paraPeriodo()).isEqualTo(new Periodo(8, 2026));
  }

  @Test
  void deveCompletarComOMesAtualOQueNaoVeio() {
    Periodo atual = Periodo.atual();

    assertThat(new PeriodoParams(null, null).paraPeriodo()).isEqualTo(atual);
    assertThat(new PeriodoParams(2, null).paraPeriodo()).isEqualTo(new Periodo(2, atual.ano()));
    assertThat(new PeriodoParams(null, 2025).paraPeriodo())
        .isEqualTo(new Periodo(atual.mes(), 2025));
  }

  @Test
  void deveAceitarOsLimites() {
    assertThat(validator.validate(new PeriodoParams(1, 2000))).isEmpty();
    assertThat(validator.validate(new PeriodoParams(12, 2100))).isEmpty();
    assertThat(validator.validate(new PeriodoParams(null, null))).isEmpty();
  }

  @Test
  void deveRecusarForaDaFaixaComAMensagemDoCampo() {
    assertThat(validator.validate(new PeriodoParams(13, 1999)))
        .extracting(v -> v.getPropertyPath().toString() + ": " + v.getMessage())
        .containsExactlyInAnyOrder(
            "mes: O mês deve estar entre 1 e 12", "ano: O ano deve estar entre 2000 e 2100");
  }
}
