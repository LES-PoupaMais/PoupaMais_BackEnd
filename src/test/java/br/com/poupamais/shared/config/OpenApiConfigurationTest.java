package br.com.poupamais.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.poupamais.shared.exception.CodigoErro;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigurationTest {

  @Test
  void swaggerTrazAVersaoDoContratoEATabelaComTodosOsCodigos() {
    OpenAPI api = new OpenApiConfiguration().openApi();

    assertThat(api.getInfo().getVersion()).isEqualTo("1.2.0");
    assertThat(api.getInfo().getDescription()).contains("| Código | Status | Quando |");
    for (CodigoErro codigo : CodigoErro.values()) {
      assertThat(api.getInfo().getDescription())
          .contains("| `" + codigo.name() + "` | " + codigo.status() + " |");
    }
  }
}
