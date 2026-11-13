package br.com.poupamais.categoria.publico;

import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * API pública do módulo de categorias (RN002, RN005). Todo método recebe o id do usuário dono; quem
 * chama já o obteve de {@code UsuarioLogado.id()}.
 */
public interface CategoriaFacade {

  /**
   * Categorias do usuário com os ids informados, em uma única consulta. Ids inexistentes ou de
   * outro usuário são simplesmente ignorados.
   */
  List<CategoriaResumo> buscarPorIds(Long idUsuario, Collection<Long> ids);

  /**
   * Todas as categorias do usuário, ativas e inativas (somente leitura): ativas primeiro, depois em
   * ordem alfabética sem diferenciar maiúsculas. Usado pela exportação de dados do titular (RF016).
   */
  List<CategoriaResumo> listarTodas(Long idUsuario);

  /**
   * Garante que todas as categorias existem, são do usuário e estão ativas.
   *
   * @throws br.com.poupamais.shared.exception.RecursoNaoEncontradoException (404) se alguma não
   *     existir ou for de outro usuário (RN005: não revela que existe)
   * @throws br.com.poupamais.shared.exception.RegraNegocioException (422) se alguma estiver inativa
   */
  void validarAtivasDoUsuario(Long idUsuario, Collection<Long> ids);

  /**
   * Categoria do usuário com essa descrição (trim, sem diferenciar maiúsculas), sem criar nada.
   * Usado pela importação CSV para checar categoria inativa e só criar categoria nova em linha
   * válida (RN002). Descrição nula ou vazia devolve vazio.
   */
  Optional<CategoriaResumo> buscarPorDescricao(Long idUsuario, String descricao);

  /**
   * Devolve a categoria do usuário com essa descrição (trim, sem diferenciar maiúsculas) ou cria
   * uma nova ativa. Usado pela importação CSV (RN002). Não reativa categoria inativa encontrada.
   *
   * @throws br.com.poupamais.shared.exception.RegraNegocioException (422) se a descrição for vazia
   *     ou maior que 255 caracteres
   */
  CategoriaResumo buscarOuCriar(Long idUsuario, String descricao);

  /** Cria as 11 categorias padrão da RN002 para um usuário recém-cadastrado. */
  void criarPadroes(Long idUsuario);
}
