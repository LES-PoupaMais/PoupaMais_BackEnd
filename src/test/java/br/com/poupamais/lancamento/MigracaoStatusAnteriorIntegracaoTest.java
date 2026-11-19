package br.com.poupamais.lancamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

// Contra PostgreSQL real: prova a V3 (status_anterior). Cria um banco descartável no mesmo servidor
// do POUPAMAIS_TEST_DB_URL, aplica até a V2, grava um lançamento já na lixeira e então aplica a V3:
// ele fica com status_anterior NULL (o restaurar devolve como INATIVO). Sem a variável o teste é
// pulado.
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class MigracaoStatusAnteriorIntegracaoTest {

  private static final String URL = System.getenv("POUPAMAIS_TEST_DB_URL");
  private static final String USUARIO = valorOu("DB_USER", "poupamais");
  private static final String SENHA = valorOu("DB_PASSWORD", "poupamais");

  private final String banco =
      "poupamais_migracao_" + UUID.randomUUID().toString().replace("-", "");
  private SingleConnectionDataSource dataSource;
  private JdbcTemplate jdbc;

  @BeforeEach
  void criarBanco() throws SQLException {
    executarNoServidor("create database " + banco);
    dataSource = new SingleConnectionDataSource(urlDo(banco), USUARIO, SENHA, true);
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void apagarBanco() throws SQLException {
    dataSource.destroy();
    executarNoServidor("drop database if exists " + banco + " with (force)");
  }

  @Test
  void v3CriaStatusAnteriorNuloParaQuemJaEstavaNaLixeira() {
    flyway("2").migrate();
    Long usuario =
        jdbc.queryForObject(
            "insert into poupamais.usuario (nome, email, senha, aceite_termos_em)"
                + " values ('Migração', 'migracao@teste.com', 'x', now()) returning id",
            Long.class);
    Long naLixeira =
        jdbc.queryForObject(
            "insert into poupamais.lancamento"
                + " (id_usuario, descricao, data, valor, tipo, status, data_exclusao)"
                + " values (?, 'Antigo', date '2026-08-15', 10.00, 'DESPESA', 'DELETADO', now())"
                + " returning id",
            Long.class,
            usuario);

    flyway(null).migrate();

    List<String> colunas =
        jdbc.queryForList(
            "select column_name || ':' || data_type || ':' || is_nullable"
                + " from information_schema.columns"
                + " where table_schema = 'poupamais' and table_name = 'lancamento'",
            String.class);
    assertThat(colunas).contains("status_anterior:character varying:YES");
    assertThat(
            jdbc.queryForObject(
                "select status_anterior from poupamais.lancamento where id = ?",
                String.class,
                naLixeira))
        .isNull();
  }

  private Flyway flyway(String alvo) {
    var configuracao =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas("poupamais")
            .defaultSchema("poupamais")
            .createSchemas(true)
            .locations("classpath:db/migration");
    if (alvo != null) {
      configuracao.target(alvo);
    }
    return configuracao.load();
  }

  private static void executarNoServidor(String sql) throws SQLException {
    try (Connection conexao = DriverManager.getConnection(URL, USUARIO, SENHA);
        Statement comando = conexao.createStatement()) {
      comando.execute(sql);
    }
  }

  /** Mesma URL do banco de testes, trocando só o nome do banco. */
  private static String urlDo(String nomeBanco) {
    int barra = URL.lastIndexOf('/');
    int parametros = URL.indexOf('?', barra);
    String resto = parametros < 0 ? "" : URL.substring(parametros);
    return URL.substring(0, barra + 1) + nomeBanco + resto;
  }

  private static String valorOu(String variavel, String padrao) {
    String valor = System.getenv(variavel);
    return valor == null || valor.isBlank() ? padrao : valor;
  }
}
