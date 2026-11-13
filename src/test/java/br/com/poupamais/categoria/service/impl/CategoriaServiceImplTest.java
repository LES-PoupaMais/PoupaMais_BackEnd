package br.com.poupamais.categoria.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.poupamais.categoria.model.entidades.Categoria;
import br.com.poupamais.categoria.model.repositories.CategoriaRepository;
import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.shared.exception.ConflitoException;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceImplTest {

  private static final Long USUARIO = 1L;

  @Mock private CategoriaRepository repository;
  @InjectMocks private CategoriaServiceImpl service;

  private static Categoria categoria(Long id, String descricao, boolean ativa) {
    return Categoria.builder().id(id).idUsuario(USUARIO).descricao(descricao).ativa(ativa).build();
  }

  private void salvarDevolveOMesmo() {
    when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void deveCriarCategoriaComDescricaoSemEspacos() {
    salvarDevolveOMesmo();

    Categoria criada = service.criar(USUARIO, "  Pets  ");

    assertThat(criada.getDescricao()).isEqualTo("Pets");
    assertThat(criada.getIdUsuario()).isEqualTo(USUARIO);
    assertThat(criada.isAtiva()).isTrue();
  }

  @Test
  void deveRecusarCategoriaDuplicadaSemDiferenciarMaiusculas() {
    when(repository.existsByIdUsuarioAndDescricaoIgnoreCase(USUARIO, "LAZER")).thenReturn(true);

    assertThatThrownBy(() -> service.criar(USUARIO, "LAZER"))
        .isInstanceOf(ConflitoException.class)
        .hasMessage("Já existe uma categoria com essa descrição");
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void deveTransformarViolacaoDoIndiceUnicoEm409() {
    when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk"));

    assertThatThrownBy(() -> service.criar(USUARIO, "Lazer")).isInstanceOf(ConflitoException.class);
  }

  @Test
  void deveRecusarDescricaoVazia() {
    assertThatThrownBy(() -> service.criar(USUARIO, "   "))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("A descrição da categoria é obrigatória");
    assertThatThrownBy(() -> service.criar(USUARIO, null))
        .isInstanceOf(RegraNegocioException.class);
  }

  @Test
  void deveRecusarDescricaoMaiorQue255() {
    assertThatThrownBy(() -> service.criar(USUARIO, "a".repeat(256)))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("A descrição da categoria deve ter no máximo 255 caracteres");
  }

  @Test
  void deveRecusarEditarParaDescricaoDeOutraCategoria() {
    when(repository.findByIdAndIdUsuario(5L, USUARIO))
        .thenReturn(Optional.of(categoria(5L, "Pets", true)));
    when(repository.existsByIdUsuarioAndDescricaoIgnoreCaseAndIdNot(USUARIO, "lazer", 5L))
        .thenReturn(true);

    assertThatThrownBy(() -> service.editar(USUARIO, 5L, "lazer"))
        .isInstanceOf(ConflitoException.class);
  }

  @Test
  void devePermitirEditarAMesmaCategoriaMudandoMaiusculas() {
    when(repository.findByIdAndIdUsuario(5L, USUARIO))
        .thenReturn(Optional.of(categoria(5L, "pets", true)));
    salvarDevolveOMesmo();

    Categoria editada = service.editar(USUARIO, 5L, "Pets");

    assertThat(editada.getDescricao()).isEqualTo("Pets");
  }

  @Test
  void deveDevolver404AoEditarCategoriaDeOutroUsuario() {
    when(repository.findByIdAndIdUsuario(9L, USUARIO)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.editar(USUARIO, 9L, "X"))
        .isInstanceOf(RecursoNaoEncontradoException.class)
        .hasMessage("Categoria não encontrada");
  }

  @Test
  void deveInativarEReativar() {
    Categoria c = categoria(5L, "Pets", true);
    when(repository.findByIdAndIdUsuario(5L, USUARIO)).thenReturn(Optional.of(c));
    when(repository.save(c)).thenReturn(c);

    assertThat(service.alterarStatus(USUARIO, 5L, false).isAtiva()).isFalse();
    assertThat(service.alterarStatus(USUARIO, 5L, true).isAtiva()).isTrue();
  }

  @Test
  void deveDevolver404AoAlterarStatusDeOutroUsuario() {
    when(repository.findByIdAndIdUsuario(9L, USUARIO)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.alterarStatus(USUARIO, 9L, false))
        .isInstanceOf(RecursoNaoEncontradoException.class);
  }

  @Test
  void deveExcluirCategoriaSemLancamentos() {
    Categoria c = categoria(5L, "Pets", true);
    when(repository.findByIdAndIdUsuario(5L, USUARIO)).thenReturn(Optional.of(c));
    when(repository.possuiLancamentos(5L, USUARIO)).thenReturn(false);

    service.excluir(USUARIO, 5L);

    verify(repository).delete(c);
  }

  @Test
  void deveRecusarExcluirCategoriaVinculada() {
    when(repository.findByIdAndIdUsuario(5L, USUARIO))
        .thenReturn(Optional.of(categoria(5L, "Pets", true)));
    when(repository.possuiLancamentos(5L, USUARIO)).thenReturn(true);

    assertThatThrownBy(() -> service.excluir(USUARIO, 5L))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Categoria vinculada a lançamentos, apenas inative");
    verify(repository, never()).delete(any());
  }

  @Test
  void deveDevolver404AoExcluirCategoriaDeOutroUsuario() {
    when(repository.findByIdAndIdUsuario(9L, USUARIO)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.excluir(USUARIO, 9L))
        .isInstanceOf(RecursoNaoEncontradoException.class);
  }

  @Test
  void deveListarTodasOuPorStatus() {
    List<Categoria> todas = List.of(categoria(1L, "A", true));
    when(repository.listarDoUsuario(USUARIO)).thenReturn(todas);
    when(repository.listarDoUsuarioPorStatus(USUARIO, false)).thenReturn(List.of());

    assertThat(service.listar(USUARIO, null)).isEqualTo(todas);
    assertThat(service.listar(USUARIO, false)).isEmpty();
  }

  @Test
  void deveBuscarPorIdsNumaConsulta() {
    when(repository.findByIdUsuarioAndIdIn(eq(USUARIO), any()))
        .thenReturn(List.of(categoria(1L, "A", true), categoria(2L, "B", false)));

    List<CategoriaResumo> resumos = service.buscarPorIds(USUARIO, List.of(1L, 2L));

    assertThat(resumos)
        .containsExactly(new CategoriaResumo(1L, "A", true), new CategoriaResumo(2L, "B", false));
    assertThat(service.buscarPorIds(USUARIO, List.of())).isEmpty();
    assertThat(service.buscarPorIds(USUARIO, null)).isEmpty();
  }

  @Test
  void deveAceitarCategoriasAtivasDoUsuario() {
    when(repository.findByIdUsuarioAndIdIn(eq(USUARIO), any()))
        .thenReturn(List.of(categoria(1L, "A", true), categoria(2L, "B", true)));

    service.validarAtivasDoUsuario(USUARIO, List.of(1L, 2L, 2L));
    service.validarAtivasDoUsuario(USUARIO, List.of());
    service.validarAtivasDoUsuario(USUARIO, null);
  }

  @Test
  void deveRecusarCategoriaInativaNoLancamento() {
    when(repository.findByIdUsuarioAndIdIn(eq(USUARIO), any()))
        .thenReturn(List.of(categoria(1L, "A", true), categoria(2L, "Lazer", false)));

    assertThatThrownBy(() -> service.validarAtivasDoUsuario(USUARIO, List.of(1L, 2L)))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("A categoria \"Lazer\" está inativa e não pode ser usada");
  }

  @Test
  void deveDevolver404ParaCategoriaInexistenteOuDeOutroUsuario() {
    when(repository.findByIdUsuarioAndIdIn(eq(USUARIO), any()))
        .thenReturn(List.of(categoria(1L, "A", true)));

    assertThatThrownBy(() -> service.validarAtivasDoUsuario(USUARIO, List.of(1L, 99L)))
        .isInstanceOf(RecursoNaoEncontradoException.class);
  }

  @Test
  void deveDevolver404ParaIdNulo() {
    assertThatThrownBy(
            () -> service.validarAtivasDoUsuario(USUARIO, new ArrayList<>(Arrays.asList(1L, null))))
        .isInstanceOf(RecursoNaoEncontradoException.class);
  }

  @Test
  void buscarOuCriarDeveReaproveitarExistenteSemDiferenciarMaiusculas() {
    when(repository.findByIdUsuarioAndDescricaoIgnoreCase(USUARIO, "lazer"))
        .thenReturn(Optional.of(categoria(3L, "Lazer", true)));

    CategoriaResumo resumo = service.buscarOuCriar(USUARIO, " lazer ");

    assertThat(resumo).isEqualTo(new CategoriaResumo(3L, "Lazer", true));
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void buscarPorDescricaoDeveAcharSemDiferenciarMaiusculasENaoCriar() {
    when(repository.findByIdUsuarioAndDescricaoIgnoreCase(USUARIO, "lazer"))
        .thenReturn(Optional.of(categoria(3L, "Lazer", false)));

    assertThat(service.buscarPorDescricao(USUARIO, " lazer "))
        .contains(new CategoriaResumo(3L, "Lazer", false));
    assertThat(service.buscarPorDescricao(USUARIO, "  ")).isEmpty();
    assertThat(service.buscarPorDescricao(USUARIO, null)).isEmpty();
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void buscarOuCriarDeveCriarQuandoNaoExiste() {
    when(repository.findByIdUsuarioAndDescricaoIgnoreCase(eq(USUARIO), anyString()))
        .thenReturn(Optional.empty());
    salvarDevolveOMesmo();

    CategoriaResumo resumo = service.buscarOuCriar(USUARIO, "Pets");

    assertThat(resumo.descricao()).isEqualTo("Pets");
    assertThat(resumo.ativa()).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void deveCriarAsOnzeCategoriasPadrao() {
    ArgumentCaptor<List<Categoria>> captor = ArgumentCaptor.forClass(List.class);

    service.criarPadroes(USUARIO);

    verify(repository).saveAll(captor.capture());
    assertThat(captor.getValue())
        .hasSize(11)
        .allMatch(c -> c.getIdUsuario().equals(USUARIO) && c.isAtiva())
        .extracting(Categoria::getDescricao)
        .containsExactly(
            "Alimentação",
            "Educação",
            "Freelance",
            "Investimentos",
            "Lazer",
            "Moradia",
            "Outros",
            "Salário",
            "Saúde",
            "Transporte",
            "Vestuário");
  }

  @Test
  void listarTodasDeveDevolverAtivasEInativasDoUsuarioComoResumo() {
    when(repository.listarDoUsuario(USUARIO))
        .thenReturn(List.of(categoria(1L, "Lazer", true), categoria(2L, "Antiga", false)));

    List<CategoriaResumo> todas = service.listarTodas(USUARIO);

    assertThat(todas)
        .containsExactly(
            new CategoriaResumo(1L, "Lazer", true), new CategoriaResumo(2L, "Antiga", false));
  }
}
