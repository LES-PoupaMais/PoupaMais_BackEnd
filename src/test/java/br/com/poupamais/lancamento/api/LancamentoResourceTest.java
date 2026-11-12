package br.com.poupamais.lancamento.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.categoria.CategoriaResumo;
import br.com.poupamais.lancamento.FiltroLancamento;
import br.com.poupamais.lancamento.LancamentoResumo;
import br.com.poupamais.lancamento.NovoLancamento;
import br.com.poupamais.lancamento.StatusLancamento;
import br.com.poupamais.lancamento.TipoLancamento;
import br.com.poupamais.lancamento.service.LancamentoService;
import br.com.poupamais.shared.config.WebConfiguration;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.shared.security.JwtService;
import br.com.poupamais.shared.security.SecurityConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = LancamentoResource.class)
@Import({
  SecurityConfiguration.class,
  JwtService.class,
  WebConfiguration.class,
  LancamentoMapper.class
})
class LancamentoResourceTest {

  private static final Long USUARIO = 7L;
  private static final String CORPO =
      """
      {"descricao":"Salário mensal","tipo":"RECEITA","mes":8,"ano":2026,
       "valor":8500.00,"categoriaIds":[3]}
      """;

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;
  @MockitoBean private LancamentoService service;

  private String bearer;

  @BeforeEach
  void token() {
    bearer = "Bearer " + jwtService.gerar(USUARIO).token();
  }

  private static LancamentoResumo resumo(StatusLancamento status) {
    return new LancamentoResumo(
        1L,
        "Salário mensal",
        TipoLancamento.RECEITA,
        8,
        2026,
        new BigDecimal("8500.00"),
        status,
        List.of(new CategoriaResumo(3L, "Salário", true)),
        LocalDate.of(2026, 8, 1),
        null);
  }

  private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String corpo) {
    return req.header(HttpHeaders.AUTHORIZATION, bearer)
        .contentType(MediaType.APPLICATION_JSON)
        .content(corpo);
  }

  private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder req) {
    return req.header(HttpHeaders.AUTHORIZATION, bearer);
  }

  @Test
  void deveListarNoFormatoDoContratoRepassandoFiltros() throws Exception {
    FiltroLancamento filtro =
        new FiltroLancamento("sal", 8, 2026, TipoLancamento.RECEITA, StatusLancamento.ATIVO, 3L);
    when(service.listar(USUARIO, filtro)).thenReturn(List.of(resumo(StatusLancamento.ATIVO)));

    mvc.perform(
            autenticado(
                get(
                    "/api/lancamentos?descricao=sal&mes=8&ano=2026&tipo=RECEITA&status=ATIVO"
                        + "&categoriaId=3")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].descricao").value("Salário mensal"))
        .andExpect(jsonPath("$[0].tipo").value("RECEITA"))
        .andExpect(jsonPath("$[0].mes").value(8))
        .andExpect(jsonPath("$[0].ano").value(2026))
        .andExpect(jsonPath("$[0].valor").value(8500.0))
        .andExpect(jsonPath("$[0].status").value("ATIVO"))
        .andExpect(jsonPath("$[0].categorias[0].id").value(3))
        .andExpect(jsonPath("$[0].categorias[0].descricao").value("Salário"))
        .andExpect(jsonPath("$[0].categorias[0].ativa").doesNotExist())
        .andExpect(jsonPath("$[0].dataCadastro").value("2026-08-01"))
        .andExpect(jsonPath("$[0].dataAtualizacao").doesNotExist());
  }

  @Test
  void deveListarSemFiltros() throws Exception {
    when(service.listar(USUARIO, FiltroLancamento.vazio())).thenReturn(List.of());

    mvc.perform(autenticado(get("/api/lancamentos")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void deveDevolver400ParaFiltroComTipoInvalido() throws Exception {
    mvc.perform(autenticado(get("/api/lancamentos?tipo=XPTO")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("tipo"));
  }

  @Test
  void deveDevolver404QuandoCategoriaDoFiltroNaoEDoUsuario() throws Exception {
    when(service.listar(eq(USUARIO), any()))
        .thenThrow(new RecursoNaoEncontradoException("Categoria não encontrada"));

    mvc.perform(autenticado(get("/api/lancamentos?categoriaId=99")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.mensagem").value("Categoria não encontrada"));
  }

  @Test
  void deveDevolver401SemTokenEmTodasAsRotas() throws Exception {
    mvc.perform(get("/api/lancamentos")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/lancamentos/1")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/lancamentos").contentType(MediaType.APPLICATION_JSON).content(CORPO))
        .andExpect(status().isUnauthorized());
    mvc.perform(put("/api/lancamentos/1").contentType(MediaType.APPLICATION_JSON).content(CORPO))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            patch("/api/lancamentos/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ATIVO\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/lancamentos/1")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/lancamentos/1/restaurar")).andExpect(status().isUnauthorized());
  }

  @Test
  void deveBuscarPorId() throws Exception {
    when(service.buscar(USUARIO, 1L)).thenReturn(resumo(StatusLancamento.ATIVO));

    mvc.perform(autenticado(get("/api/lancamentos/1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  void deveDevolver404ParaLancamentoDeOutroUsuario() throws Exception {
    RecursoNaoEncontradoException naoEncontrado =
        new RecursoNaoEncontradoException("Lançamento não encontrado");
    when(service.buscar(USUARIO, 2L)).thenThrow(naoEncontrado);
    when(service.editar(eq(USUARIO), eq(2L), any())).thenThrow(naoEncontrado);
    when(service.alterarStatus(USUARIO, 2L, StatusLancamento.INATIVO)).thenThrow(naoEncontrado);
    doThrow(naoEncontrado).when(service).excluir(USUARIO, 2L);
    when(service.restaurar(USUARIO, 2L)).thenThrow(naoEncontrado);

    mvc.perform(autenticado(get("/api/lancamentos/2")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.mensagem").value("Lançamento não encontrado"));
    mvc.perform(json(put("/api/lancamentos/2"), CORPO)).andExpect(status().isNotFound());
    mvc.perform(json(patch("/api/lancamentos/2/status"), "{\"status\":\"INATIVO\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(autenticado(delete("/api/lancamentos/2"))).andExpect(status().isNotFound());
    mvc.perform(autenticado(post("/api/lancamentos/2/restaurar"))).andExpect(status().isNotFound());
  }

  @Test
  void deveCriarCom201() throws Exception {
    NovoLancamento esperado =
        new NovoLancamento(
            "Salário mensal",
            TipoLancamento.RECEITA,
            8,
            2026,
            new BigDecimal("8500.00"),
            List.of(3L));
    when(service.criar(USUARIO, esperado)).thenReturn(resumo(StatusLancamento.ATIVO));

    mvc.perform(json(post("/api/lancamentos"), CORPO))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.status").value("ATIVO"));
  }

  @Test
  void deveDevolver400ComErrosPorCampo() throws Exception {
    mvc.perform(
            json(
                post("/api/lancamentos"),
                """
                {"descricao":"","tipo":null,"mes":13,"ano":1999,"valor":0,"categoriaIds":[]}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
        .andExpect(jsonPath("$.erros.length()").value(6))
        .andExpect(
            jsonPath("$.erros[?(@.campo == 'valor')].mensagem")
                .value("O valor deve ser maior que zero"))
        .andExpect(
            jsonPath("$.erros[?(@.campo == 'categoriaIds')].mensagem")
                .value("Informe pelo menos uma categoria"));
  }

  @Test
  void deveDevolver400ParaDescricaoLongaEValorComTresCasas() throws Exception {
    mvc.perform(
            json(
                post("/api/lancamentos"),
                """
                {"descricao":"%s","tipo":"DESPESA","mes":1,"ano":2026,"valor":1.234,
                 "categoriaIds":[1]}
                """
                    .formatted("a".repeat(101))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros.length()").value(2));
  }

  @Test
  void deveDevolver400ParaTipoInexistenteNoCorpo() throws Exception {
    mvc.perform(json(post("/api/lancamentos"), CORPO.replace("RECEITA", "OUTRO")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deveDevolver422ParaCategoriaInativa() throws Exception {
    when(service.criar(eq(USUARIO), any()))
        .thenThrow(
            new RegraNegocioException("A categoria \"X\" está inativa e não pode ser usada"));

    mvc.perform(json(post("/api/lancamentos"), CORPO))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422));
  }

  @Test
  void deveEditar() throws Exception {
    when(service.editar(eq(USUARIO), eq(1L), any())).thenReturn(resumo(StatusLancamento.ATIVO));

    mvc.perform(json(put("/api/lancamentos/1"), CORPO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  void deveDevolver422AoEditarDeletado() throws Exception {
    when(service.editar(eq(USUARIO), eq(1L), any()))
        .thenThrow(new RegraNegocioException("Lançamento excluído não pode ser editado."));

    mvc.perform(json(put("/api/lancamentos/1"), CORPO)).andExpect(status().isUnprocessableEntity());
  }

  @Test
  void deveDevolver400NoPutInvalido() throws Exception {
    mvc.perform(json(put("/api/lancamentos/1"), "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros").isArray());
  }

  @Test
  void deveAlterarStatus() throws Exception {
    when(service.alterarStatus(USUARIO, 1L, StatusLancamento.INATIVO))
        .thenReturn(resumo(StatusLancamento.INATIVO));

    mvc.perform(json(patch("/api/lancamentos/1/status"), "{\"status\":\"INATIVO\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INATIVO"));
  }

  @Test
  void deveDevolver400SemStatusE422ParaDeletado() throws Exception {
    when(service.alterarStatus(eq(USUARIO), anyLong(), eq(StatusLancamento.ATIVO)))
        .thenThrow(new RegraNegocioException("Não é possível alterar o status"));

    mvc.perform(json(patch("/api/lancamentos/1/status"), "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("status"));
    mvc.perform(json(patch("/api/lancamentos/1/status"), "{\"status\":\"ATIVO\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void deveExcluirCom204() throws Exception {
    mvc.perform(autenticado(delete("/api/lancamentos/1"))).andExpect(status().isNoContent());

    verify(service).excluir(USUARIO, 1L);
  }

  @Test
  void deveDevolver422AoExcluirJaExcluido() throws Exception {
    doThrow(new RegraNegocioException("O lançamento já está excluído"))
        .when(service)
        .excluir(USUARIO, 1L);

    mvc.perform(autenticado(delete("/api/lancamentos/1")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.mensagem").value("O lançamento já está excluído"));
  }

  @Test
  void deveRestaurarComoInativo() throws Exception {
    when(service.restaurar(USUARIO, 1L)).thenReturn(resumo(StatusLancamento.INATIVO));

    mvc.perform(autenticado(post("/api/lancamentos/1/restaurar")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INATIVO"));
  }

  @Test
  void deveDevolver422AoRestaurarNaoExcluido() throws Exception {
    when(service.restaurar(USUARIO, 1L))
        .thenThrow(new RegraNegocioException("Só é possível restaurar um lançamento excluído"));

    mvc.perform(autenticado(post("/api/lancamentos/1/restaurar")))
        .andExpect(status().isUnprocessableEntity());
  }
}
