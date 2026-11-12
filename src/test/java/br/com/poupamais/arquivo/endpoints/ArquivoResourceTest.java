package br.com.poupamais.arquivo.endpoints;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.arquivo.service.exportacao.ArquivoExportado;
import br.com.poupamais.arquivo.service.exportacao.ExportacaoService;
import br.com.poupamais.arquivo.service.exportacao.FormatoInvalidoException;
import br.com.poupamais.arquivo.service.importacao.ImportacaoService;
import br.com.poupamais.arquivo.service.importacao.ResultadoImportacao;
import br.com.poupamais.lancamento.publico.dto.FiltroLancamento;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.shared.config.WebConfiguration;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.shared.security.JwtService;
import br.com.poupamais.shared.security.SecurityConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ArquivoResource.class)
@Import({SecurityConfiguration.class, JwtService.class, WebConfiguration.class})
class ArquivoResourceTest {

  private static final Long USUARIO = 7L;

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;
  @MockitoBean private ImportacaoService importacao;
  @MockitoBean private ExportacaoService exportacao;

  private String bearer;

  @BeforeEach
  void token() {
    bearer = "Bearer " + jwtService.gerar(USUARIO).token();
  }

  private static MockMultipartFile csv(String conteudo) {
    return new MockMultipartFile(
        "arquivo", "lancamentos.csv", "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void importarDeveDevolverResumo() throws Exception {
    when(importacao.importar(eq(USUARIO), any()))
        .thenReturn(
            new ResultadoImportacao(
                2,
                1,
                List.of(new ResultadoImportacao.Falha(3, "x;y", "Tipo inválido")),
                List.of("Pets")));

    mvc.perform(
            multipart("/api/lancamentos/importar")
                .file(csv("a"))
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalLinhas").value(2))
        .andExpect(jsonPath("$.importados").value(1))
        .andExpect(jsonPath("$.falhas[0].linha").value(3))
        .andExpect(jsonPath("$.falhas[0].conteudo").value("x;y"))
        .andExpect(jsonPath("$.falhas[0].motivo").value("Tipo inválido"))
        .andExpect(jsonPath("$.categoriasCriadas[0]").value("Pets"));
  }

  @Test
  void importarArquivoRecusadoDeveDar422() throws Exception {
    when(importacao.importar(eq(USUARIO), any()))
        .thenThrow(new RegraNegocioException("Cabeçalho inválido"));

    mvc.perform(
            multipart("/api/lancamentos/importar")
                .file(csv("a"))
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.mensagem").value("Cabeçalho inválido"));
  }

  @Test
  void importarSemArquivoDeveDar422() throws Exception {
    mvc.perform(
            multipart("/api/lancamentos/importar")
                .file(csv(""))
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.mensagem").value(ArquivoResource.MSG_SEM_ARQUIVO));
    mvc.perform(multipart("/api/lancamentos/importar").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnprocessableContent());
    verifyNoInteractions(importacao);
  }

  @Test
  void semTokenDeveDar401() throws Exception {
    mvc.perform(multipart("/api/lancamentos/importar").file(csv("a")))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/lancamentos/exportar").param("formato", "csv"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/lancamentos/template-csv")).andExpect(status().isUnauthorized());
  }

  @Test
  void exportarDeveDevolverAnexoComFiltros() throws Exception {
    FiltroLancamento filtro =
        new FiltroLancamento("pão", 8, 2026, TipoLancamento.DESPESA, StatusLancamento.DELETADO, 5L);
    when(exportacao.exportar(USUARIO, "xlsx", filtro))
        .thenReturn(
            new ArquivoExportado(
                "lancamentos-2026-11-12.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {1, 2}));

    mvc.perform(
            get("/api/lancamentos/exportar")
                .param("formato", "xlsx")
                .param("descricao", "pão")
                .param("mes", "8")
                .param("ano", "2026")
                .param("tipo", "DESPESA")
                .param("status", "DELETADO")
                .param("categoriaId", "5")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"lancamentos-2026-11-12.xlsx\""))
        .andExpect(
            content()
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .andExpect(content().bytes(new byte[] {1, 2}));
    verify(exportacao).exportar(USUARIO, "xlsx", filtro);
  }

  @Test
  void exportarFormatoInvalidoDeveDar400() throws Exception {
    when(exportacao.exportar(eq(USUARIO), eq("pdf"), any()))
        .thenThrow(new FormatoInvalidoException("Formato inválido. Use: csv ou xlsx"));

    mvc.perform(
            get("/api/lancamentos/exportar")
                .param("formato", "pdf")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value("Formato inválido. Use: csv ou xlsx"));
  }

  @Test
  void exportarSemDadosDeveDar422() throws Exception {
    when(exportacao.exportar(eq(USUARIO), eq("csv"), any()))
        .thenThrow(new RegraNegocioException("Não há dados para exportação"));

    mvc.perform(
            get("/api/lancamentos/exportar")
                .param("formato", "csv")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.mensagem").value("Não há dados para exportação"));
  }

  @Test
  void templateDeveSerBaixadoComoAnexo() throws Exception {
    mvc.perform(get("/api/lancamentos/template-csv").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"template.csv\""))
        .andExpect(content().contentType("text/csv;charset=UTF-8"))
        .andExpect(
            content()
                .string(
                    Matchers.startsWith(
                        "descricao;tipo;mes;ano;valor;categorias\nSalário mensal;RECEITA;8;2026;8500,00;Salário")));
  }
}
