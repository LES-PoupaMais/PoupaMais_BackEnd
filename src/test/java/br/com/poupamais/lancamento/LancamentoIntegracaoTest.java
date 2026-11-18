package br.com.poupamais.lancamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.categoria.publico.CategoriaFacade;
import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.lancamento.publico.LancamentoFacade;
import br.com.poupamais.lancamento.publico.dto.FiltroLancamento;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.lancamento.service.LancamentoService;
import br.com.poupamais.shared.domain.Periodo;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.shared.security.JwtService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.assertj.core.data.TemporalUnitOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

// Contra PostgreSQL real: Specifications, ordenação, associativa com id_usuario, cascata e FK
// DEFERRABLE só se provam no banco. Sem POUPAMAIS_TEST_DB_URL o teste é pulado.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class LancamentoIntegracaoTest {

  private static final TemporalUnitOffset PROXIMO = within(5, ChronoUnit.SECONDS);

  @DynamicPropertySource
  static void banco(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", () -> System.getenv("POUPAMAIS_TEST_DB_URL"));
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transacao;
  @Autowired private LancamentoService service;
  @Autowired private LancamentoFacade facade;
  @Autowired private CategoriaFacade categorias;
  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;

  private Long usuarioA;
  private Long usuarioB;
  private Long mercado;
  private Long lazer;

  private Long criarUsuario() {
    return jdbc.queryForObject(
        "insert into poupamais.usuario (nome, email, senha, aceite_termos_em)"
            + " values ('Teste', ?, 'x', now()) returning id",
        Long.class,
        "lancamento-" + UUID.randomUUID() + "@teste.com");
  }

  @BeforeEach
  void dados() {
    usuarioA = criarUsuario();
    usuarioB = criarUsuario();
    mercado = categorias.buscarOuCriar(usuarioA, "Mercado").id();
    lazer = categorias.buscarOuCriar(usuarioA, "Lazer").id();
  }

  @AfterEach
  void limpar() {
    jdbc.update("delete from poupamais.usuario where id in (?, ?)", usuarioA, usuarioB);
  }

  private LancamentoResumo criar(
      Long usuario, String descricao, TipoLancamento tipo, LocalDate data, List<Long> cats) {
    return facade.criar(
        usuario, new NovoLancamento(descricao, tipo, data, new BigDecimal("10.00"), cats));
  }

  @Test
  void deveGravarAssociativaComIdUsuarioEDatas() {
    LancamentoResumo criado =
        criar(
            usuarioA,
            "Feira",
            TipoLancamento.DESPESA,
            LocalDate.of(2026, 8, 15),
            List.of(lazer, mercado, lazer));

    Integer vinculos =
        jdbc.queryForObject(
            "select count(*) from poupamais.categorias_lancamentos"
                + " where id_lancamento = ? and id_usuario = ?",
            Integer.class,
            criado.id(),
            usuarioA);
    assertThat(vinculos).isEqualTo(2);
    assertThat(criado.dataCadastro()).isCloseTo(OffsetDateTime.now(), PROXIMO);
    assertThat(criado.data()).isEqualTo(LocalDate.of(2026, 8, 15));
    assertThat(criado.valor()).isEqualByComparingTo("10.00");
    assertThat(criado.categorias())
        .extracting(CategoriaResumo::id)
        .containsExactly(Math.min(lazer, mercado), Math.max(lazer, mercado));
  }

  @Test
  void deveEditarTrocandoCategorias() {
    LancamentoResumo criado =
        criar(
            usuarioA, "Feira", TipoLancamento.DESPESA, LocalDate.of(2026, 8, 15), List.of(mercado));

    LancamentoResumo editado =
        service.editar(
            usuarioA,
            criado.id(),
            new NovoLancamento(
                "Cinema",
                TipoLancamento.DESPESA,
                LocalDate.of(2026, 9, 30),
                new BigDecimal("30"),
                List.of(lazer)));

    assertThat(editado.categorias()).extracting(CategoriaResumo::id).containsExactly(lazer);
    assertThat(editado.data()).isEqualTo(LocalDate.of(2026, 9, 30));
    assertThat(editado.dataAtualizacao()).isCloseTo(OffsetDateTime.now(), PROXIMO);
    assertThat(editado.dataCadastro()).isEqualTo(criado.dataCadastro());
    assertThat(service.buscar(usuarioA, criado.id()).descricao()).isEqualTo("Cinema");
  }

  @Test
  void deveCombinarFiltrosEOrdenarPorDataEIdDecrescentes() {
    LancamentoResumo a =
        criar(
            usuarioA,
            "Mercado Extra",
            TipoLancamento.DESPESA,
            LocalDate.of(2026, 1, 15),
            List.of(mercado));
    LancamentoResumo b =
        criar(
            usuarioA,
            "Salário",
            TipoLancamento.RECEITA,
            LocalDate.of(2025, 12, 15),
            List.of(lazer));
    LancamentoResumo c =
        criar(
            usuarioA,
            "MERCADO dia",
            TipoLancamento.DESPESA,
            LocalDate.of(2026, 1, 15),
            List.of(lazer));
    LancamentoResumo d =
        criar(
            usuarioA,
            "mercado 100%",
            TipoLancamento.DESPESA,
            LocalDate.of(2026, 3, 15),
            List.of(mercado));
    service.alterarStatus(usuarioA, d.id(), StatusLancamento.INATIVO);
    LancamentoResumo e =
        criar(
            usuarioA,
            "Mercado apagado",
            TipoLancamento.DESPESA,
            LocalDate.of(2026, 1, 15),
            List.of(mercado));
    service.excluir(usuarioA, e.id());

    assertThat(ids(FiltroLancamento.vazio())).containsExactly(d.id(), c.id(), a.id(), b.id());
    assertThat(ids(filtro("mercado", null, null, null, null, null)))
        .containsExactly(d.id(), c.id(), a.id());
    assertThat(ids(filtro("100%", null, null, null, null, null))).containsExactly(d.id());
    assertThat(ids(filtro(null, 1, 2026, null, null, null))).containsExactly(c.id(), a.id());
    assertThat(ids(filtro(null, null, 2025, TipoLancamento.RECEITA, null, null)))
        .containsExactly(b.id());
    assertThat(ids(filtro(null, null, null, null, StatusLancamento.INATIVO, null)))
        .containsExactly(d.id());
    assertThat(ids(filtro(null, null, null, null, StatusLancamento.DELETADO, null)))
        .containsExactly(e.id());
    assertThat(ids(filtro("mercado", 1, 2026, TipoLancamento.DESPESA, null, mercado)))
        .containsExactly(a.id());
  }

  @Test
  void naoDeveDuplicarLancamentoComVariasCategoriasNoFiltro() {
    LancamentoResumo a =
        criar(
            usuarioA,
            "Feira",
            TipoLancamento.DESPESA,
            LocalDate.of(2026, 8, 15),
            List.of(mercado, lazer));

    assertThat(ids(filtro(null, null, null, null, null, mercado))).containsExactly(a.id());
    assertThat(ids(FiltroLancamento.vazio())).containsExactly(a.id());
  }

  @Test
  void deveIsolarLancamentosEntreUsuarios() {
    LancamentoResumo doA =
        criar(
            usuarioA, "Feira", TipoLancamento.DESPESA, LocalDate.of(2026, 8, 15), List.of(mercado));
    Long categoriaB = categorias.buscarOuCriar(usuarioB, "Outra").id();

    assertThat(facade.listarTodos(usuarioB)).isEmpty();
    assertThatThrownBy(() -> service.buscar(usuarioB, doA.id()))
        .isInstanceOf(RecursoNaoEncontradoException.class);
    assertThatThrownBy(() -> service.excluir(usuarioB, doA.id()))
        .isInstanceOf(RecursoNaoEncontradoException.class);
    assertThatThrownBy(
            () -> service.listar(usuarioA, filtro(null, null, null, null, null, categoriaB)))
        .isInstanceOf(RecursoNaoEncontradoException.class);
    assertThatThrownBy(() ->
            criar(
                usuarioA,
                "x",
                TipoLancamento.DESPESA,
                LocalDate.of(2026, 8, 15),
                List.of(categoriaB)))
        .isInstanceOf(RecursoNaoEncontradoException.class);
  }

  @Test
  void deveRecusarCategoriaInativaNoBanco() {
    jdbc.update("update poupamais.categoria set ativa = false where id = ?", lazer);

    assertThatThrownBy(() ->
            criar(usuarioA, "x", TipoLancamento.DESPESA, LocalDate.of(2026, 8, 15), List.of(lazer)))
        .isInstanceOf(RegraNegocioException.class);
  }

  @Test
  void facadeListaAtivosDosPeriodosETodosOsStatus() {
    LancamentoResumo ago =
        criar(usuarioA, "Ago", TipoLancamento.DESPESA, LocalDate.of(2026, 8, 15), List.of(mercado));
    LancamentoResumo dez =
        criar(
            usuarioA, "Dez", TipoLancamento.RECEITA, LocalDate.of(2025, 12, 15), List.of(mercado));
    LancamentoResumo inativo =
        criar(usuarioA, "Ina", TipoLancamento.DESPESA, LocalDate.of(2026, 8, 15), List.of(mercado));
    service.alterarStatus(usuarioA, inativo.id(), StatusLancamento.INATIVO);
    LancamentoResumo fora =
        criar(
            usuarioA, "Fora", TipoLancamento.DESPESA, LocalDate.of(2024, 1, 15), List.of(mercado));
    service.excluir(usuarioA, fora.id());

    assertThat(facade.listarAtivos(usuarioA, List.of(new Periodo(8, 2026), new Periodo(12, 2025))))
        .extracting(LancamentoResumo::id)
        .containsExactly(ago.id(), dez.id());
    assertThat(facade.listarTodos(usuarioA))
        .extracting(LancamentoResumo::id)
        .containsExactly(inativo.id(), ago.id(), dez.id(), fora.id());
    assertThat(facade.listar(usuarioA, FiltroLancamento.vazio())).hasSize(3);
  }

  @Test
  void deveExcluirERestaurarComoInativo() {
    LancamentoResumo l =
        criar(usuarioA, "x", TipoLancamento.DESPESA, LocalDate.of(2026, 8, 15), List.of(mercado));

    service.excluir(usuarioA, l.id());
    LancamentoResumo restaurado = service.restaurar(usuarioA, l.id());

    assertThat(restaurado.status()).isEqualTo(StatusLancamento.INATIVO);
    assertThat(
            jdbc.queryForObject(
                "select status from poupamais.lancamento where id = ?", String.class, l.id()))
        .isEqualTo("INATIVO");
  }

  @Test
  void devePreencherDataDeExclusaoAoExcluirELimparAoRestaurar() {
    LancamentoResumo l =
        criar(usuarioA, "x", TipoLancamento.DESPESA, LocalDate.of(2026, 8, 15), List.of(mercado));
    assertThat(l.dataExclusao()).isNull();

    service.excluir(usuarioA, l.id());

    LancamentoResumo naLixeira = service.buscar(usuarioA, l.id());
    assertThat(naLixeira.dataExclusao()).isCloseTo(OffsetDateTime.now(), PROXIMO);
    assertThat(naLixeira.dataExclusao().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(dataExclusaoNoBanco(l.id())).isNotNull();

    LancamentoResumo restaurado = service.restaurar(usuarioA, l.id());

    assertThat(restaurado.dataExclusao()).isNull();
    assertThat(service.buscar(usuarioA, l.id()).dataExclusao()).isNull();
    assertThat(dataExclusaoNoBanco(l.id())).isNull();
  }

  @Test
  void filtroDoMesIncluiPrimeiroEUltimoDiaEExcluiOsVizinhos() {
    LancamentoResumo ultimoDeJaneiro = criarEm(LocalDate.of(2026, 1, 31));
    LancamentoResumo primeiroDeFevereiro = criarEm(LocalDate.of(2026, 2, 1));
    LancamentoResumo ultimoDeFevereiro = criarEm(LocalDate.of(2026, 2, 28));
    LancamentoResumo primeiroDeMarco = criarEm(LocalDate.of(2026, 3, 1));

    assertThat(ids(filtro(null, 2, 2026, null, null, null)))
        .containsExactly(ultimoDeFevereiro.id(), primeiroDeFevereiro.id());
    assertThat(ids(filtro(null, 1, 2026, null, null, null))).containsExactly(ultimoDeJaneiro.id());
    assertThat(ids(filtro(null, 3, 2026, null, null, null))).containsExactly(primeiroDeMarco.id());
    assertThat(facade.listarAtivos(usuarioA, List.of(new Periodo(2, 2026))))
        .extracting(LancamentoResumo::id)
        .containsExactly(ultimoDeFevereiro.id(), primeiroDeFevereiro.id());
  }

  @Test
  void filtroDoMesEmAnoBissextoIncluiO29DeFevereiro() {
    LancamentoResumo bissexto = criarEm(LocalDate.of(2028, 2, 29));
    criarEm(LocalDate.of(2028, 3, 1));

    assertThat(ids(filtro(null, 2, 2028, null, null, null))).containsExactly(bissexto.id());
  }

  @Test
  void filtroSoDoAnoOuSoDoMesEValoresForaDaFaixa() {
    LancamentoResumo dez2025 = criarEm(LocalDate.of(2025, 12, 31));
    LancamentoResumo jan2026 = criarEm(LocalDate.of(2026, 1, 1));
    LancamentoResumo dez2026 = criarEm(LocalDate.of(2026, 12, 1));

    assertThat(ids(filtro(null, null, 2026, null, null, null)))
        .containsExactly(dez2026.id(), jan2026.id());
    assertThat(ids(filtro(null, 12, null, null, null, null)))
        .containsExactly(dez2026.id(), dez2025.id());
    assertThat(ids(filtro(null, 13, 2026, null, null, null))).isEmpty();
    assertThat(ids(filtro(null, 0, null, null, null, null))).isEmpty();
    assertThat(ids(filtro(null, null, 1999, null, null, null))).isEmpty();
  }

  @Test
  void deveOrdenarPeloDiaDentroDoMesEDesempatarPeloId() {
    LancamentoResumo dia20 = criarEm(LocalDate.of(2026, 5, 20));
    LancamentoResumo dia3 = criarEm(LocalDate.of(2026, 5, 3));
    LancamentoResumo outroDia20 = criarEm(LocalDate.of(2026, 5, 20));

    assertThat(ids(filtro(null, 5, 2026, null, null, null)))
        .containsExactly(outroDia20.id(), dia20.id(), dia3.id());
  }

  // ---------- paginação (GET /lancamentos) ----------

  /** 5 lançamentos: três no mesmo dia (empate desempatado pelo id) e dois em dias diferentes. */
  private List<Long> cincoEmOrdemDaListagem() {
    LancamentoResumo dia1 = criarEm(LocalDate.of(2026, 7, 1));
    LancamentoResumo a = criarEm(LocalDate.of(2026, 7, 10));
    LancamentoResumo b = criarEm(LocalDate.of(2026, 7, 10));
    LancamentoResumo dia20 = criarEm(LocalDate.of(2026, 7, 20));
    LancamentoResumo c = criarEm(LocalDate.of(2026, 7, 10));
    return List.of(dia20.id(), c.id(), b.id(), a.id(), dia1.id());
  }

  private List<Long> idsDaPagina(int pagina, int tamanho) {
    return service.listarPagina(usuarioA, FiltroLancamento.vazio(), pagina, tamanho).stream()
        .map(LancamentoResumo::id)
        .toList();
  }

  @Test
  void paginasSeguidasCobremTudoNaOrdemDaListagemSemRepetir() {
    List<Long> esperado = cincoEmOrdemDaListagem();

    Page<LancamentoResumo> primeira =
        service.listarPagina(usuarioA, FiltroLancamento.vazio(), 0, 2);
    Page<LancamentoResumo> ultima = service.listarPagina(usuarioA, FiltroLancamento.vazio(), 2, 2);

    assertThat(primeira.getTotalElements()).isEqualTo(5);
    assertThat(primeira.getTotalPages()).isEqualTo(3);
    assertThat(primeira.getContent())
        .extracting(LancamentoResumo::id)
        .containsExactly(esperado.get(0), esperado.get(1));
    assertThat(idsDaPagina(1, 2)).containsExactly(esperado.get(2), esperado.get(3));
    assertThat(ultima.getContent())
        .extracting(LancamentoResumo::id)
        .containsExactly(esperado.get(4));
    assertThat(ultima.getContent().getFirst().categorias())
        .extracting(CategoriaResumo::id)
        .containsExactly(mercado);
  }

  @Test
  void ordemEstavelEntrePaginasComDatasIguais() {
    List<Long> esperado = cincoEmOrdemDaListagem();

    for (int i = 0; i < 3; i++) {
      List<Long> concatenado = new ArrayList<>();
      for (int pagina = 0; pagina < 5; pagina++) {
        concatenado.addAll(idsDaPagina(pagina, 1));
      }
      assertThat(concatenado).containsExactlyElementsOf(esperado);
    }
  }

  @Test
  void paginaAlemDoFimVemVaziaComTotalCorreto() {
    cincoEmOrdemDaListagem();

    Page<LancamentoResumo> alem = service.listarPagina(usuarioA, FiltroLancamento.vazio(), 9, 2);

    assertThat(alem.getContent()).isEmpty();
    assertThat(alem.getTotalElements()).isEqualTo(5);
    assertThat(alem.getTotalPages()).isEqualTo(3);
  }

  @Test
  void apiPaginaComPadrao12EFiltroAplicadoAoTotal() throws Exception {
    for (int i = 0; i < 13; i++) {
      criarEm(LocalDate.of(2026, 3, 1 + i));
    }
    criar(usuarioA, "Outro mês", TipoLancamento.RECEITA, LocalDate.of(2026, 4, 1), List.of(lazer));
    criar(
        usuarioB,
        "De outro usuário",
        TipoLancamento.DESPESA,
        LocalDate.of(2026, 3, 5),
        List.of(categorias.buscarOuCriar(usuarioB, "Mercado").id()));
    String bearer = "Bearer " + jwtService.gerar(usuarioA).token();

    mvc.perform(
            get("/api/lancamentos")
                .param("mes", "3")
                .param("ano", "2026")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.itens.length()").value(12))
        .andExpect(jsonPath("$.itens[0].data").value("2026-03-13"))
        .andExpect(jsonPath("$.itens[0].categorias[0].descricao").value("Mercado"))
        .andExpect(jsonPath("$.pagina").value(0))
        .andExpect(jsonPath("$.tamanho").value(12))
        .andExpect(jsonPath("$.total").value(13))
        .andExpect(jsonPath("$.totalPaginas").value(2));
    mvc.perform(
            get("/api/lancamentos")
                .param("mes", "3")
                .param("ano", "2026")
                .param("pagina", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.itens.length()").value(1))
        .andExpect(jsonPath("$.itens[0].data").value("2026-03-01"))
        .andExpect(jsonPath("$.total").value(13));
    mvc.perform(
            get("/api/lancamentos")
                .param("descricao", "nada-assim")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.itens.length()").value(0))
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.totalPaginas").value(0));
    mvc.perform(
            get("/api/lancamentos")
                .param("tamanho", "101")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("tamanho"));
  }

  @Test
  void exportacaoIgnoraAPaginacao() throws Exception {
    for (int i = 0; i < 13; i++) {
      criarEm(LocalDate.of(2026, 3, 1 + i));
    }
    String bearer = "Bearer " + jwtService.gerar(usuarioA).token();

    String csv =
        mvc.perform(
                get("/api/lancamentos/exportar")
                    .param("formato", "csv")
                    .param("mes", "3")
                    .param("ano", "2026")
                    .param("pagina", "0")
                    .param("tamanho", "1")
                    .header(HttpHeaders.AUTHORIZATION, bearer))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(csv.lines().filter(l -> !l.isBlank()).count()).isEqualTo(14); // cabeçalho + 13
    assertThat(facade.listar(usuarioA, filtro(null, 3, 2026, null, null, null))).hasSize(13);
  }

  @Test
  void apiRecusaDia31EmMesCurtoCom400() throws Exception {
    String bearer = "Bearer " + jwtService.gerar(usuarioA).token();
    String corpo =
        """
        {"descricao":"x","tipo":"DESPESA","data":"%s","valor":10,"categoriaIds":[%d]}
        """;

    for (String data : List.of("2026-02-31", "2026-04-31", "2026-02-29")) {
      mvc.perform(
              post("/api/lancamentos")
                  .header(HttpHeaders.AUTHORIZATION, bearer)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(corpo.formatted(data, mercado)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.erros[0].campo").value("data"));
    }
    mvc.perform(
            post("/api/lancamentos")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo.formatted("2026-01-31", mercado)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data").value("2026-01-31"))
        .andExpect(jsonPath("$.dataExclusao").doesNotExist());
    assertThat(facade.listarTodos(usuarioA)).hasSize(1);
  }

  @Test
  void bancoRecusaDataForaDaFaixa() {
    LancamentoResumo l = criarEm(LocalDate.of(2026, 8, 15));

    assertThatThrownBy(() ->
            jdbc.update(
                "update poupamais.lancamento set data = date '1999-12-31' where id = ?", l.id()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("update poupamais.lancamento set data = null where id = ?", l.id()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private LancamentoResumo criarEm(LocalDate data) {
    return criar(usuarioA, "x", TipoLancamento.DESPESA, data, List.of(mercado));
  }

  private OffsetDateTime dataExclusaoNoBanco(Long id) {
    return jdbc.queryForObject(
        "select data_exclusao from poupamais.lancamento where id = ?", OffsetDateTime.class, id);
  }

  @Test
  void deveBloquearExclusaoDeCategoriaVinculadaCom422() throws Exception {
    criar(usuarioA, "x", TipoLancamento.DESPESA, LocalDate.of(2026, 8, 15), List.of(mercado));
    String bearer = "Bearer " + jwtService.gerar(usuarioA).token();

    mvc.perform(delete("/api/categorias/" + mercado).header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.mensagem").value("Categoria vinculada a lançamentos, apenas inative"));
  }

  @Test
  void fkDeferrableImpedeApagarCategoriaVinculadaNoCommit() {
    criar(usuarioA, "x", TipoLancamento.DESPESA, LocalDate.of(2026, 8, 15), List.of(mercado));

    assertThatThrownBy(() ->
            transacao.executeWithoutResult(
                s -> jdbc.update("delete from poupamais.categoria where id = ?", mercado)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void exclusaoDaContaApagaTudoEmCascata() {
    LancamentoResumo l =
        criar(usuarioA, "x", TipoLancamento.DESPESA, LocalDate.of(2026, 8, 15), List.of(mercado));

    jdbc.update("delete from poupamais.usuario where id = ?", usuarioA);

    assertThat(
            jdbc.queryForObject(
                "select count(*) from poupamais.lancamento where id = ?", Integer.class, l.id()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from poupamais.categorias_lancamentos where id_lancamento = ?",
                Integer.class,
                l.id()))
        .isZero();
  }

  @Test
  void bancoRecusaStatusForaDoCheck() {
    LancamentoResumo l =
        criar(usuarioA, "x", TipoLancamento.DESPESA, LocalDate.of(2026, 8, 15), List.of(mercado));

    assertThatThrownBy(() ->
            jdbc.update("update poupamais.lancamento set status = 'XPTO' where id = ?", l.id()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private List<Long> ids(FiltroLancamento filtro) {
    return facade.listar(usuarioA, filtro).stream().map(LancamentoResumo::id).toList();
  }

  private static FiltroLancamento filtro(
      String descricao,
      Integer mes,
      Integer ano,
      TipoLancamento tipo,
      StatusLancamento status,
      Long categoriaId) {
    return new FiltroLancamento(descricao, mes, ano, tipo, status, categoriaId);
  }
}
