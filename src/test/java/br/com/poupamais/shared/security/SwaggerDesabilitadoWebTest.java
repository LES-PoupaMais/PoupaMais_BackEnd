package br.com.poupamais.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.shared.config.WebConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

// SWAGGER_HABILITADO=false (produção): as rotas do Swagger saem da lista pública
@WebMvcTest(
    controllers = SegurancaWebTest.ControllerDeTeste.class,
    properties = "poupamais.swagger-habilitado=false")
@Import({
  SecurityConfiguration.class,
  JwtService.class,
  WebConfiguration.class,
  SegurancaWebTest.ControllerDeTeste.class
})
class SwaggerDesabilitadoWebTest {

  @Autowired private MockMvc mvc;

  @Test
  void swaggerExigeAutenticacaoQuandoDesabilitado() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
  }

  @Test
  void rotasPublicasDeAuthContinuamLiberadas() throws Exception {
    mvc.perform(post("/api/auth/login")).andExpect(status().isOk());
  }
}
