package br.com.poupamais.usuario.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.poupamais.categoria.publico.CategoriaFacade;
import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.lancamento.publico.LancamentoFacade;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.shared.exception.ErroAutenticacaoException;
import br.com.poupamais.usuario.endpoints.dto.DadosTitularResponse;
import br.com.poupamais.usuario.endpoints.dto.DadosTitularResponse.CategoriaDoLancamento;
import br.com.poupamais.usuario.endpoints.dto.DadosTitularResponse.CategoriaExportada;
import br.com.poupamais.usuario.endpoints.dto.DadosTitularResponse.LancamentoExportado;
import br.com.poupamais.usuario.endpoints.dto.DadosTitularResponse.PerfilExportado;
import br.com.poupamais.usuario.model.entidades.Usuario;
import br.com.poupamais.usuario.model.repositories.FotoPerfilRepository;
import br.com.poupamais.usuario.model.repositories.UsuarioRepository;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExportacaoDadosServiceImplTest {

  private static final Long USUARIO = 1L;
  private static final Instant AGORA = Instant.parse("2026-11-12T18:30:45.123Z");
  private static final Instant ACEITE = Instant.parse("2026-11-01T10:00:00Z");

  private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
  private final FotoPerfilRepository fotoRepository = mock(FotoPerfilRepository.class);
  private final CategoriaFacade categoriaFacade = mock(CategoriaFacade.class);
  private final LancamentoFacade lancamentoFacade = mock(LancamentoFacade.class);
  private final ExportacaoDadosServiceImpl service =
      new ExportacaoDadosServiceImpl(
          usuarioRepository, fotoRepository, categoriaFacade, lancamentoFacade);

  private final Usuario ana =
      Usuario.builder()
          .id(USUARIO)
          .nome("Ana")
          .email("ana@email.com")
          .senha("$2a$10$hashsecreto")
          .dataCadastro(LocalDate.of(2026, 11, 1))
          .aceiteTermosEm(ACEITE)
          .senhaAlteradaEm(Instant.parse("2026-11-05T12:00:00Z"))
          .build();

  @BeforeEach
  void relogio() {
    service.usarRelogio(Clock.fixed(AGORA, ZoneOffset.UTC));
  }

  private static LancamentoResumo lancamento(
      Long id, StatusLancamento status, List<CategoriaResumo> categorias) {
    return new LancamentoResumo(
        id,
        "Lançamento " + id,
        TipoLancamento.DESPESA,
        8,
        2026,
        new BigDecimal("120.50"),
        status,
        categorias,
        LocalDate.of(2026, 8, 1),
        status == StatusLancamento.ATIVO ? null : LocalDate.of(2026, 8, 10));
  }

  @Test
  void deveExportarPerfilCategoriasELancamentosEmTodosOsStatus() {
    CategoriaResumo lazer = new CategoriaResumo(10L, "Lazer", true);
    CategoriaResumo antiga = new CategoriaResumo(11L, "Antiga", false);
    when(usuarioRepository.findById(USUARIO)).thenReturn(Optional.of(ana));
    when(fotoRepository.existsById(USUARIO)).thenReturn(true);
    when(categoriaFacade.listarTodas(USUARIO)).thenReturn(List.of(lazer, antiga));
    when(lancamentoFacade.listarTodos(USUARIO))
        .thenReturn(
            List.of(
                lancamento(3L, StatusLancamento.DELETADO, List.of(antiga)),
                lancamento(2L, StatusLancamento.INATIVO, List.of(lazer)),
                lancamento(1L, StatusLancamento.ATIVO, List.of(lazer, antiga))));

    DadosTitularResponse dados = service.exportar(USUARIO);

    assertThat(dados.geradoEm()).isEqualTo(Instant.parse("2026-11-12T18:30:45Z"));
    assertThat(dados.perfil())
        .isEqualTo(
            new PerfilExportado(
                USUARIO, "Ana", "ana@email.com", LocalDate.of(2026, 11, 1), ACEITE, true));
    assertThat(dados.categorias())
        .containsExactly(
            new CategoriaExportada(10L, "Lazer", true),
            new CategoriaExportada(11L, "Antiga", false));
    assertThat(dados.lancamentos())
        .extracting(LancamentoExportado::id, LancamentoExportado::status)
        .containsExactly(
            tuple(3L, StatusLancamento.DELETADO),
            tuple(2L, StatusLancamento.INATIVO),
            tuple(1L, StatusLancamento.ATIVO));
    LancamentoExportado ativo = dados.lancamentos().get(2);
    assertThat(ativo.descricao()).isEqualTo("Lançamento 1");
    assertThat(ativo.tipo()).isEqualTo(TipoLancamento.DESPESA);
    assertThat(ativo.mes()).isEqualTo(8);
    assertThat(ativo.ano()).isEqualTo(2026);
    assertThat(ativo.valor()).isEqualByComparingTo("120.50");
    assertThat(ativo.dataCadastro()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(ativo.dataAtualizacao()).isNull();
    assertThat(ativo.categorias())
        .containsExactly(
            new CategoriaDoLancamento(10L, "Lazer"), new CategoriaDoLancamento(11L, "Antiga"));
    assertThat(dados.lancamentos().get(0).dataAtualizacao()).isEqualTo(LocalDate.of(2026, 8, 10));
  }

  @Test
  void naoDeveExporDadosSensiveis() {
    when(usuarioRepository.findById(USUARIO)).thenReturn(Optional.of(ana));
    when(categoriaFacade.listarTodas(USUARIO)).thenReturn(List.of());
    when(lancamentoFacade.listarTodos(USUARIO)).thenReturn(List.of());

    DadosTitularResponse dados = service.exportar(USUARIO);

    assertThat(dados.toString()).doesNotContain("hashsecreto").doesNotContain("2026-11-05");
    assertThat(
            Arrays.stream(PerfilExportado.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("id", "nome", "email", "dataCadastro", "aceiteTermosEm", "possuiFoto");
    assertThat(dados.perfil().possuiFoto()).isFalse();
  }

  @Test
  void usuarioSemLancamentosDeveTerListaVazia() {
    when(usuarioRepository.findById(USUARIO)).thenReturn(Optional.of(ana));
    when(categoriaFacade.listarTodas(USUARIO))
        .thenReturn(List.of(new CategoriaResumo(10L, "Lazer", true)));
    when(lancamentoFacade.listarTodos(USUARIO)).thenReturn(List.of());

    DadosTitularResponse dados = service.exportar(USUARIO);

    assertThat(dados.lancamentos()).isEmpty();
    assertThat(dados.categorias()).hasSize(1);
    assertThat(dados.perfil().nome()).isEqualTo("Ana");
  }

  @Test
  void usuarioInexistenteDeveSerSessaoInvalidaSemConsultarOutrosModulos() {
    when(usuarioRepository.findById(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.exportar(USUARIO))
        .isInstanceOf(ErroAutenticacaoException.class)
        .hasMessage(PerfilServiceImpl.MSG_SESSAO);
    verifyNoInteractions(categoriaFacade, lancamentoFacade);
  }
}
