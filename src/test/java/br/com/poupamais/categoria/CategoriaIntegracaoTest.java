package br.com.poupamais.categoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.poupamais.categoria.model.entidades.Categoria;
import br.com.poupamais.categoria.publico.CategoriaFacade;
import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.categoria.service.CategoriaService;
import br.com.poupamais.shared.exception.ConflitoException;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// Contra PostgreSQL real: índice único lower(descricao) e vínculo na associativa só se provam no
// banco. Sem POUPAMAIS_TEST_DB_URL o teste é pulado.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class CategoriaIntegracaoTest {

  @DynamicPropertySource
  static void banco(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", () -> System.getenv("POUPAMAIS_TEST_DB_URL"));
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private CategoriaService service;
  @Autowired private CategoriaFacade facade;

  private Long usuarioA;
  private Long usuarioB;

  private Long criarUsuario() {
    return jdbc.queryForObject(
        "insert into poupamais.usuario (nome, email, senha, aceite_termos_em)"
            + " values ('Teste', ?, 'x', now()) returning id",
        Long.class,
        "categoria-" + UUID.randomUUID() + "@teste.com");
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

  @Test
  void indiceUnicoDoBancoNaoDiferenciaMaiusculas() {
    jdbc.update(
        "insert into poupamais.categoria (id_usuario, descricao) values (?, 'Lazer')", usuarioA);

    assertThatThrownBy(() ->
            jdbc.update(
                "insert into poupamais.categoria (id_usuario, descricao) values (?, 'LAZER')",
                usuarioA))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deveRecusarDuplicadaCaseInsensitiveMasPermitirEmOutroUsuario() {
    service.criar(usuarioA, "Pets");

    assertThatThrownBy(() -> service.criar(usuarioA, "PETS")).isInstanceOf(ConflitoException.class);
    assertThat(service.criar(usuarioB, "pets").getId()).isNotNull();
  }

  @Test
  void deveIsolarCategoriasEntreUsuarios() {
    facade.criarPadroes(usuarioA);
    Categoria deB = service.criar(usuarioB, "Só do B");

    List<Categoria> doA = service.listar(usuarioA, null);

    assertThat(doA).hasSize(11).noneMatch(c -> c.getId().equals(deB.getId()));
    assertThatThrownBy(() -> service.editar(usuarioA, deB.getId(), "Roubada"))
        .isInstanceOf(RecursoNaoEncontradoException.class);
    assertThatThrownBy(() -> service.excluir(usuarioA, deB.getId()))
        .isInstanceOf(RecursoNaoEncontradoException.class);
    assertThatThrownBy(() -> facade.validarAtivasDoUsuario(usuarioA, List.of(deB.getId())))
        .isInstanceOf(RecursoNaoEncontradoException.class);
    assertThat(facade.buscarPorIds(usuarioA, List.of(deB.getId()))).isEmpty();
  }

  @Test
  void deveListarAtivasPrimeiroEmOrdemAlfabetica() {
    service.criar(usuarioA, "zebra");
    Categoria b = service.criar(usuarioA, "Banana");
    service.criar(usuarioA, "abacaxi");
    service.alterarStatus(usuarioA, b.getId(), false);

    assertThat(service.listar(usuarioA, null))
        .extracting(Categoria::getDescricao)
        .containsExactly("abacaxi", "zebra", "Banana");
    assertThat(service.listar(usuarioA, false))
        .extracting(Categoria::getDescricao)
        .containsExactly("Banana");
  }

  @Test
  void deveBloquearExclusaoDeCategoriaVinculadaELiberarSemVinculo() {
    Categoria vinculada = service.criar(usuarioA, "Mercado");
    Categoria livre = service.criar(usuarioA, "Livre");
    Long idLancamento =
        jdbc.queryForObject(
            "insert into poupamais.lancamento (id_usuario, descricao, mes, ano, valor, tipo, status)"
                + " values (?, 'Compra', 8, 2026, 10.00, 'DESPESA', 'DELETADO') returning id",
            Long.class,
            usuarioA);
    jdbc.update(
        "insert into poupamais.categorias_lancamentos (id_categoria, id_lancamento, id_usuario)"
            + " values (?, ?, ?)",
        vinculada.getId(),
        idLancamento,
        usuarioA);

    assertThatThrownBy(() -> service.excluir(usuarioA, vinculada.getId()))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Categoria vinculada a lançamentos, apenas inative");

    service.excluir(usuarioA, livre.getId());
    assertThat(facade.buscarPorIds(usuarioA, List.of(livre.getId(), vinculada.getId())))
        .extracting(CategoriaResumo::id)
        .containsExactly(vinculada.getId());
  }

  @Test
  void buscarOuCriarDeveReaproveitarSemDiferenciarMaiusculas() {
    CategoriaResumo criada = facade.buscarOuCriar(usuarioA, "Viagem");

    CategoriaResumo encontrada = facade.buscarOuCriar(usuarioA, "  VIAGEM ");

    assertThat(encontrada.id()).isEqualTo(criada.id());
  }
}
