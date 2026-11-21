package br.com.poupamais;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.shared.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// Saúde da API (actuator) com o contexto completo e o PostgreSQL real: GET /actuator/health é
// público e não mostra detalhes; nenhum outro endpoint do actuator fica exposto. Sem
// POUPAMAIS_TEST_DB_URL é pulado.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class SaudeIntegracaoTest {

  @DynamicPropertySource
  static void banco(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", () -> System.getenv("POUPAMAIS_TEST_DB_URL"));
  }

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;
  @Autowired private JdbcTemplate jdbc;

  private Long usuario;

  @BeforeEach
  void usuario() {
    usuario =
        jdbc.queryForObject(
            "insert into poupamais.usuario (nome, email, senha, aceite_termos_em)"
                + " values ('Teste', ?, 'x', now()) returning id",
            Long.class,
            "saude-" + UUID.randomUUID() + "@teste.com");
  }

  @AfterEach
  void limpar() {
    jdbc.update("delete from poupamais.usuario where id = ?", usuario);
  }

  @Test
  void saudeRespondeUpSemTokenESemDetalhes() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", true));
  }

  @Test
  void saudeComTokenTambemNaoMostraDetalhes() throws Exception {
    mvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", true));
  }

  @Test
  void outrosEndpointsDoActuatorNaoFicamExpostos() throws Exception {
    String bearer = bearer();
    for (String rota :
        new String[] {
          "/actuator",
          "/actuator/info",
          "/actuator/env",
          "/actuator/beans",
          "/actuator/configprops",
          "/actuator/metrics",
          "/actuator/loggers",
          "/actuator/mappings",
          "/actuator/heapdump",
          "/actuator/threaddump"
        }) {
      // Sem token: fora da lista pública (401). Com token: não existe (404 no padrão de erro)
      mvc.perform(get(rota))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
      mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }
    // Componentes da saúde (ex.: banco) não aparecem: sem token 401; com token, o próprio actuator
    // responde 404 (sem corpo, por isso sem codigo)
    mvc.perform(get("/actuator/health/db")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/health/db").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isNotFound());
    mvc.perform(get("/actuator/health/liveness").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isNotFound());
  }

  private String bearer() {
    return "Bearer " + jwtService.gerar(usuario).token();
  }
}
