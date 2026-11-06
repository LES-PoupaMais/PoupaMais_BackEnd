package br.com.poupamais;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// Contra PostgreSQL real: sobe a aplicação inteira (ddl-auto=validate) e confere o schema do
// docker/init.sql. Sem POUPAMAIS_TEST_DB_URL o teste é pulado. Ver README.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class SchemaIntegracaoTest {

  @DynamicPropertySource
  static void banco(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", () -> System.getenv("POUPAMAIS_TEST_DB_URL"));
  }

  @Autowired private JdbcTemplate jdbc;

  @Test
  void schemaPoupamaisTemAsSeisTabelas() {
    List<String> tabelas =
        jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'poupamais'",
            String.class);

    assertThat(tabelas)
        .containsExactlyInAnyOrder(
            "usuario",
            "categoria",
            "lancamento",
            "categorias_lancamentos",
            "foto_perfil",
            "token_recuperacao_senha");
  }
}
