package br.com.poupamais.categoria.service.impl;

import br.com.poupamais.categoria.CategoriaFacade;
import br.com.poupamais.categoria.CategoriaResumo;
import br.com.poupamais.categoria.model.Categoria;
import br.com.poupamais.categoria.model.CategoriaRepository;
import br.com.poupamais.categoria.service.CategoriaService;
import br.com.poupamais.shared.exception.ConflitoException;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoriaServiceImpl implements CategoriaService, CategoriaFacade {

  static final int TAMANHO_MAXIMO = 255;
  static final List<String> PADROES =
      List.of(
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

  static final String MSG_NAO_ENCONTRADA = "Categoria não encontrada";
  static final String MSG_DUPLICADA = "Já existe uma categoria com essa descrição";
  static final String MSG_OBRIGATORIA = "A descrição da categoria é obrigatória";
  static final String MSG_LONGA = "A descrição da categoria deve ter no máximo 255 caracteres";
  static final String MSG_VINCULADA = "Categoria vinculada a lançamentos, apenas inative";
  static final String MSG_INATIVA = "A categoria \"%s\" está inativa e não pode ser usada";

  private final CategoriaRepository repository;

  // ---------- API ----------

  @Override
  @Transactional(readOnly = true)
  public List<Categoria> listar(Long idUsuario, Boolean ativa) {
    return ativa == null
        ? repository.listarDoUsuario(idUsuario)
        : repository.listarDoUsuarioPorStatus(idUsuario, ativa);
  }

  @Override
  @Transactional
  public Categoria criar(Long idUsuario, String descricao) {
    String normalizada = normalizar(descricao);
    if (repository.existsByIdUsuarioAndDescricaoIgnoreCase(idUsuario, normalizada)) {
      throw new ConflitoException(MSG_DUPLICADA);
    }
    return salvar(Categoria.builder().idUsuario(idUsuario).descricao(normalizada).build());
  }

  @Override
  @Transactional
  public Categoria editar(Long idUsuario, Long id, String descricao) {
    Categoria categoria = buscarDoUsuario(idUsuario, id);
    String normalizada = normalizar(descricao);
    if (repository.existsByIdUsuarioAndDescricaoIgnoreCaseAndIdNot(idUsuario, normalizada, id)) {
      throw new ConflitoException(MSG_DUPLICADA);
    }
    categoria.setDescricao(normalizada);
    return salvar(categoria);
  }

  @Override
  @Transactional
  public Categoria alterarStatus(Long idUsuario, Long id, boolean ativa) {
    Categoria categoria = buscarDoUsuario(idUsuario, id);
    categoria.setAtiva(ativa);
    return repository.save(categoria);
  }

  @Override
  @Transactional
  public void excluir(Long idUsuario, Long id) {
    Categoria categoria = buscarDoUsuario(idUsuario, id);
    // A FK da associativa é DEFERRABLE: só falharia no commit, por isso a checagem antes (RN002)
    if (repository.possuiLancamentos(id, idUsuario)) {
      throw new RegraNegocioException(MSG_VINCULADA);
    }
    repository.delete(categoria);
  }

  // ---------- Facade ----------

  @Override
  @Transactional(readOnly = true)
  public List<CategoriaResumo> buscarPorIds(Long idUsuario, Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return repository.findByIdUsuarioAndIdIn(idUsuario, new HashSet<>(ids)).stream()
        .map(CategoriaServiceImpl::resumo)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public void validarAtivasDoUsuario(Long idUsuario, Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return;
    }
    if (ids.stream().anyMatch(Objects::isNull)) {
      throw new RecursoNaoEncontradoException(MSG_NAO_ENCONTRADA);
    }
    Set<Long> unicos = new HashSet<>(ids);
    Map<Long, Categoria> encontradas =
        repository.findByIdUsuarioAndIdIn(idUsuario, unicos).stream()
            .collect(Collectors.toMap(Categoria::getId, Function.identity()));
    if (encontradas.size() != unicos.size()) {
      throw new RecursoNaoEncontradoException(MSG_NAO_ENCONTRADA);
    }
    encontradas.values().stream()
        .filter(c -> !c.isAtiva())
        .findFirst()
        .ifPresent(c -> {
          throw new RegraNegocioException(MSG_INATIVA.formatted(c.getDescricao()));
        });
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CategoriaResumo> buscarPorDescricao(Long idUsuario, String descricao) {
    if (descricao == null || descricao.isBlank()) {
      return Optional.empty();
    }
    return repository
        .findByIdUsuarioAndDescricaoIgnoreCase(idUsuario, descricao.strip())
        .map(CategoriaServiceImpl::resumo);
  }

  @Override
  @Transactional
  public CategoriaResumo buscarOuCriar(Long idUsuario, String descricao) {
    String normalizada = normalizar(descricao);
    return repository
        .findByIdUsuarioAndDescricaoIgnoreCase(idUsuario, normalizada)
        .map(CategoriaServiceImpl::resumo)
        .orElseGet(() ->
            resumo(
                salvar(Categoria.builder().idUsuario(idUsuario).descricao(normalizada).build())));
  }

  @Override
  @Transactional
  public void criarPadroes(Long idUsuario) {
    repository.saveAll(
        PADROES.stream()
            .map(d -> Categoria.builder().idUsuario(idUsuario).descricao(d).build())
            .toList());
  }

  // ---------- apoio ----------

  private Categoria buscarDoUsuario(Long idUsuario, Long id) {
    return repository
        .findByIdAndIdUsuario(id, idUsuario)
        .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_NAO_ENCONTRADA));
  }

  /** Corrida entre a checagem e o insert: o índice único do banco vira 409. */
  private Categoria salvar(Categoria categoria) {
    try {
      return repository.saveAndFlush(categoria);
    } catch (DataIntegrityViolationException e) {
      throw new ConflitoException(MSG_DUPLICADA);
    }
  }

  static String normalizar(String descricao) {
    String normalizada = descricao == null ? "" : descricao.strip();
    if (normalizada.isEmpty()) {
      throw new RegraNegocioException(MSG_OBRIGATORIA);
    }
    if (normalizada.length() > TAMANHO_MAXIMO) {
      throw new RegraNegocioException(MSG_LONGA);
    }
    return normalizada;
  }

  private static CategoriaResumo resumo(Categoria c) {
    return new CategoriaResumo(c.getId(), c.getDescricao(), c.isAtiva());
  }
}
