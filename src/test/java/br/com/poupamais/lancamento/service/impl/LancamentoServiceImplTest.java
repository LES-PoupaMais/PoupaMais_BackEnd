package br.com.poupamais.lancamento.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.poupamais.categoria.publico.CategoriaFacade;
import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.lancamento.model.entidades.Lancamento;
import br.com.poupamais.lancamento.model.repositories.LancamentoRepository;
import br.com.poupamais.lancamento.publico.dto.FiltroLancamento;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.lancamento.service.LancamentoResumoConversor;
import br.com.poupamais.lancamento.service.LancamentoValidator;
import br.com.poupamais.shared.domain.Periodo;
import br.com.poupamais.shared.exception.CodigoErro;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.assertj.core.data.TemporalUnitOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class LancamentoServiceImplTest {

  private static final Long USUARIO = 1L;
  private static final LocalDate DATA = LocalDate.of(2026, 8, 31);
  private static final TemporalUnitOffset PROXIMO = within(5, ChronoUnit.SECONDS);

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
        " Aluguel ", TipoLancamento.DESPESA, DATA, new BigDecimal("2100"), categorias);
  }

  private static Lancamento existente(StatusLancamento status, Long... categorias) {
    Lancamento l =
        Lancamento.builder()
            .id(10L)
            .idUsuario(USUARIO)
            .descricao("Antigo")
            .tipo(TipoLancamento.RECEITA)
            .data(LocalDate.of(2025, 1, 31))
            .valor(new BigDecimal("1.00"))
            .status(status)
            .dataCadastro(OffsetDateTime.of(2025, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
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
    doThrow(
            new RegraNegocioException(
                CodigoErro.CATEGORIA_INATIVA,
                "A categoria \"Lazer\" está inativa e não pode ser usada"))
        .when(categoriaFacade)
        .validarAtivasDoUsuario(USUARIO, List.of(3L));

    assertThatThrownBy(() -> service.criar(USUARIO, novo(List.of(3L))))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessageContaining("inativa")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.CATEGORIA_INATIVA);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void deveRecusarCategoriaInexistenteOuDeOutroCom404() {
    doThrow(
            new RecursoNaoEncontradoException(
                CodigoErro.CATEGORIA_NAO_ENCONTRADA, "Categoria não encontrada"))
        .when(categoriaFacade)
        .validarAtivasDoUsuario(USUARIO, List.of(99L));

    assertThatThrownBy(() -> service.criar(USUARIO, novo(List.of(99L))))
        .isInstanceOf(RecursoNaoEncontradoException.class)
        .hasMessage("Categoria não encontrada")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.CATEGORIA_NAO_ENCONTRADA);
  }

  @Test
  void deveValidarRn001AntesDeConsultarCategorias() {
    NovoLancamento semValor =
        new NovoLancamento("x", TipoLancamento.DESPESA, DATA, null, List.of(1L));

    assertThatThrownBy(() -> service.criar(USUARIO, semValor))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("O valor é obrigatório")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.LANCAMENTO_INVALIDO);
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
        .hasMessage("Lançamento não encontrado")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.LANCAMENTO_NAO_ENCONTRADO);
    assertThatThrownBy(() -> service.editar(USUARIO, 10L, novo(List.of(1L)), null))
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

    LancamentoResumo editado = service.editar(USUARIO, 10L, novo(List.of(2L, 3L)), null);

    assertThat(editado.descricao()).isEqualTo("Aluguel");
    assertThat(editado.tipo()).isEqualTo(TipoLancamento.DESPESA);
    assertThat(editado.data()).isEqualTo(DATA);
    assertThat(editado.status()).isEqualTo(StatusLancamento.INATIVO);
    assertThat(editado.dataAtualizacao()).isCloseTo(OffsetDateTime.now(), PROXIMO);
    assertThat(editado.categorias()).extracting(CategoriaResumo::id).containsExactly(2L, 3L);
    // A categoria 2 já estava no lançamento: só a nova (3) precisa estar ativa
    verify(categoriaFacade).validarAtivasDoUsuario(USUARIO, List.of(3L));
  }

  @Test
  void naoDeveEditarDeletado() {
    encontra(existente(StatusLancamento.DELETADO, 1L));

    assertThatThrownBy(() -> service.editar(USUARIO, 10L, novo(List.of(1L)), null))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Lançamento excluído não pode ser editado. Restaure-o antes.")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.LANCAMENTO_NA_LIXEIRA);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void deveCriarJaComOStatusPedido() {
    salvarDevolveOMesmo();
    categoriasExistem(new CategoriaResumo(1L, "A", true));

    LancamentoResumo inativo = service.criar(USUARIO, novo(List.of(1L)), StatusLancamento.INATIVO);
    LancamentoResumo semStatus = service.criar(USUARIO, novo(List.of(1L)), null);

    assertThat(inativo.status()).isEqualTo(StatusLancamento.INATIVO);
    assertThat(inativo.dataAtualizacao()).isNull();
    assertThat(semStatus.status()).isEqualTo(StatusLancamento.ATIVO);
  }

  @Test
  void deveRecusarStatusDeletadoNoCadastroENaEdicao() {
    assertThatThrownBy(() -> service.criar(USUARIO, novo(List.of(1L)), StatusLancamento.DELETADO))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Informe o status ATIVO ou INATIVO")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.STATUS_INVALIDO);
    assertThatThrownBy(
            () -> service.editar(USUARIO, 10L, novo(List.of(1L)), StatusLancamento.DELETADO))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Informe o status ATIVO ou INATIVO")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.STATUS_INVALIDO);
    verifyNoInteractions(repository);
  }

  @Test
  void deveEditarTrocandoOStatus() {
    Lancamento l = existente(StatusLancamento.ATIVO, 1L);
    encontra(l);
    salvarDevolveOMesmo();
    categoriasExistem(new CategoriaResumo(1L, "A", true));

    LancamentoResumo editado =
        service.editar(USUARIO, 10L, novo(List.of(1L)), StatusLancamento.INATIVO);

    assertThat(editado.status()).isEqualTo(StatusLancamento.INATIVO);
    assertThat(editado.dataAtualizacao()).isCloseTo(OffsetDateTime.now(), PROXIMO);
  }

  @Test
  void deveEditarMantendoOStatusQuandoNaoVemOuEOMesmo() {
    Lancamento l = existente(StatusLancamento.INATIVO, 1L);
    encontra(l);
    salvarDevolveOMesmo();
    categoriasExistem(new CategoriaResumo(1L, "A", true));

    assertThat(service.editar(USUARIO, 10L, novo(List.of(1L)), null).status())
        .isEqualTo(StatusLancamento.INATIVO);
    assertThat(service.editar(USUARIO, 10L, novo(List.of(1L)), StatusLancamento.INATIVO).status())
        .isEqualTo(StatusLancamento.INATIVO);
  }

  @Test
  void naoDeveEditarDeletadoMesmoComStatus() {
    encontra(existente(StatusLancamento.DELETADO, 1L));

    assertThatThrownBy(
            () -> service.editar(USUARIO, 10L, novo(List.of(1L)), StatusLancamento.ATIVO))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Lançamento excluído não pode ser editado. Restaure-o antes.")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.LANCAMENTO_NA_LIXEIRA);
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
    assertThat(resumo.dataAtualizacao()).isCloseTo(OffsetDateTime.now(), PROXIMO);
    assertThat(resumo.dataExclusao()).isNull();
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
        .hasMessage("Não é possível alterar o status de DELETADO para ATIVO")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.LANCAMENTO_NA_LIXEIRA);
  }

  @Test
  void patchNaoTiraDaLixeira() {
    Lancamento l = existente(StatusLancamento.DELETADO, 1L);
    encontra(l);

    assertThatThrownBy(() -> service.alterarStatus(USUARIO, 10L, StatusLancamento.INATIVO))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Não é possível alterar o status de DELETADO para INATIVO")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.LANCAMENTO_NA_LIXEIRA);
    assertThat(l.getStatus()).isEqualTo(StatusLancamento.DELETADO);
    verify(repository, never()).save(any());
  }

  @Test
  void deveRecusarStatusDeletadoOuNuloNoPatch() {
    assertThatThrownBy(() -> service.alterarStatus(USUARIO, 10L, StatusLancamento.DELETADO))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Informe o status ATIVO ou INATIVO")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.STATUS_INVALIDO);
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
    assertThat(l.getDataAtualizacao()).isCloseTo(OffsetDateTime.now(), PROXIMO);
    assertThat(l.getDataExclusao()).isEqualTo(l.getDataAtualizacao());
    verify(repository).save(l);
    verify(repository, never()).delete(any(Lancamento.class));
  }

  @Test
  void naoDeveExcluirJaExcluido() {
    encontra(existente(StatusLancamento.DELETADO, 1L));

    assertThatThrownBy(() -> service.excluir(USUARIO, 10L))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("O lançamento já está excluído")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.LANCAMENTO_JA_EXCLUIDO);
  }

  @Test
  void deveRestaurarComoInativoQuemNaoTemStatusAnterior() {
    Lancamento l = existente(StatusLancamento.DELETADO, 1L);
    l.setDataExclusao(OffsetDateTime.of(2025, 2, 1, 12, 0, 0, 0, ZoneOffset.UTC));
    encontra(l);
    when(repository.save(l)).thenReturn(l);
    categoriasExistem();

    LancamentoResumo resumo = service.restaurar(USUARIO, 10L);

    assertThat(resumo.status()).isEqualTo(StatusLancamento.INATIVO);
    assertThat(resumo.dataAtualizacao()).isCloseTo(OffsetDateTime.now(), PROXIMO);
    assertThat(resumo.dataExclusao()).isNull();
  }

  @Test
  void deveRestaurarAoStatusQueTinhaAntesDaLixeira() {
    Lancamento l = existente(StatusLancamento.ATIVO, 1L);
    encontra(l);
    when(repository.save(l)).thenReturn(l);
    categoriasExistem();
    service.excluir(USUARIO, 10L);

    LancamentoResumo resumo = service.restaurar(USUARIO, 10L);

    assertThat(resumo.status()).isEqualTo(StatusLancamento.ATIVO);
    assertThat(l.getStatusAnterior()).isNull();
  }

  @Test
  void naoDeveRestaurarQuemNaoEstaExcluido() {
    encontra(existente(StatusLancamento.ATIVO, 1L));

    assertThatThrownBy(() -> service.restaurar(USUARIO, 10L))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Só é possível restaurar um lançamento excluído")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.LANCAMENTO_NAO_EXCLUIDO);
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
        .hasMessage("Categoria não encontrada")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.CATEGORIA_NAO_ENCONTRADA);
  }

  @Test
  void deveListarComFiltroDeCategoriaDoUsuario() {
    when(categoriaFacade.buscarPorIds(USUARIO, List.of(1L)))
        .thenReturn(List.of(new CategoriaResumo(1L, "A", true)));
    findAllDevolve(List.of());

    assertThat(service.listar(USUARIO, new FiltroLancamento(null, null, null, null, null, 1L)))
        .isEmpty();
  }

  // ---------- listarPagina ----------

  private static Lancamento comId(Long id, Long... categorias) {
    Lancamento l = existente(StatusLancamento.ATIVO, categorias);
    l.setId(id);
    return l;
  }

  @Test
  @SuppressWarnings("unchecked")
  void listarPaginaConsultaPaginadoNoBancoComOrdenacaoDaListagem() {
    Lancamento l1 = comId(21L, 1L);
    Lancamento l2 = comId(20L, 1L);
    when(repository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(l1, l2), PageRequest.of(1, 2), 7));
    // O carregamento das categorias pode voltar em outra ordem: vale a ordem da página
    when(repository.findByIdIn(List.of(21L, 20L))).thenReturn(List.of(l2, l1));
    categoriasExistem(new CategoriaResumo(1L, "A", true));

    Page<LancamentoResumo> pagina = service.listarPagina(USUARIO, null, 1, 2);

    ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAll(any(Specification.class), pedido.capture());
    assertThat(pedido.getValue().getPageNumber()).isEqualTo(1);
    assertThat(pedido.getValue().getPageSize()).isEqualTo(2);
    assertThat(pedido.getValue().getSort()).isEqualTo(LancamentoRepository.ORDENACAO);
    assertThat(pagina.getContent()).extracting(LancamentoResumo::id).containsExactly(21L, 20L);
    assertThat(pagina.getTotalElements()).isEqualTo(7);
    assertThat(pagina.getTotalPages()).isEqualTo(4);
    assertThat(pagina.getNumber()).isEqualTo(1);
    verify(repository, never()).findAll(any(Specification.class), any(Sort.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void listarPaginaVaziaNaoCarregaCategoriasEMantemTotal() {
    when(repository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(5, 12), 30));

    Page<LancamentoResumo> pagina = service.listarPagina(USUARIO, FiltroLancamento.vazio(), 5, 12);

    assertThat(pagina.getContent()).isEmpty();
    assertThat(pagina.getTotalElements()).isEqualTo(30);
    assertThat(pagina.getTotalPages()).isEqualTo(3);
    verify(repository, never()).findByIdIn(any());
    verifyNoInteractions(categoriaFacade);
  }

  @Test
  void listarPaginaDevolve404QuandoFiltroDeCategoriaNaoEDoUsuario() {
    when(categoriaFacade.buscarPorIds(USUARIO, List.of(77L))).thenReturn(List.of());
    FiltroLancamento filtro = new FiltroLancamento(null, null, null, null, null, 77L);

    assertThatThrownBy(() -> service.listarPagina(USUARIO, filtro, 0, 12))
        .isInstanceOf(RecursoNaoEncontradoException.class)
        .hasMessage("Categoria não encontrada")
        .hasFieldOrPropertyWithValue("codigo", CodigoErro.CATEGORIA_NAO_ENCONTRADA);
    verifyNoInteractions(repository);
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
