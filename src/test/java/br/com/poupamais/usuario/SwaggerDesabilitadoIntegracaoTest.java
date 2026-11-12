package br.com.poupamais.usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// SWAGGER_HABILITADO=false (produção): springdoc desligado e rotas fora da lista pública.
// Contexto completo (precisa do banco): sem POUPAMAIS_TEST_DB_URL o teste é pulado.
@SpringBootTest(properties = "poupamais.swagger-habilitado=false")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class SwaggerDesabilitadoIntegracaoTest {

  @DynamicPropertySource
  static void banco(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", () -> System.getenv("POUPAMAIS_TEST_DB_URL"));
  }

  @Autowired private MockMvc mvc;

  @Test
  void swaggerDesabilitadoExigeTokenENaoExpoeADocumentacao() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
  }
}
