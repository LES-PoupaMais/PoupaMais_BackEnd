package br.com.poupamais.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ErroPadraoControllerTest {

  private final ErroPadraoController controller = new ErroPadraoController();

  @ParameterizedTest
  @CsvSource({
    "400, 400, REQUISICAO_INVALIDA",
    "401, 401, NAO_AUTENTICADO",
    "403, 403, ACESSO_NEGADO",
    "404, 404, NAO_ENCONTRADO",
    "405, 405, METODO_NAO_PERMITIDO",
    "413, 413, ARQUIVO_GRANDE_DEMAIS",
    "415, 415, TIPO_CONTEUDO_NAO_SUPORTADO",
    "500, 500, ERRO_INTERNO",
    "503, 500, ERRO_INTERNO"
  })
  void deveResponderNoPadraoComCodigoDoStatus(int recebido, int esperado, CodigoErro codigo) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, recebido);

    ResponseEntity<ErroResponse> resposta = controller.erro(request);

    assertThat(resposta.getStatusCode().value()).isEqualTo(esperado);
    assertThat(resposta.getBody().status()).isEqualTo(esperado);
    assertThat(resposta.getBody().codigo()).isEqualTo(codigo);
    assertThat(resposta.getBody().mensagem()).isEqualTo(ErroPadraoController.mensagem(codigo));
  }

  @Test
  void acessoDiretoSemStatusRespondeComoRotaInexistente() {
    ResponseEntity<ErroResponse> resposta = controller.erro(new MockHttpServletRequest());

    assertThat(resposta.getStatusCode().value()).isEqualTo(404);
    assertThat(resposta.getBody().codigo()).isEqualTo(CodigoErro.NAO_ENCONTRADO);
    assertThat(resposta.getBody().mensagem()).isEqualTo("Recurso não encontrado");
  }

  @Test
  void mensagensDosCodigosGenericosNaoVazamDetalhes() {
    assertThat(ErroPadraoController.mensagem(CodigoErro.REQUISICAO_INVALIDA))
        .isEqualTo("Não foi possível processar a requisição. Confira os dados enviados.");
    assertThat(ErroPadraoController.mensagem(CodigoErro.ERRO_INTERNO))
        .isEqualTo(GlobalExceptionHandler.MSG_ERRO_INTERNO);
  }
}
