package br.com.poupamais.shared.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SegurancaWebTest.ControllerDeTeste.class)
@Import({
  SecurityConfiguration.class,
  WebConfiguration.class,
  SegurancaWebTest.ControllerDeTeste.class
})
class SegurancaWebTest {

  @Autowired private MockMvc mvc;

  @Test
  void rotaDeAutenticacaoEhPublica() throws Exception {
    mvc.perform(get("/api/auth/ping")).andExpect(status().isOk());
  }

  @Test
  void rotaPrivadaSemTokenRetorna401() throws Exception {
    mvc.perform(get("/api/privado")).andExpect(status().isUnauthorized());
  }

  @Test
  void corsLiberaOFrontDeDesenvolvimento() throws Exception {
    mvc.perform(
            options("/api/auth/ping")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
  }

  @Test
  void corsRecusaOrigemDesconhecida() throws Exception {
    mvc.perform(
            options("/api/auth/ping")
                .header(HttpHeaders.ORIGIN, "http://site-malicioso.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden());
  }

  @RestController
  static class ControllerDeTeste {

    @GetMapping("/api/auth/ping")
    String publico() {
      return "ok";
    }

    @GetMapping("/api/privado")
    String privado() {
      return "segredo";
    }
  }
}
