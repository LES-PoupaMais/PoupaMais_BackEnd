package br.com.poupamais.categoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.categoria.service.CategoriaComQuantidade;
import br.com.poupamais.categoria.service.CategoriaService;
import br.com.poupamais.shared.domain.Periodo;
import br.com.poupamais.shared.security.JwtService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// Contra PostgreSQL real: a contagem agregada de GET /api/categorias (status, intervalo de datas do
// mês, associativa com várias categorias e isolamento por usuário) só se prova no banco.
// Sem POUPAMAIS_TEST_DB_URL o teste é pulado.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class CategoriaQuantidadeLancamentosIntegracaoTest {

  @DynamicPropertySource
  static void banco(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", () -> System.getenv("POUPAMAIS_TEST_DB_URL"));
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private CategoriaService service;
  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;

  private Long usuarioA;
  private Long usuarioB;

  private Long criarUsuario() {
    return jdbc.queryForObject(
        "insert into poupamais.usuario (nome, email, senha, aceite_termos_em)"
            + " values ('Teste', ?, 'x', now()) returning id",
        Long.class,
        "categoria-qtd-" + UUID.randomUUID() + "@teste.com");
  }

  @BeforeEach
  void usuarios() {
    usuarioA = criarUsuario();
    usuarioB = criarUsuario();
  }

  @AfterEach
  void limpar() {
    // Cascata: apaga categorias, lançamentos e associativa (FK DEFERRABLE)
    jdbc.update("delete from poupamais.usuario where id in (?, ?)", usuarioA, usuarioB);
  }

  private Long categoria(Long dono, String descricao) {
    return service.criar(dono, descricao).getId();
  }

  private void lancamento(Long dono, LocalDate data, String status, Long... categorias) {
    Long id =
        jdbc.queryForObject(
            "insert into poupamais.lancamento (id_usuario, descricao, data, valor, tipo, status)"
                + " values (?, 'Teste', ?, 10.00, 'DESPESA', ?) returning id",
            Long.class,
            dono,
            data,
            status);
    for (Long categoria : categorias) {
      jdbc.update(
          "insert into poupamais.categorias_lancamentos (id_categoria, id_lancamento, id_usuario)"
              + " values (?, ?, ?)",
          categoria,
          id,
          dono);
    }
  }

  private Map<Long, Long> quantidades(Long dono, Periodo periodo) {
    return service.listarComQuantidade(dono, null, periodo).stream()
        .collect(
            Collectors.toMap(
                i -> i.categoria().getId(), CategoriaComQuantidade::quantidadeLancamentos));
  }

  @Test
  void deveContarAtivosEInativosDoMesIgnorandoDeletadosEOutrosMeses() {
    Long mercado = categoria(usuarioA, "Mercado");
    lancamento(usuarioA, LocalDate.of(2026, 8, 10), "ATIVO", mercado);
    lancamento(usuarioA, LocalDate.of(2026, 8, 11), "INATIVO", mercado);
    lancamento(usuarioA, LocalDate.of(2026, 8, 12), "DELETADO", mercado);
    lancamento(usuarioA, LocalDate.of(2026, 7, 10), "ATIVO", mercado);
    lancamento(usuarioA, LocalDate.of(2026, 9, 10), "ATIVO", mercado);
    lancamento(usuarioA, LocalDate.of(2025, 8, 10), "ATIVO", mercado);

    assertThat(quantidades(usuarioA, new Periodo(8, 2026))).containsEntry(mercado, 2L);
    assertThat(quantidades(usuarioA, new Periodo(7, 2026))).containsEntry(mercado, 1L);
  }

  @Test
  void deveIncluirOPrimeiroEOUltimoDiaDoMesEExcluirOsVizinhos() {
    Long mercado = categoria(usuarioA, "Mercado");
    lancamento(usuarioA, LocalDate.of(2024, 1, 31), "ATIVO", mercado);
    lancamento(usuarioA, LocalDate.of(2024, 2, 1), "ATIVO", mercado);
    lancamento(usuarioA, LocalDate.of(2024, 2, 29), "INATIVO", mercado);
    lancamento(usuarioA, LocalDate.of(2024, 3, 1), "ATIVO", mercado);

    assertThat(quantidades(usuarioA, new Periodo(2, 2024))).containsEntry(mercado, 2L);
  }

  @Test
  void deveDevolverZeroParaCategoriaSemLancamentoNoMes() {
    Long mercado = categoria(usuarioA, "Mercado");
    Long vazia = categoria(usuarioA, "Vazia");
    Long soDeletado = categoria(usuarioA, "Só deletado");
    lancamento(usuarioA, LocalDate.of(2026, 8, 10), "ATIVO", mercado);
    lancamento(usuarioA, LocalDate.of(2026, 8, 10), "DELETADO", soDeletado);

    Map<Long, Long> quantidades = quantidades(usuarioA, new Periodo(8, 2026));

    assertThat(quantidades)
        .containsEntry(mercado, 1L)
        .containsEntry(vazia, 0L)
        .containsEntry(soDeletado, 0L);
  }

  @Test
  void deveContarLancamentoComDuasCategoriasEmAmbas() {
    Long mercado = categoria(usuarioA, "Mercado");
    Long lazer = categoria(usuarioA, "Lazer");
    lancamento(usuarioA, LocalDate.of(2026, 8, 10), "ATIVO", mercado, lazer);
    lancamento(usuarioA, LocalDate.of(2026, 8, 11), "ATIVO", lazer);

    assertThat(quantidades(usuarioA, new Periodo(8, 2026)))
        .containsEntry(mercado, 1L)
        .containsEntry(lazer, 2L);
  }

  @Test
  void deveIsolarAContagemEntreUsuarios() {
    Long doA = categoria(usuarioA, "Mercado");
    Long doB = categoria(usuarioB, "Mercado");
    lancamento(usuarioA, LocalDate.of(2026, 8, 10), "ATIVO", doA);
    lancamento(usuarioB, LocalDate.of(2026, 8, 10), "ATIVO", doB);
    lancamento(usuarioB, LocalDate.of(2026, 8, 11), "ATIVO", doB);

    Map<Long, Long> doUsuarioA = quantidades(usuarioA, new Periodo(8, 2026));

    assertThat(doUsuarioA).containsOnlyKeys(doA).containsEntry(doA, 1L);
    assertThat(quantidades(usuarioB, new Periodo(8, 2026))).containsEntry(doB, 2L);
  }

  @Test
  void deveManterOFiltroAtivaEAOrdemDaListagem() {
    Long zebra = categoria(usuarioA, "zebra");
    Long banana = categoria(usuarioA, "Banana");
    Long abacaxi = categoria(usuarioA, "abacaxi");
    service.alterarStatus(usuarioA, banana, false);
    lancamento(usuarioA, LocalDate.of(2026, 8, 10), "ATIVO", banana, zebra);

    List<CategoriaComQuantidade> todas =
        service.listarComQuantidade(usuarioA, null, new Periodo(8, 2026));
    List<CategoriaComQuantidade> inativas =
        service.listarComQuantidade(usuarioA, false, new Periodo(8, 2026));

    assertThat(todas)
        .extracting(i -> i.categoria().getId())
        .containsExactly(abacaxi, zebra, banana);
    assertThat(inativas)
        .singleElement()
        .satisfies(i -> {
          assertThat(i.categoria().getId()).isEqualTo(banana);
          assertThat(i.quantidadeLancamentos()).isEqualTo(1L);
        });
  }

  @Test
  void deveUsarOMesAtualPorPadraoNaApi() throws Exception {
    Long mercado = categoria(usuarioA, "Mercado");
    Periodo atual = Periodo.atual();
    lancamento(usuarioA, atual.inicio(), "ATIVO", mercado);
    lancamento(usuarioA, atual.fim(), "INATIVO", mercado);
    lancamento(usuarioA, atual.anterior().fim(), "ATIVO", mercado);
    String bearer = "Bearer " + jwtService.gerar(usuarioA).token();

    mvc.perform(get("/api/categorias").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(mercado))
        .andExpect(jsonPath("$[0].descricao").value("Mercado"))
        .andExpect(jsonPath("$[0].ativa").value(true))
        .andExpect(jsonPath("$[0].quantidadeLancamentos").value(2));

    Periodo anterior = atual.anterior();
    mvc.perform(
            get("/api/categorias")
                .param("mes", String.valueOf(anterior.mes()))
                .param("ano", String.valueOf(anterior.ano()))
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].quantidadeLancamentos").value(1));
  }
}
