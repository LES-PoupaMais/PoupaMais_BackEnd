package br.com.poupamais;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// A API só autentica por JWT: o contexto completo não tem o usuário em memória do Spring Security
// (o InMemoryUserDetailsManager que escrevia "Using generated security password" no log). Sem
// POUPAMAIS_TEST_DB_URL é pulado.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class SemUsuarioEmMemoriaIntegracaoTest {

  @DynamicPropertySource
  static void banco(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", () -> System.getenv("POUPAMAIS_TEST_DB_URL"));
  }

  @Autowired private ApplicationContext contexto;

  @Test
  void contextoSemUsuarioEmMemoria() {
    assertThat(contexto.getBeanNamesForType(UserDetailsService.class)).isEmpty();
  }
}
