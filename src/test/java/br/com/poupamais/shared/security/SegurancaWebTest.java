package br.com.poupamais.shared.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.shared.config.WebConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SegurancaWebTest.ControllerDeTeste.class)
@Import({
  SecurityConfiguration.class,
  JwtService.class,
  WebConfiguration.class,
  SegurancaWebTest.ControllerDeTeste.class
})
class SegurancaWebTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void rotaDeLoginEhPublica() throws Exception {
    mvc.perform(post("/api/auth/login")).andExpect(status().isOk());
  }

  @Test
  void outraRotaDeAuthNaoEhPublica() throws Exception {
    mvc.perform(get("/api/auth/login")).andExpect(status().isUnauthorized());
  }

  @Test
  void rotaPrivadaSemTokenRetorna401NoPadraoDeErro() throws Exception {
    mvc.perform(get("/api/privado"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.mensagem").value(containsString("Faça login novamente")));
  }

  @Test
  void rotaPrivadaComTokenInvalidoRetorna401() throws Exception {
    mvc.perform(get("/api/privado").header(HttpHeaders.AUTHORIZATION, "Bearer invalido"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rotaPrivadaComTokenValidoDevolveIdDoUsuarioLogado() throws Exception {
    String token = jwtService.gerar(99L).token();

    mvc.perform(get("/api/privado").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().string("99"));
  }

  @Test
  void passwordEncoderEhBcrypt() {
    String hash = passwordEncoder.encode("123456");

    org.assertj.core.api.Assertions.assertThat(hash).startsWith("$2");
    org.assertj.core.api.Assertions.assertThat(passwordEncoder.matches("123456", hash)).isTrue();
  }

  @Test
  void corsLiberaOFrontDeDesenvolvimento() throws Exception {
    mvc.perform(
            options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
  }

  @Test
  void corsRecusaOrigemDesconhecida() throws Exception {
    mvc.perform(
            options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://site-malicioso.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden());
  }

  @RestController
  static class ControllerDeTeste {

    @PostMapping("/api/auth/login")
    String publico() {
      return "ok";
    }

    @GetMapping("/api/privado")
    String privado() {
      return String.valueOf(UsuarioLogado.id());
    }
  }
}
