package br.com.poupamais.lancamento.service.impl;

import br.com.poupamais.categoria.publico.CategoriaFacade;
import br.com.poupamais.lancamento.model.entidades.Lancamento;
import br.com.poupamais.lancamento.model.repositories.LancamentoRepository;
import br.com.poupamais.lancamento.model.specifications.LancamentoSpecifications;
import br.com.poupamais.lancamento.publico.LancamentoFacade;
import br.com.poupamais.lancamento.publico.dto.FiltroLancamento;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.service.LancamentoResumoConversor;
import br.com.poupamais.lancamento.service.LancamentoService;
import br.com.poupamais.lancamento.service.LancamentoValidator;
import br.com.poupamais.shared.domain.Periodo;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LancamentoServiceImpl implements LancamentoService, LancamentoFacade {

  static final String MSG_NAO_ENCONTRADO = "Lançamento não encontrado";
  static final String MSG_CATEGORIA_NAO_ENCONTRADA = "Categoria não encontrada";
  static final String MSG_DELETADO_NAO_EDITA =
      "Lançamento excluído não pode ser editado. Restaure-o antes.";
  static final String MSG_STATUS_INVALIDO = "Informe o status ATIVO ou INATIVO";
  static final String MSG_JA_EXCLUIDO = "O lançamento já está excluído";
  static final String MSG_NAO_EXCLUIDO = "Só é possível restaurar um lançamento excluído";

  private final LancamentoRepository repository;
  private final LancamentoValidator validator;
  private final CategoriaFacade categoriaFacade;
  private final LancamentoResumoConversor conversor;

  // ---------- API ----------

  @Override
  @Transactional(readOnly = true)
  public List<LancamentoResumo> listar(Long idUsuario, FiltroLancamento filtro) {
    return buscarTodos(especificacaoDoFiltro(idUsuario, filtro));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<LancamentoResumo> listarPagina(
      Long idUsuario, FiltroLancamento filtro, int pagina, int tamanho) {
    Page<Lancamento> encontrados =
        repository.findAll(
            especificacaoDoFiltro(idUsuario, filtro),
            PageRequest.of(pagina, tamanho, LancamentoRepository.ORDENACAO));
    return new PageImpl<>(
        conversor.converter(comCategorias(encontrados.getContent())),
        encontrados.getPageable(),
        encontrados.getTotalElements());
  }

  @Override
  @Transactional(readOnly = true)
  public LancamentoResumo buscar(Long idUsuario, Long id) {
    return conversor.converter(buscarDoUsuario(idUsuario, id));
  }

  @Override
  @Transactional
  public LancamentoResumo criar(Long idUsuario, NovoLancamento novo) {
    return criar(idUsuario, novo, null);
  }

  @Override
  @Transactional
  public LancamentoResumo criar(Long idUsuario, NovoLancamento novo, StatusLancamento status) {
    validarStatusDoCorpo(status);
    NovoLancamento dados = validarNovo(idUsuario, novo);
    Lancamento lancamento =
        Lancamento.builder()
            .idUsuario(idUsuario)
            .status(status == null ? StatusLancamento.ATIVO : status)
            .build();
    preencher(lancamento, dados);
    return conversor.converter(repository.saveAndFlush(lancamento));
  }

  @Override
  @Transactional
  public LancamentoResumo editar(
      Long idUsuario, Long id, NovoLancamento novo, StatusLancamento status) {
    validarStatusDoCorpo(status);
    Lancamento lancamento = buscarDoUsuario(idUsuario, id);
    if (lancamento.getStatus() == StatusLancamento.DELETADO) {
      throw new RegraNegocioException(MSG_DELETADO_NAO_EDITA);
    }
    NovoLancamento dados = validator.validar(novo);
    // Categoria que já estava no lançamento pode continuar mesmo inativa; só as novas precisam
    // estar ativas (RN002: inativa não entra em lançamento)
    List<Long> novas =
        dados.categoriaIds().stream().filter(c -> !lancamento.idsCategorias().contains(c)).toList();
    categoriaFacade.validarAtivasDoUsuario(idUsuario, novas);
    preencher(lancamento, dados);
    if (status != null && status != lancamento.getStatus()) {
      lancamento.alterarStatus(status);
    }
    lancamento.marcarAtualizacao();
    return conversor.converter(repository.saveAndFlush(lancamento));
  }

  @Override
  @Transactional
  public LancamentoResumo alterarStatus(Long idUsuario, Long id, StatusLancamento status) {
    if (status == null) {
      throw new RegraNegocioException(MSG_STATUS_INVALIDO);
    }
    validarStatusDoCorpo(status);
    Lancamento lancamento = buscarDoUsuario(idUsuario, id);
    // Pedir o status que já está é aceito sem mudar nada (idempotente)
    if (lancamento.getStatus() != status) {
      lancamento.alterarStatus(status);
      repository.save(lancamento);
    }
    return conversor.converter(lancamento);
  }

  @Override
  @Transactional
  public void excluir(Long idUsuario, Long id) {
    Lancamento lancamento = buscarDoUsuario(idUsuario, id);
    if (lancamento.getStatus() == StatusLancamento.DELETADO) {
      throw new RegraNegocioException(MSG_JA_EXCLUIDO);
    }
    lancamento.alterarStatus(StatusLancamento.DELETADO);
    repository.save(lancamento);
  }

  @Override
  @Transactional
  public LancamentoResumo restaurar(Long idUsuario, Long id) {
    Lancamento lancamento = buscarDoUsuario(idUsuario, id);
    if (lancamento.getStatus() != StatusLancamento.DELETADO) {
      throw new RegraNegocioException(MSG_NAO_EXCLUIDO);
    }
    lancamento.restaurar();
    return conversor.converter(repository.save(lancamento));
  }

  // ---------- Facade (criar e listar acima atendem as duas interfaces) ----------

  @Override
  @Transactional(readOnly = true)
  public List<LancamentoResumo> listarAtivos(Long idUsuario, Collection<Periodo> periodos) {
    if (periodos == null || periodos.isEmpty()) {
      return List.of();
    }
    return buscarTodos(
        Specification.allOf(
            LancamentoSpecifications.doUsuario(idUsuario),
            LancamentoSpecifications.comStatus(StatusLancamento.ATIVO),
            LancamentoSpecifications.dosPeriodos(periodos)));
  }

  @Override
  @Transactional(readOnly = true)
  public List<LancamentoResumo> listarTodos(Long idUsuario) {
    return buscarTodos(LancamentoSpecifications.doUsuario(idUsuario));
  }

  // ---------- apoio ----------

  /** Filtros da listagem (nulo = sem filtro); categoria de outro usuário → 404. */
  private Specification<Lancamento> especificacaoDoFiltro(Long idUsuario, FiltroLancamento filtro) {
    FiltroLancamento f = filtro == null ? FiltroLancamento.vazio() : filtro;
    if (f.categoriaId() != null
        && categoriaFacade.buscarPorIds(idUsuario, List.of(f.categoriaId())).isEmpty()) {
      throw new RecursoNaoEncontradoException(MSG_CATEGORIA_NAO_ENCONTRADA);
    }
    return LancamentoSpecifications.doFiltro(idUsuario, f);
  }

  /** Os mesmos lançamentos, na mesma ordem, já com as categorias (uma consulta, sem N+1). */
  private List<Lancamento> comCategorias(List<Lancamento> lancamentos) {
    if (lancamentos.isEmpty()) {
      return List.of();
    }
    Map<Long, Lancamento> porId =
        repository.findByIdIn(lancamentos.stream().map(Lancamento::getId).toList()).stream()
            .collect(Collectors.toMap(Lancamento::getId, Function.identity()));
    return lancamentos.stream().map(l -> porId.getOrDefault(l.getId(), l)).toList();
  }

  private List<LancamentoResumo> buscarTodos(Specification<Lancamento> spec) {
    return conversor.converter(repository.findAll(spec, LancamentoRepository.ORDENACAO));
  }

  private Lancamento buscarDoUsuario(Long idUsuario, Long id) {
    return repository
        .findByIdAndIdUsuario(id, idUsuario)
        .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_NAO_ENCONTRADO));
  }

  /**
   * Pelo corpo só vão ATIVO ou INATIVO; a lixeira é pelo DELETE (na API o DTO já barra com 400).
   */
  private static void validarStatusDoCorpo(StatusLancamento status) {
    if (status == StatusLancamento.DELETADO) {
      throw new RegraNegocioException(MSG_STATUS_INVALIDO);
    }
  }

  /** RN001 + categorias existentes, do usuário e ativas (404/422 vêm da CategoriaFacade). */
  private NovoLancamento validarNovo(Long idUsuario, NovoLancamento novo) {
    NovoLancamento dados = validator.validar(novo);
    categoriaFacade.validarAtivasDoUsuario(idUsuario, dados.categoriaIds());
    return dados;
  }

  private static void preencher(Lancamento lancamento, NovoLancamento dados) {
    lancamento.setDescricao(dados.descricao());
    lancamento.setTipo(dados.tipo());
    lancamento.setData(dados.data());
    lancamento.setValor(dados.valor());
    lancamento.definirCategorias(dados.categoriaIds());
  }
}
