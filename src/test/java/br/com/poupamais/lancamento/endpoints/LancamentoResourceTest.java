package br.com.poupamais.lancamento.endpoints;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.lancamento.endpoints.mappers.LancamentoMapper;
import br.com.poupamais.lancamento.publico.dto.FiltroLancamento;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.lancamento.service.LancamentoService;
import br.com.poupamais.shared.config.WebConfiguration;
import br.com.poupamais.shared.exception.CodigoErro;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.shared.security.JwtService;
import br.com.poupamais.shared.security.SecurityConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
      {"descricao":"Salário mensal","tipo":"RECEITA","data":"2026-08-05",
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
        LocalDate.of(2026, 8, 5),
        new BigDecimal("8500.00"),
        status,
        List.of(new CategoriaResumo(3L, "Salário", true)),
        OffsetDateTime.of(2026, 8, 1, 13, 5, 0, 0, ZoneOffset.UTC),
        null,
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

  private static Page<LancamentoResumo> pagina(
      List<LancamentoResumo> itens, int pagina, int tamanho, long total) {
    return new PageImpl<>(itens, PageRequest.of(pagina, tamanho), total);
  }

  @Test
  void deveListarNoFormatoDoContratoRepassandoFiltrosEPaginacao() throws Exception {
    FiltroLancamento filtro =
        new FiltroLancamento("sal", 8, 2026, TipoLancamento.RECEITA, StatusLancamento.ATIVO, 3L);
    when(service.listarPagina(USUARIO, filtro, 2, 5))
        .thenReturn(pagina(List.of(resumo(StatusLancamento.ATIVO)), 2, 5, 11));

    mvc.perform(
            autenticado(
                get(
                    "/api/lancamentos?descricao=sal&mes=8&ano=2026&tipo=RECEITA&status=ATIVO"
                        + "&categoriaId=3&pagina=2&tamanho=5")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pagina").value(2))
        .andExpect(jsonPath("$.tamanho").value(5))
        .andExpect(jsonPath("$.total").value(11))
        .andExpect(jsonPath("$.totalPaginas").value(3))
        .andExpect(jsonPath("$.content").doesNotExist())
        .andExpect(jsonPath("$.pageable").doesNotExist())
        .andExpect(jsonPath("$.itens.length()").value(1))
        .andExpect(jsonPath("$.itens[0].id").value(1))
        .andExpect(jsonPath("$.itens[0].descricao").value("Salário mensal"))
        .andExpect(jsonPath("$.itens[0].tipo").value("RECEITA"))
        .andExpect(jsonPath("$.itens[0].data").value("2026-08-05"))
        .andExpect(jsonPath("$.itens[0].mes").doesNotExist())
        .andExpect(jsonPath("$.itens[0].ano").doesNotExist())
        .andExpect(jsonPath("$.itens[0].valor").value(8500.0))
        .andExpect(jsonPath("$.itens[0].status").value("ATIVO"))
        .andExpect(jsonPath("$.itens[0].categorias[0].id").value(3))
        .andExpect(jsonPath("$.itens[0].categorias[0].descricao").value("Salário"))
        .andExpect(jsonPath("$.itens[0].categorias[0].ativa").doesNotExist())
        .andExpect(jsonPath("$.itens[0].dataCadastro").value("2026-08-01T13:05:00Z"))
        .andExpect(jsonPath("$.itens[0].dataAtualizacao").doesNotExist())
        .andExpect(jsonPath("$.itens[0].dataExclusao").doesNotExist());
  }

  @Test
  void deveListarSemParametrosNaPrimeiraPaginaCom12() throws Exception {
    when(service.listarPagina(USUARIO, FiltroLancamento.vazio(), 0, 12))
        .thenReturn(pagina(List.of(), 0, 12, 0));

    mvc.perform(autenticado(get("/api/lancamentos")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.itens.length()").value(0))
        .andExpect(jsonPath("$.pagina").value(0))
        .andExpect(jsonPath("$.tamanho").value(12))
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.totalPaginas").value(0));
  }

  @Test
  void deveDevolverPaginaAlemDoFimVaziaComTotal() throws Exception {
    when(service.listarPagina(USUARIO, FiltroLancamento.vazio(), 7, 12))
        .thenReturn(pagina(List.of(), 7, 12, 30));

    mvc.perform(autenticado(get("/api/lancamentos?pagina=7")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.itens.length()").value(0))
        .andExpect(jsonPath("$.pagina").value(7))
        .andExpect(jsonPath("$.total").value(30))
        .andExpect(jsonPath("$.totalPaginas").value(3));
  }

  @Test
  void deveAceitarTamanhoNosLimites1E100() throws Exception {
    when(service.listarPagina(eq(USUARIO), any(), eq(0), anyInt()))
        .thenAnswer(i -> pagina(List.of(), 0, i.getArgument(3), 0));

    mvc.perform(autenticado(get("/api/lancamentos?tamanho=1"))).andExpect(status().isOk());
    mvc.perform(autenticado(get("/api/lancamentos?tamanho=100")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tamanho").value(100));
  }

  @Test
  void deveRecusarTamanhoAcimaDoMaximoCom400() throws Exception {
    mvc.perform(autenticado(get("/api/lancamentos?tamanho=101")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
        .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
        .andExpect(jsonPath("$.erros[0].campo").value("tamanho"))
        .andExpect(
            jsonPath("$.erros[0].mensagem").value("O tamanho da página deve ser de 1 a 100"));
    verifyNoInteractions(service);
  }

  @Test
  void deveRecusarTamanhoZeroOuNegativoCom400() throws Exception {
    for (String tamanho : List.of("0", "-5")) {
      mvc.perform(autenticado(get("/api/lancamentos?tamanho=" + tamanho)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.erros[0].campo").value("tamanho"))
          .andExpect(
              jsonPath("$.erros[0].mensagem").value("O tamanho da página deve ser de 1 a 100"));
    }
    verifyNoInteractions(service);
  }

  @Test
  void deveRecusarPaginaNegativaCom400() throws Exception {
    mvc.perform(autenticado(get("/api/lancamentos?pagina=-1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("pagina"))
        .andExpect(jsonPath("$.erros[0].mensagem").value("A página deve ser maior ou igual a 0"));
    verifyNoInteractions(service);
  }

  @Test
  void deveDevolver400ParaFiltroComTipoInvalido() throws Exception {
    mvc.perform(autenticado(get("/api/lancamentos?tipo=XPTO")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("tipo"));
  }

  @Test
  void deveDevolver404QuandoCategoriaDoFiltroNaoEDoUsuario() throws Exception {
    when(service.listarPagina(eq(USUARIO), any(), anyInt(), anyInt()))
        .thenThrow(
            new RecursoNaoEncontradoException(
                CodigoErro.CATEGORIA_NAO_ENCONTRADA, "Categoria não encontrada"));

    mvc.perform(autenticado(get("/api/lancamentos?categoriaId=99")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.mensagem").value("Categoria não encontrada"))
        .andExpect(jsonPath("$.codigo").value("CATEGORIA_NAO_ENCONTRADA"));
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
        new RecursoNaoEncontradoException(
            CodigoErro.LANCAMENTO_NAO_ENCONTRADO, "Lançamento não encontrado");
    when(service.buscar(USUARIO, 2L)).thenThrow(naoEncontrado);
    when(service.editar(eq(USUARIO), eq(2L), any(), any())).thenThrow(naoEncontrado);
    when(service.alterarStatus(USUARIO, 2L, StatusLancamento.INATIVO)).thenThrow(naoEncontrado);
    doThrow(naoEncontrado).when(service).excluir(USUARIO, 2L);
    when(service.restaurar(USUARIO, 2L)).thenThrow(naoEncontrado);

    mvc.perform(autenticado(get("/api/lancamentos/2")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.mensagem").value("Lançamento não encontrado"))
        .andExpect(jsonPath("$.codigo").value("LANCAMENTO_NAO_ENCONTRADO"));
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
            LocalDate.of(2026, 8, 5),
            new BigDecimal("8500.00"),
            List.of(3L));
    when(service.criar(USUARIO, esperado, null)).thenReturn(resumo(StatusLancamento.ATIVO));

    mvc.perform(json(post("/api/lancamentos"), CORPO))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.status").value("ATIVO"));
  }

  @Test
  void deveRepassarOStatusDoCorpoNoPostENoPut() throws Exception {
    String inativo = CORPO.replace("\"categoriaIds\"", "\"status\":\"INATIVO\",\"categoriaIds\"");
    when(service.criar(eq(USUARIO), any(), eq(StatusLancamento.INATIVO)))
        .thenReturn(resumo(StatusLancamento.INATIVO));
    when(service.editar(eq(USUARIO), eq(1L), any(), eq(StatusLancamento.INATIVO)))
        .thenReturn(resumo(StatusLancamento.INATIVO));

    mvc.perform(json(post("/api/lancamentos"), inativo))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("INATIVO"));
    mvc.perform(json(put("/api/lancamentos/1"), inativo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INATIVO"));
  }

  @Test
  void deveRepassarStatusNuloQuandoNaoVemNoPut() throws Exception {
    when(service.editar(USUARIO, 1L, novoDoCorpo(), null))
        .thenReturn(resumo(StatusLancamento.ATIVO));

    mvc.perform(json(put("/api/lancamentos/1"), CORPO)).andExpect(status().isOk());

    verify(service).editar(USUARIO, 1L, novoDoCorpo(), null);
  }

  private static NovoLancamento novoDoCorpo() {
    return new NovoLancamento(
        "Salário mensal",
        TipoLancamento.RECEITA,
        LocalDate.of(2026, 8, 5),
        new BigDecimal("8500.00"),
        List.of(3L));
  }

  @Test
  void deveDevolver400ParaStatusDeletadoNoPostENoPut() throws Exception {
    String deletado = CORPO.replace("\"categoriaIds\"", "\"status\":\"DELETADO\",\"categoriaIds\"");

    mvc.perform(json(post("/api/lancamentos"), deletado))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
        .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
        .andExpect(jsonPath("$.erros[0].campo").value("status"))
        .andExpect(jsonPath("$.erros[0].mensagem").value("Informe o status ATIVO ou INATIVO"));
    mvc.perform(json(put("/api/lancamentos/1"), deletado))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("status"));
    verifyNoInteractions(service);
  }

  @Test
  void deveDevolver400ComErrosPorCampo() throws Exception {
    mvc.perform(
            json(
                post("/api/lancamentos"),
                """
                {"descricao":"","tipo":null,"data":null,"valor":0,"categoriaIds":[]}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
        .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
        .andExpect(jsonPath("$.erros.length()").value(5))
        .andExpect(jsonPath("$.erros[?(@.campo == 'data')].mensagem").value("A data é obrigatória"))
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
                {"descricao":"%s","tipo":"DESPESA","data":"2026-01-31","valor":1.234,
                 "categoriaIds":[1]}
                """
                    .formatted("a".repeat(101))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros.length()").value(2));
  }

  @Test
  void deveDevolver400NoCampoDataParaDiaQueNaoExiste() throws Exception {
    mvc.perform(json(post("/api/lancamentos"), CORPO.replace("2026-08-05", "2026-02-31")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
        .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
        .andExpect(jsonPath("$.erros[0].campo").value("data"))
        .andExpect(
            jsonPath("$.erros[0].mensagem")
                .value("Data inválida. Use o formato AAAA-MM-DD com um dia que exista."));
    verifyNoInteractions(service);
  }

  @Test
  void deveDevolver400NoCampoDataForaDoFormatoIso() throws Exception {
    mvc.perform(json(post("/api/lancamentos"), CORPO.replace("2026-08-05", "05/08/2026")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("data"));
  }

  @Test
  void deveResponderAuditoriaComHoraEDataDeExclusao() throws Exception {
    OffsetDateTime excluido = OffsetDateTime.of(2026, 8, 3, 9, 30, 15, 0, ZoneOffset.UTC);
    LancamentoResumo deletado =
        new LancamentoResumo(
            1L,
            "Cinema",
            TipoLancamento.DESPESA,
            LocalDate.of(2026, 8, 2),
            new BigDecimal("45.90"),
            StatusLancamento.DELETADO,
            List.of(),
            OffsetDateTime.of(2026, 8, 2, 20, 0, 0, 0, ZoneOffset.UTC),
            excluido,
            excluido);
    when(service.buscar(USUARIO, 1L)).thenReturn(deletado);

    mvc.perform(autenticado(get("/api/lancamentos/1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value("2026-08-02"))
        .andExpect(jsonPath("$.dataAtualizacao").value("2026-08-03T09:30:15Z"))
        .andExpect(jsonPath("$.dataExclusao").value("2026-08-03T09:30:15Z"));
  }

  @Test
  void deveDevolver400ParaTipoInexistenteNoCorpo() throws Exception {
    mvc.perform(json(post("/api/lancamentos"), CORPO.replace("RECEITA", "OUTRO")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deveDevolver422ParaCategoriaInativa() throws Exception {
    when(service.criar(eq(USUARIO), any(), any()))
        .thenThrow(
            new RegraNegocioException(
                CodigoErro.CATEGORIA_INATIVA,
                "A categoria \"X\" está inativa e não pode ser usada"));

    mvc.perform(json(post("/api/lancamentos"), CORPO))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422));
  }

  @Test
  void deveEditar() throws Exception {
    when(service.editar(eq(USUARIO), eq(1L), any(), any()))
        .thenReturn(resumo(StatusLancamento.ATIVO));

    mvc.perform(json(put("/api/lancamentos/1"), CORPO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  void deveDevolver422AoEditarDeletado() throws Exception {
    when(service.editar(eq(USUARIO), eq(1L), any(), any()))
        .thenThrow(
            new RegraNegocioException(
                CodigoErro.LANCAMENTO_NA_LIXEIRA, "Lançamento excluído não pode ser editado."));

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
        .thenThrow(
            new RegraNegocioException(
                CodigoErro.LANCAMENTO_NA_LIXEIRA, "Não é possível alterar o status"));

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
    doThrow(
            new RegraNegocioException(
                CodigoErro.LANCAMENTO_JA_EXCLUIDO, "O lançamento já está excluído"))
        .when(service)
        .excluir(USUARIO, 1L);

    mvc.perform(autenticado(delete("/api/lancamentos/1")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.mensagem").value("O lançamento já está excluído"))
        .andExpect(jsonPath("$.codigo").value("LANCAMENTO_JA_EXCLUIDO"));
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
        .thenThrow(
            new RegraNegocioException(
                CodigoErro.LANCAMENTO_NAO_EXCLUIDO,
                "Só é possível restaurar um lançamento excluído"));

    mvc.perform(autenticado(post("/api/lancamentos/1/restaurar")))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void deveDevolver400ComParametroInvalidoParaIdNaoNumerico() throws Exception {
    mvc.perform(autenticado(get("/api/lancamentos/abc")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
        .andExpect(
            jsonPath("$.mensagem").value("Parâmetro inválido: id. Confira o valor enviado."));

    verifyNoInteractions(service);
  }

  @Test
  void deveDevolver406EmJsonQuandoOAcceptNaoAceitaJson() throws Exception {
    when(service.buscar(USUARIO, 1L)).thenReturn(resumo(StatusLancamento.ATIVO));

    mvc.perform(autenticado(get("/api/lancamentos/1")).accept("text/csv"))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(406))
        .andExpect(jsonPath("$.codigo").value("TIPO_RESPOSTA_NAO_ACEITO"));
  }

  /** Erro antes de escrever a resposta: sai com o status e o código dele, em JSON (não 406). */
  @Test
  void erroComAcceptNaoJsonDeveSairEmJsonComCodigo() throws Exception {
    when(service.buscar(USUARIO, 99L))
        .thenThrow(
            new RecursoNaoEncontradoException(
                CodigoErro.LANCAMENTO_NAO_ENCONTRADO, "Lançamento não encontrado"));

    for (String accept : List.of("text/csv", "application/octet-stream")) {
      mvc.perform(autenticado(get("/api/lancamentos/99")).accept(accept))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.codigo").value("LANCAMENTO_NAO_ENCONTRADO"));
      mvc.perform(
              autenticado(post("/api/lancamentos"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}")
                  .accept(accept))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
          .andExpect(jsonPath("$.erros").isArray());
    }
  }
}
