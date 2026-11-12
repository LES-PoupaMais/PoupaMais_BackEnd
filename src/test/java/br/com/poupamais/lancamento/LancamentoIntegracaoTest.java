package br.com.poupamais.lancamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
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
      Long usuario, String descricao, TipoLancamento tipo, int mes, int ano, List<Long> cats) {
    return facade.criar(
        usuario, new NovoLancamento(descricao, tipo, mes, ano, new BigDecimal("10.00"), cats));
  }

  @Test
  void deveGravarAssociativaComIdUsuarioEDatas() {
    LancamentoResumo criado =
        criar(usuarioA, "Feira", TipoLancamento.DESPESA, 8, 2026, List.of(lazer, mercado, lazer));

    Integer vinculos =
        jdbc.queryForObject(
            "select count(*) from poupamais.categorias_lancamentos"
                + " where id_lancamento = ? and id_usuario = ?",
            Integer.class,
            criado.id(),
            usuarioA);
    assertThat(vinculos).isEqualTo(2);
    assertThat(criado.dataCadastro()).isEqualTo(LocalDate.now());
    assertThat(criado.valor()).isEqualByComparingTo("10.00");
    assertThat(criado.categorias())
        .extracting(CategoriaResumo::id)
        .containsExactly(Math.min(lazer, mercado), Math.max(lazer, mercado));
  }

  @Test
  void deveEditarTrocandoCategorias() {
    LancamentoResumo criado =
        criar(usuarioA, "Feira", TipoLancamento.DESPESA, 8, 2026, List.of(mercado));

    LancamentoResumo editado =
        service.editar(
            usuarioA,
            criado.id(),
            new NovoLancamento(
                "Cinema", TipoLancamento.DESPESA, 9, 2026, new BigDecimal("30"), List.of(lazer)));

    assertThat(editado.categorias()).extracting(CategoriaResumo::id).containsExactly(lazer);
    assertThat(editado.dataAtualizacao()).isEqualTo(LocalDate.now());
    assertThat(service.buscar(usuarioA, criado.id()).descricao()).isEqualTo("Cinema");
  }

  @Test
  void deveCombinarFiltrosEOrdenarPorAnoMesIdDecrescentes() {
    LancamentoResumo a =
        criar(usuarioA, "Mercado Extra", TipoLancamento.DESPESA, 1, 2026, List.of(mercado));
    LancamentoResumo b =
        criar(usuarioA, "Salário", TipoLancamento.RECEITA, 12, 2025, List.of(lazer));
    LancamentoResumo c =
        criar(usuarioA, "MERCADO dia", TipoLancamento.DESPESA, 1, 2026, List.of(lazer));
    LancamentoResumo d =
        criar(usuarioA, "mercado 100%", TipoLancamento.DESPESA, 3, 2026, List.of(mercado));
    service.alterarStatus(usuarioA, d.id(), StatusLancamento.INATIVO);
    LancamentoResumo e =
        criar(usuarioA, "Mercado apagado", TipoLancamento.DESPESA, 1, 2026, List.of(mercado));
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
        criar(usuarioA, "Feira", TipoLancamento.DESPESA, 8, 2026, List.of(mercado, lazer));

    assertThat(ids(filtro(null, null, null, null, null, mercado))).containsExactly(a.id());
    assertThat(ids(FiltroLancamento.vazio())).containsExactly(a.id());
  }

  @Test
  void deveIsolarLancamentosEntreUsuarios() {
    LancamentoResumo doA =
        criar(usuarioA, "Feira", TipoLancamento.DESPESA, 8, 2026, List.of(mercado));
    Long categoriaB = categorias.buscarOuCriar(usuarioB, "Outra").id();

    assertThat(facade.listarTodos(usuarioB)).isEmpty();
    assertThatThrownBy(() -> service.buscar(usuarioB, doA.id()))
        .isInstanceOf(RecursoNaoEncontradoException.class);
    assertThatThrownBy(() -> service.excluir(usuarioB, doA.id()))
        .isInstanceOf(RecursoNaoEncontradoException.class);
    assertThatThrownBy(
            () -> service.listar(usuarioA, filtro(null, null, null, null, null, categoriaB)))
        .isInstanceOf(RecursoNaoEncontradoException.class);
    assertThatThrownBy(
            () -> criar(usuarioA, "x", TipoLancamento.DESPESA, 8, 2026, List.of(categoriaB)))
        .isInstanceOf(RecursoNaoEncontradoException.class);
  }

  @Test
  void deveRecusarCategoriaInativaNoBanco() {
    jdbc.update("update poupamais.categoria set ativa = false where id = ?", lazer);

    assertThatThrownBy(() -> criar(usuarioA, "x", TipoLancamento.DESPESA, 8, 2026, List.of(lazer)))
        .isInstanceOf(RegraNegocioException.class);
  }

  @Test
  void facadeListaAtivosDosPeriodosETodosOsStatus() {
    LancamentoResumo ago =
        criar(usuarioA, "Ago", TipoLancamento.DESPESA, 8, 2026, List.of(mercado));
    LancamentoResumo dez =
        criar(usuarioA, "Dez", TipoLancamento.RECEITA, 12, 2025, List.of(mercado));
    LancamentoResumo inativo =
        criar(usuarioA, "Ina", TipoLancamento.DESPESA, 8, 2026, List.of(mercado));
    service.alterarStatus(usuarioA, inativo.id(), StatusLancamento.INATIVO);
    LancamentoResumo fora =
        criar(usuarioA, "Fora", TipoLancamento.DESPESA, 1, 2024, List.of(mercado));
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
    LancamentoResumo l = criar(usuarioA, "x", TipoLancamento.DESPESA, 8, 2026, List.of(mercado));

    service.excluir(usuarioA, l.id());
    LancamentoResumo restaurado = service.restaurar(usuarioA, l.id());

    assertThat(restaurado.status()).isEqualTo(StatusLancamento.INATIVO);
    assertThat(
            jdbc.queryForObject(
                "select status from poupamais.lancamento where id = ?", String.class, l.id()))
        .isEqualTo("INATIVO");
  }

  @Test
  void deveBloquearExclusaoDeCategoriaVinculadaCom422() throws Exception {
    criar(usuarioA, "x", TipoLancamento.DESPESA, 8, 2026, List.of(mercado));
    String bearer = "Bearer " + jwtService.gerar(usuarioA).token();

    mvc.perform(delete("/api/categorias/" + mercado).header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.mensagem").value("Categoria vinculada a lançamentos, apenas inative"));
  }

  @Test
  void fkDeferrableImpedeApagarCategoriaVinculadaNoCommit() {
    criar(usuarioA, "x", TipoLancamento.DESPESA, 8, 2026, List.of(mercado));

    assertThatThrownBy(() ->
            transacao.executeWithoutResult(
                s -> jdbc.update("delete from poupamais.categoria where id = ?", mercado)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void exclusaoDaContaApagaTudoEmCascata() {
    LancamentoResumo l = criar(usuarioA, "x", TipoLancamento.DESPESA, 8, 2026, List.of(mercado));

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
    LancamentoResumo l = criar(usuarioA, "x", TipoLancamento.DESPESA, 8, 2026, List.of(mercado));

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
