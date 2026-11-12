package br.com.poupamais.lancamento.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.poupamais.categoria.CategoriaFacade;
import br.com.poupamais.categoria.CategoriaResumo;
import br.com.poupamais.lancamento.FiltroLancamento;
import br.com.poupamais.lancamento.LancamentoResumo;
import br.com.poupamais.lancamento.NovoLancamento;
import br.com.poupamais.lancamento.StatusLancamento;
import br.com.poupamais.lancamento.TipoLancamento;
import br.com.poupamais.lancamento.model.Lancamento;
import br.com.poupamais.lancamento.model.LancamentoRepository;
import br.com.poupamais.lancamento.service.LancamentoResumoConversor;
import br.com.poupamais.lancamento.service.LancamentoValidator;
import br.com.poupamais.shared.domain.Periodo;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class LancamentoServiceImplTest {

  private static final Long USUARIO = 1L;

  @Mock private LancamentoRepository repository;
  @Mock private CategoriaFacade categoriaFacade;

  private LancamentoServiceImpl service;

  @BeforeEach
  void montar() {
    service =
        new LancamentoServiceImpl(
            repository,
            new LancamentoValidator(),
            categoriaFacade,
            new LancamentoResumoConversor(categoriaFacade));
  }

  private static NovoLancamento novo(List<Long> categorias) {
    return new NovoLancamento(
        " Aluguel ", TipoLancamento.DESPESA, 8, 2026, new BigDecimal("2100"), categorias);
  }

  private static Lancamento existente(StatusLancamento status, Long... categorias) {
    Lancamento l =
        Lancamento.builder()
            .id(10L)
            .idUsuario(USUARIO)
            .descricao("Antigo")
            .tipo(TipoLancamento.RECEITA)
            .mes(1)
            .ano(2025)
            .valor(new BigDecimal("1.00"))
            .status(status)
            .dataCadastro(LocalDate.of(2025, 1, 1))
            .build();
    l.definirCategorias(List.of(categorias));
    return l;
  }

  private void categoriasExistem(CategoriaResumo... categorias) {
    when(categoriaFacade.buscarPorIds(eq(USUARIO), anyCollection()))
        .thenReturn(List.of(categorias));
  }

  private void salvarDevolveOMesmo() {
    when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
  }

  private void encontra(Lancamento l) {
    when(repository.findByIdAndIdUsuario(10L, USUARIO)).thenReturn(Optional.of(l));
  }

  // ---------- criar ----------

  @Test
  void deveCriarAtivoComDadosNormalizadosECategoriasOrdenadas() {
    salvarDevolveOMesmo();
    categoriasExistem(
        new CategoriaResumo(5L, "Moradia", true), new CategoriaResumo(2L, "Casa", true));

    LancamentoResumo criado = service.criar(USUARIO, novo(List.of(5L, 2L, 5L)));

    assertThat(criado.descricao()).isEqualTo("Aluguel");
    assertThat(criado.status()).isEqualTo(StatusLancamento.ATIVO);
    assertThat(criado.valor()).isEqualByComparingTo("2100.00");
    assertThat(criado.categorias()).extracting(CategoriaResumo::id).containsExactly(2L, 5L);
    assertThat(criado.dataAtualizacao()).isNull();
    verify(categoriaFacade).validarAtivasDoUsuario(USUARIO, List.of(5L, 2L));
  }

  @Test
  void deveGravarIdsRepetidosUmaVez() {
    salvarDevolveOMesmo();
    categoriasExistem(new CategoriaResumo(3L, "Lazer", true));
    ArgumentCaptor<Lancamento> salvo = ArgumentCaptor.forClass(Lancamento.class);

    service.criar(USUARIO, novo(List.of(3L, 3L, 3L)));

    verify(repository).saveAndFlush(salvo.capture());
    assertThat(salvo.getValue().getCategorias()).hasSize(1);
    assertThat(salvo.getValue().getIdUsuario()).isEqualTo(USUARIO);
  }

  @Test
  void deveRecusarCategoriaInativaCom422() {
    doThrow(new RegraNegocioException("A categoria \"Lazer\" está inativa e não pode ser usada"))
        .when(categoriaFacade)
        .validarAtivasDoUsuario(USUARIO, List.of(3L));

    assertThatThrownBy(() -> service.criar(USUARIO, novo(List.of(3L))))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessageContaining("inativa");
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void deveRecusarCategoriaInexistenteOuDeOutroCom404() {
    doThrow(new RecursoNaoEncontradoException("Categoria não encontrada"))
        .when(categoriaFacade)
        .validarAtivasDoUsuario(USUARIO, List.of(99L));

    assertThatThrownBy(() -> service.criar(USUARIO, novo(List.of(99L))))
        .isInstanceOf(RecursoNaoEncontradoException.class)
        .hasMessage("Categoria não encontrada");
  }

  @Test
  void deveValidarRn001AntesDeConsultarCategorias() {
    NovoLancamento semValor =
        new NovoLancamento("x", TipoLancamento.DESPESA, 8, 2026, null, List.of(1L));

    assertThatThrownBy(() -> service.criar(USUARIO, semValor))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("O valor é obrigatório");
    verifyNoInteractions(categoriaFacade, repository);
  }

  // ---------- buscar ----------

  @Test
  void deveBuscarLancamentoDoUsuario() {
    encontra(existente(StatusLancamento.ATIVO, 1L));
    categoriasExistem(new CategoriaResumo(1L, "Salário", true));

    LancamentoResumo resumo = service.buscar(USUARIO, 10L);

    assertThat(resumo.id()).isEqualTo(10L);
    assertThat(resumo.categorias())
        .extracting(CategoriaResumo::descricao)
        .containsExactly("Salário");
  }

  @Test
  void deveDevolver404ParaLancamentoDeOutroUsuarioOuInexistente() {
    when(repository.findByIdAndIdUsuario(anyLong(), eq(USUARIO))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.buscar(USUARIO, 10L))
        .isInstanceOf(RecursoNaoEncontradoException.class)
        .hasMessage("Lançamento não encontrado");
    assertThatThrownBy(() -> service.editar(USUARIO, 10L, novo(List.of(1L))))
        .isInstanceOf(RecursoNaoEncontradoException.class);
    assertThatThrownBy(() -> service.alterarStatus(USUARIO, 10L, StatusLancamento.INATIVO))
        .isInstanceOf(RecursoNaoEncontradoException.class);
    assertThatThrownBy(() -> service.excluir(USUARIO, 10L))
        .isInstanceOf(RecursoNaoEncontradoException.class);
    assertThatThrownBy(() -> service.restaurar(USUARIO, 10L))
        .isInstanceOf(RecursoNaoEncontradoException.class);
  }

  // ---------- editar ----------

  @Test
  void deveEditarTrocandoCategoriasEMarcandoAtualizacao() {
    Lancamento l = existente(StatusLancamento.INATIVO, 1L, 2L);
    encontra(l);
    salvarDevolveOMesmo();
    categoriasExistem(new CategoriaResumo(2L, "B", false), new CategoriaResumo(3L, "C", true));

    LancamentoResumo editado = service.editar(USUARIO, 10L, novo(List.of(2L, 3L)));

    assertThat(editado.descricao()).isEqualTo("Aluguel");
    assertThat(editado.tipo()).isEqualTo(TipoLancamento.DESPESA);
    assertThat(editado.mes()).isEqualTo(8);
    assertThat(editado.ano()).isEqualTo(2026);
    assertThat(editado.status()).isEqualTo(StatusLancamento.INATIVO);
    assertThat(editado.dataAtualizacao()).isEqualTo(LocalDate.now());
    assertThat(editado.categorias()).extracting(CategoriaResumo::id).containsExactly(2L, 3L);
    // A categoria 2 já estava no lançamento: só a nova (3) precisa estar ativa
    verify(categoriaFacade).validarAtivasDoUsuario(USUARIO, List.of(3L));
  }

  @Test
  void naoDeveEditarDeletado() {
    encontra(existente(StatusLancamento.DELETADO, 1L));

    assertThatThrownBy(() -> service.editar(USUARIO, 10L, novo(List.of(1L))))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Lançamento excluído não pode ser editado. Restaure-o antes.");
    verify(repository, never()).saveAndFlush(any());
  }

  // ---------- status ----------

  @Test
  void deveAlternarAtivoParaInativo() {
    Lancamento l = existente(StatusLancamento.ATIVO, 1L);
    encontra(l);
    categoriasExistem();

    LancamentoResumo resumo = service.alterarStatus(USUARIO, 10L, StatusLancamento.INATIVO);

    assertThat(resumo.status()).isEqualTo(StatusLancamento.INATIVO);
    assertThat(resumo.dataAtualizacao()).isEqualTo(LocalDate.now());
    verify(repository).save(l);
  }

  @Test
  void deveAceitarMesmoStatusSemAlterarNada() {
    Lancamento l = existente(StatusLancamento.INATIVO, 1L);
    encontra(l);
    categoriasExistem();

    LancamentoResumo resumo = service.alterarStatus(USUARIO, 10L, StatusLancamento.INATIVO);

    assertThat(resumo.status()).isEqualTo(StatusLancamento.INATIVO);
    assertThat(resumo.dataAtualizacao()).isNull();
    verify(repository, never()).save(any());
  }

  @Test
  void naoDeveAlternarDeletado() {
    encontra(existente(StatusLancamento.DELETADO, 1L));

    assertThatThrownBy(() -> service.alterarStatus(USUARIO, 10L, StatusLancamento.ATIVO))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Não é possível alterar o status de DELETADO para ATIVO");
  }

  @Test
  void deveRecusarStatusDeletadoOuNuloNoPatch() {
    assertThatThrownBy(() -> service.alterarStatus(USUARIO, 10L, StatusLancamento.DELETADO))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Informe o status ATIVO ou INATIVO");
    assertThatThrownBy(() -> service.alterarStatus(USUARIO, 10L, null))
        .isInstanceOf(RegraNegocioException.class);
    verifyNoInteractions(repository);
  }

  // ---------- excluir / restaurar ----------

  @Test
  void deveExcluirLogicamente() {
    Lancamento l = existente(StatusLancamento.INATIVO, 1L);
    encontra(l);

    service.excluir(USUARIO, 10L);

    assertThat(l.getStatus()).isEqualTo(StatusLancamento.DELETADO);
    assertThat(l.getDataAtualizacao()).isEqualTo(LocalDate.now());
    verify(repository).save(l);
    verify(repository, never()).delete(any(Lancamento.class));
  }

  @Test
  void naoDeveExcluirJaExcluido() {
    encontra(existente(StatusLancamento.DELETADO, 1L));

    assertThatThrownBy(() -> service.excluir(USUARIO, 10L))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("O lançamento já está excluído");
  }

  @Test
  void deveRestaurarComoInativo() {
    Lancamento l = existente(StatusLancamento.DELETADO, 1L);
    encontra(l);
    when(repository.save(l)).thenReturn(l);
    categoriasExistem();

    LancamentoResumo resumo = service.restaurar(USUARIO, 10L);

    assertThat(resumo.status()).isEqualTo(StatusLancamento.INATIVO);
    assertThat(resumo.dataAtualizacao()).isEqualTo(LocalDate.now());
  }

  @Test
  void naoDeveRestaurarQuemNaoEstaExcluido() {
    encontra(existente(StatusLancamento.ATIVO, 1L));

    assertThatThrownBy(() -> service.restaurar(USUARIO, 10L))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Só é possível restaurar um lançamento excluído");
  }

  // ---------- listagens / facade ----------

  @SuppressWarnings("unchecked")
  private void findAllDevolve(List<Lancamento> lancamentos) {
    when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(lancamentos);
  }

  @Test
  void deveListarBuscandoNomesDeCategoriaUmaVezSo() {
    findAllDevolve(
        List.of(
            existente(StatusLancamento.ATIVO, 1L, 2L), existente(StatusLancamento.INATIVO, 2L)));
    categoriasExistem(new CategoriaResumo(1L, "A", true), new CategoriaResumo(2L, "B", true));

    List<LancamentoResumo> lista = service.listar(USUARIO, FiltroLancamento.vazio());

    assertThat(lista).hasSize(2);
    assertThat(lista.get(0).categorias()).extracting(CategoriaResumo::id).containsExactly(1L, 2L);
    verify(categoriaFacade).buscarPorIds(eq(USUARIO), anyCollection());
  }

  @Test
  void deveListarComFiltroNuloComoVazio() {
    findAllDevolve(List.of());

    assertThat(service.listar(USUARIO, null)).isEmpty();
    verifyNoInteractions(categoriaFacade);
  }

  @Test
  void deveDevolver404QuandoFiltroDeCategoriaNaoEDoUsuario() {
    when(categoriaFacade.buscarPorIds(USUARIO, List.of(77L))).thenReturn(List.of());
    FiltroLancamento filtro = new FiltroLancamento(null, null, null, null, null, 77L);

    assertThatThrownBy(() -> service.listar(USUARIO, filtro))
        .isInstanceOf(RecursoNaoEncontradoException.class)
        .hasMessage("Categoria não encontrada");
  }

  @Test
  void deveListarComFiltroDeCategoriaDoUsuario() {
    when(categoriaFacade.buscarPorIds(USUARIO, List.of(1L)))
        .thenReturn(List.of(new CategoriaResumo(1L, "A", true)));
    findAllDevolve(List.of());

    assertThat(service.listar(USUARIO, new FiltroLancamento(null, null, null, null, null, 1L)))
        .isEmpty();
  }

  @Test
  void facadeCriarDevolveResumo() {
    salvarDevolveOMesmo();
    categoriasExistem(new CategoriaResumo(1L, "A", true));

    LancamentoResumo resumo = service.criar(USUARIO, novo(List.of(1L)));

    assertThat(resumo.tipo()).isEqualTo(TipoLancamento.DESPESA);
    assertThat(resumo.dataCadastro()).isNull(); // @PrePersist só roda no banco
  }

  @Test
  void listarAtivosSemPeriodosDevolveVazioSemConsultar() {
    assertThat(service.listarAtivos(USUARIO, List.of())).isEmpty();
    assertThat(service.listarAtivos(USUARIO, null)).isEmpty();
    verifyNoInteractions(repository);
  }

  @Test
  void listarAtivosConsultaOsPeriodos() {
    findAllDevolve(List.of(existente(StatusLancamento.ATIVO, 1L)));
    categoriasExistem(new CategoriaResumo(1L, "A", true));

    assertThat(service.listarAtivos(USUARIO, new Periodo(8, 2026).ultimos(4))).hasSize(1);
  }

  @Test
  void listarTodosDevolveQualquerStatus() {
    findAllDevolve(
        List.of(existente(StatusLancamento.DELETADO, 1L), existente(StatusLancamento.ATIVO, 1L)));
    categoriasExistem(new CategoriaResumo(1L, "A", true));

    assertThat(service.listarTodos(USUARIO))
        .extracting(LancamentoResumo::status)
        .containsExactly(StatusLancamento.DELETADO, StatusLancamento.ATIVO);
  }
}
