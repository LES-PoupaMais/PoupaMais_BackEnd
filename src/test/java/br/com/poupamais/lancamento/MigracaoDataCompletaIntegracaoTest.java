package br.com.poupamais.lancamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

// Contra PostgreSQL real: prova o backfill da V2 (mes/ano → data no dia 1, auditoria date →
// timestamptz). Cria um banco descartável no mesmo servidor do POUPAMAIS_TEST_DB_URL, aplica só a
// V1, grava lançamentos no formato antigo e então aplica a V2. Sem a variável o teste é pulado.
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class MigracaoDataCompletaIntegracaoTest {

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
  void v2PreencheADataNoDia1DoMesEConverteAAuditoriaParaTimestamptz() {
    flyway("1").migrate();
    Long usuario =
        jdbc.queryForObject(
            "insert into poupamais.usuario (nome, email, senha, aceite_termos_em)"
                + " values ('Migração', 'migracao@teste.com', 'x', now()) returning id",
            Long.class);
    Long fevereiro = lancamentoAntigo(usuario, 2, 2026, "2026-02-10", null);
    Long dezembro = lancamentoAntigo(usuario, 12, 2025, "2025-12-20", "2026-01-05");

    flyway(null).migrate();

    assertThat(dataDo(fevereiro)).isEqualTo(LocalDate.of(2026, 2, 1));
    assertThat(dataDo(dezembro)).isEqualTo(LocalDate.of(2025, 12, 1));
    OffsetDateTime cadastro =
        jdbc.queryForObject(
            "select data_cadastro from poupamais.lancamento where id = ?",
            OffsetDateTime.class,
            dezembro);
    OffsetDateTime atualizacao =
        jdbc.queryForObject(
            "select data_atualizacao from poupamais.lancamento where id = ?",
            OffsetDateTime.class,
            dezembro);
    ZoneId brasilia = ZoneId.of("America/Sao_Paulo");
    assertThat(cadastro.atZoneSameInstant(brasilia).toLocalDateTime())
        .isEqualTo(LocalDate.of(2025, 12, 20).atStartOfDay());
    assertThat(atualizacao.atZoneSameInstant(brasilia).toLocalDateTime())
        .isEqualTo(LocalDate.of(2026, 1, 5).atStartOfDay());
    assertThat(
            jdbc.queryForObject(
                "select data_exclusao from poupamais.lancamento where id = ?",
                OffsetDateTime.class,
                fevereiro))
        .isNull();
  }

  @Test
  void v2TrocaColunasTiposEIndice() {
    flyway(null).migrate();

    List<String> colunas =
        jdbc.queryForList(
            "select column_name || ':' || data_type || ':' || is_nullable"
                + " from information_schema.columns"
                + " where table_schema = 'poupamais' and table_name = 'lancamento'",
            String.class);
    String indice =
        jdbc.queryForObject(
            "select indexdef from pg_indexes"
                + " where schemaname = 'poupamais' and indexname = 'lancamento_dashboard_idx'",
            String.class);

    assertThat(colunas)
        .contains(
            "data:date:NO",
            "data_cadastro:timestamp with time zone:NO",
            "data_atualizacao:timestamp with time zone:YES",
            "data_exclusao:timestamp with time zone:YES")
        .noneMatch(c -> c.startsWith("mes:") || c.startsWith("ano:"));
    assertThat(indice).contains("(id_usuario, data, status)");
  }

  private Long lancamentoAntigo(
      Long usuario, int mes, int ano, String cadastro, String atualizacao) {
    return jdbc.queryForObject(
        "insert into poupamais.lancamento"
            + " (id_usuario, descricao, mes, ano, valor, tipo, data_cadastro, data_atualizacao)"
            + " values (?, 'Antigo', ?, ?, 10.00, 'DESPESA', cast(? as date), cast(? as date))"
            + " returning id",
        Long.class,
        usuario,
        mes,
        ano,
        cadastro,
        atualizacao);
  }

  private LocalDate dataDo(Long id) {
    return jdbc.queryForObject(
        "select data from poupamais.lancamento where id = ?", LocalDate.class, id);
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
