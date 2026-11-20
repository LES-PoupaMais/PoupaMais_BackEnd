package br.com.poupamais.categoria.endpoints;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.categoria.endpoints.mappers.CategoriaMapper;
import br.com.poupamais.categoria.model.entidades.Categoria;
import br.com.poupamais.categoria.service.CategoriaComQuantidade;
import br.com.poupamais.categoria.service.CategoriaService;
import br.com.poupamais.shared.config.WebConfiguration;
import br.com.poupamais.shared.domain.Periodo;
import br.com.poupamais.shared.exception.CodigoErro;
import br.com.poupamais.shared.exception.ConflitoException;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.shared.security.JwtService;
import br.com.poupamais.shared.security.SecurityConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CategoriaResource.class)
@Import({
  SecurityConfiguration.class,
  JwtService.class,
  WebConfiguration.class,
  CategoriaMapper.class
})
class CategoriaResourceTest {

  private static final Long USUARIO = 7L;

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;
  @MockitoBean private CategoriaService service;

  private String bearer;

  @BeforeEach
  void token() {
    bearer = "Bearer " + jwtService.gerar(USUARIO).token();
  }

  private static Categoria categoria(Long id, String descricao, boolean ativa) {
    return Categoria.builder().id(id).idUsuario(USUARIO).descricao(descricao).ativa(ativa).build();
  }

  @Test
  void deveListarCategoriasDoUsuarioLogado() throws Exception {
    when(service.listarComQuantidade(USUARIO, true, Periodo.atual()))
        .thenReturn(List.of(new CategoriaComQuantidade(categoria(1L, "Lazer", true), 0)));

    mvc.perform(get("/api/categorias?ativa=true").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].descricao").value("Lazer"))
        .andExpect(jsonPath("$[0].ativa").value(true))
        .andExpect(jsonPath("$[0].quantidadeLancamentos").value(0));
  }

  @Test
  void deveListarComQuantidadeDeLancamentosDoMesInformado() throws Exception {
    when(service.listarComQuantidade(USUARIO, null, new Periodo(8, 2026)))
        .thenReturn(
            List.of(
                new CategoriaComQuantidade(categoria(3L, "Lazer", true), 5),
                new CategoriaComQuantidade(categoria(1L, "Antiga", false), 0)));

    mvc.perform(get("/api/categorias?mes=8&ano=2026").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(3))
        .andExpect(jsonPath("$[0].quantidadeLancamentos").value(5))
        .andExpect(jsonPath("$[1].ativa").value(false))
        .andExpect(jsonPath("$[1].quantidadeLancamentos").value(0));
  }

  @Test
  void deveCompletarComOMesOuAnoAtualQuandoSoUmVier() throws Exception {
    Periodo atual = Periodo.atual();
    when(service.listarComQuantidade(any(), any(), any())).thenReturn(List.of());

    mvc.perform(get("/api/categorias?mes=2").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());
    mvc.perform(get("/api/categorias?ano=2025").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());

    verify(service).listarComQuantidade(USUARIO, null, new Periodo(2, atual.ano()));
    verify(service).listarComQuantidade(USUARIO, null, new Periodo(atual.mes(), 2025));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "mes=0&ano=2026",
        "mes=13&ano=2026",
        "mes=8&ano=1999",
        "mes=8&ano=2101",
        "mes=abc",
        "ano=xyz"
      })
  void deveDevolver400ParaMesOuAnoInvalido(String query) throws Exception {
    mvc.perform(get("/api/categorias?" + query).header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
        .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
        .andExpect(jsonPath("$.erros[0].campo").exists());

    verifyNoInteractions(service);
  }

  @Test
  void deveDevolverMensagemDoMesForaDaFaixa() throws Exception {
    mvc.perform(get("/api/categorias?mes=13").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("mes"))
        .andExpect(jsonPath("$.erros[0].mensagem").value("O mês deve estar entre 1 e 12"));
  }

  @Test
  void deveDevolver401SemToken() throws Exception {
    mvc.perform(get("/api/categorias")).andExpect(status().isUnauthorized());
  }

  @Test
  void deveCriarCom201() throws Exception {
    when(service.criar(USUARIO, "Pets")).thenReturn(categoria(10L, "Pets", true));

    mvc.perform(
            post("/api/categorias")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Pets\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.descricao").value("Pets"));
  }

  @Test
  void deveDevolver400ComErrosQuandoDescricaoVazia() throws Exception {
    mvc.perform(
            post("/api/categorias")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("descricao"));
  }

  @Test
  void deveDevolver409QuandoDuplicada() throws Exception {
    when(service.criar(USUARIO, "Lazer"))
        .thenThrow(
            new ConflitoException(
                CodigoErro.CATEGORIA_DUPLICADA, "Já existe uma categoria com essa descrição"));

    mvc.perform(
            post("/api/categorias")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Lazer\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.mensagem").value("Já existe uma categoria com essa descrição"))
        .andExpect(jsonPath("$.codigo").value("CATEGORIA_DUPLICADA"));
  }

  @Test
  void deveEditar() throws Exception {
    when(service.editar(USUARIO, 3L, "Casa")).thenReturn(categoria(3L, "Casa", true));

    mvc.perform(
            put("/api/categorias/3")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Casa\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.descricao").value("Casa"));
  }

  @Test
  void deveDevolver404AoEditarCategoriaDeOutroUsuario() throws Exception {
    when(service.editar(USUARIO, 99L, "Casa"))
        .thenThrow(
            new RecursoNaoEncontradoException(
                CodigoErro.CATEGORIA_NAO_ENCONTRADA, "Categoria não encontrada"));

    mvc.perform(
            put("/api/categorias/99")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Casa\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deveAlterarStatus() throws Exception {
    when(service.alterarStatus(USUARIO, 3L, false)).thenReturn(categoria(3L, "Casa", false));

    mvc.perform(
            patch("/api/categorias/3/status")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativa\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ativa").value(false));
  }

  @Test
  void deveDevolver400SemCampoAtiva() throws Exception {
    mvc.perform(
            patch("/api/categorias/3/status")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("ativa"));
  }

  @Test
  void deveExcluirCom204() throws Exception {
    mvc.perform(delete("/api/categorias/3").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isNoContent());
  }

  @Test
  void deveDevolver422AoExcluirVinculada() throws Exception {
    doThrow(
            new RegraNegocioException(
                CodigoErro.CATEGORIA_VINCULADA,
                "Categoria vinculada a lançamentos, apenas inative"))
        .when(service)
        .excluir(USUARIO, 3L);

    mvc.perform(delete("/api/categorias/3").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.mensagem").value("Categoria vinculada a lançamentos, apenas inative"));
  }

  @Test
  void deveDevolver404AoExcluirCategoriaDeOutroUsuario() throws Exception {
    doThrow(
            new RecursoNaoEncontradoException(
                CodigoErro.CATEGORIA_NAO_ENCONTRADA, "Categoria não encontrada"))
        .when(service)
        .excluir(USUARIO, 99L);

    mvc.perform(delete("/api/categorias/99").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isNotFound());
  }

  @Test
  void deveDevolver404AoAlterarStatusDeOutroUsuario() throws Exception {
    when(service.alterarStatus(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyBoolean()))
        .thenThrow(
            new RecursoNaoEncontradoException(
                CodigoErro.CATEGORIA_NAO_ENCONTRADA, "Categoria não encontrada"));

    mvc.perform(
            patch("/api/categorias/99/status")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativa\":true}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deveDevolver400ComParametroInvalidoParaAtivaQueNaoEhBooleano() throws Exception {
    mvc.perform(get("/api/categorias?ativa=talvez").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
        .andExpect(
            jsonPath("$.mensagem").value("Parâmetro inválido: ativa. Confira o valor enviado."));

    verifyNoInteractions(service);
  }
}
