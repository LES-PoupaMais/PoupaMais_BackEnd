package br.com.poupamais.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.categoria.CategoriaFacade;
import br.com.poupamais.shared.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// Importação ponta a ponta contra PostgreSQL real (roteiro da demo): CSV com uma linha inválida,
// conferência no banco, exportação filtrada e reimportação. Sem POUPAMAIS_TEST_DB_URL é pulado.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class ArquivoIntegracaoTest {

  @DynamicPropertySource
  static void banco(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", () -> System.getenv("POUPAMAIS_TEST_DB_URL"));
  }

  private static final String CSV =
      "﻿descricao;tipo;mes;ano;valor;categorias\n"
          + "Salário mensal;RECEITA;8;2026;8500,00;Salário\n"
          + "iFood — pedidos do mês;despesa;8;2026;420.00;Alimentação|Lazer\n"
          + "\n"
          + "Linha ruim;DESPESA;13;2026;10,00;Categoria Fantasma\n"
          + "Cinema;DESPESA;9;2026;1.050,50;lazer\n";

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CategoriaFacade categorias;

  private Long usuario;
  private String bearer;

  @BeforeEach
  void dados() {
    usuario =
        jdbc.queryForObject(
            "insert into poupamais.usuario (nome, email, senha, aceite_termos_em)"
                + " values ('Teste', ?, 'x', now()) returning id",
            Long.class,
            "arquivo-" + UUID.randomUUID() + "@teste.com");
    categorias.criarPadroes(usuario);
    bearer = "Bearer " + jwtService.gerar(usuario).token();
  }

  @AfterEach
  void limpar() {
    jdbc.update("delete from poupamais.usuario where id = ?", usuario);
  }

  private MockMultipartFile arquivo(byte[] conteudo) {
    return new MockMultipartFile("arquivo", "lancamentos.csv", "text/csv", conteudo);
  }

  private int lancamentos() {
    return jdbc.queryForObject(
        "select count(*) from poupamais.lancamento where id_usuario = ?", Integer.class, usuario);
  }

  @Test
  void deveImportarParcialmenteExportarFiltradoEReimportar() throws Exception {
    mvc.perform(
            multipart("/api/lancamentos/importar")
                .file(arquivo(CSV.getBytes(StandardCharsets.UTF_8)))
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalLinhas").value(4))
        .andExpect(jsonPath("$.importados").value(3))
        .andExpect(jsonPath("$.falhas.length()").value(1))
        .andExpect(jsonPath("$.falhas[0].linha").value(5))
        .andExpect(jsonPath("$.falhas[0].motivo").value("O mês deve ser um número entre 1 e 12"))
        .andExpect(jsonPath("$.categoriasCriadas.length()").value(0));

    assertThat(lancamentos()).isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from poupamais.categoria where id_usuario = ? and descricao = ?",
                Integer.class,
                usuario,
                "Categoria Fantasma"))
        .isZero();
    Map<String, Object> cinema =
        jdbc.queryForMap(
            "select valor, tipo from poupamais.lancamento where id_usuario = ? and descricao = ?",
            usuario,
            "Cinema");
    assertThat((java.math.BigDecimal) cinema.get("valor")).isEqualByComparingTo("1050.50");
    List<String> catsIfood =
        jdbc.queryForList(
            "select c.descricao from poupamais.categoria c"
                + " join poupamais.categorias_lancamentos cl on cl.id_categoria = c.id"
                + " join poupamais.lancamento l on l.id = cl.id_lancamento"
                + " where l.id_usuario = ? and l.descricao like 'iFood%' order by c.descricao",
            String.class, usuario);
    assertThat(catsIfood).containsExactly("Alimentação", "Lazer");

    byte[] exportado =
        mvc.perform(
                get("/api/lancamentos/exportar")
                    .param("formato", "csv")
                    .param("mes", "8")
                    .param("ano", "2026")
                    .header(HttpHeaders.AUTHORIZATION, bearer))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.matchesPattern(
                            "attachment; filename=\"lancamentos-\\d{4}-\\d{2}-\\d{2}\\.csv\"")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    String texto = new String(exportado, StandardCharsets.UTF_8);
    assertThat(texto).contains("Salário mensal;RECEITA;8;2026;8500,00;Salário;ATIVO");
    assertThat(texto).doesNotContain("Cinema");

    mvc.perform(
            multipart("/api/lancamentos/importar")
                .file(arquivo(exportado))
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalLinhas").value(2))
        .andExpect(jsonPath("$.importados").value(2));
    assertThat(lancamentos()).isEqualTo(5);
  }

  @Test
  void deveCriarCategoriaNovaSoEmLinhaValidaERecusarInativa() throws Exception {
    Long antiga = categorias.buscarOuCriar(usuario, "Antiga").id();
    jdbc.update("update poupamais.categoria set ativa = false where id = ?", antiga);
    String csv =
        "descricao;tipo;mes;ano;valor;categorias\n"
            + "Ração;DESPESA;8;2026;80;Pets\n"
            + "Velha;DESPESA;8;2026;10;antiga|Viagem\n";

    mvc.perform(
            multipart("/api/lancamentos/importar")
                .file(arquivo(csv.getBytes(StandardCharsets.UTF_8)))
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.importados").value(1))
        .andExpect(jsonPath("$.categoriasCriadas[0]").value("Pets"))
        .andExpect(jsonPath("$.categoriasCriadas.length()").value(1))
        .andExpect(
            jsonPath("$.falhas[0].motivo")
                .value("A categoria \"Antiga\" está inativa e não pode ser usada"));

    assertThat(categorias.buscarPorDescricao(usuario, "Viagem")).isEmpty();
  }

  @Test
  void deveRecusarArquivoSemCabecalhoSemGravarNada() throws Exception {
    mvc.perform(
            multipart("/api/lancamentos/importar")
                .file(arquivo("Pão;DESPESA;8;2026;10;Mercado\n".getBytes(StandardCharsets.UTF_8)))
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnprocessableContent());

    assertThat(lancamentos()).isZero();
  }

  @Test
  void exportarSemDadosDeveDar422EDeletadoSoComFiltro() throws Exception {
    mvc.perform(
            get("/api/lancamentos/exportar")
                .param("formato", "xlsx")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.mensagem").value("Não há dados para exportação"));

    mvc.perform(
        multipart("/api/lancamentos/importar")
            .file(arquivo(CSV.getBytes(StandardCharsets.UTF_8)))
            .header(HttpHeaders.AUTHORIZATION, bearer));
    jdbc.update(
        "update poupamais.lancamento set status = 'DELETADO' where id_usuario = ?", usuario);

    mvc.perform(
            get("/api/lancamentos/exportar")
                .param("formato", "csv")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnprocessableContent());
    mvc.perform(
            get("/api/lancamentos/exportar")
                .param("formato", "xlsx")
                .param("status", "DELETADO")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_TYPE,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
  }

  @Test
  void rotasDoArquivoNaoColidemComAsDoLancamento() throws Exception {
    mvc.perform(get("/api/lancamentos/template-csv").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/lancamentos/exportar")
                .param("formato", "pdf")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value("Formato inválido. Use: csv ou xlsx"));
    mvc.perform(get("/api/lancamentos/999999999").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/lancamentos").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());
  }
}
