package br.com.poupamais.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.poupamais.shared.exception.CodigoErro;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ErroAcessoNegadoHandlerTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void deveResponder403NoPadraoComCodigo() throws Exception {
    MockHttpServletResponse resposta = new MockHttpServletResponse();

    new ErroAcessoNegadoHandler()
        .handle(new MockHttpServletRequest(), resposta, new AccessDeniedException("negado"));

    assertThat(resposta.getStatus()).isEqualTo(403);
    assertThat(resposta.getContentType()).startsWith("application/json");
    JsonNode corpo = json.readTree(resposta.getContentAsString());
    assertThat(corpo.get("status").asInt()).isEqualTo(403);
    assertThat(corpo.get("codigo").asString()).isEqualTo(CodigoErro.ACESSO_NEGADO.name());
    assertThat(corpo.get("mensagem").asString())
        .isEqualTo("Você não tem permissão para acessar este recurso.");
  }

  @Test
  void corpoDo401TemOCodigoNaoAutenticado() {
    JsonNode corpo = json.readTree(ErroAutenticacaoEntryPoint.CORPO);

    assertThat(corpo.get("status").asInt()).isEqualTo(401);
    assertThat(corpo.get("codigo").asString()).isEqualTo(CodigoErro.NAO_AUTENTICADO.name());
  }
}
