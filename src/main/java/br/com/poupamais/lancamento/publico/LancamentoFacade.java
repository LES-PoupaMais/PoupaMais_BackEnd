package br.com.poupamais.lancamento.publico;

import br.com.poupamais.lancamento.publico.dto.FiltroLancamento;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import br.com.poupamais.shared.domain.Periodo;
import java.util.Collection;
import java.util.List;

/**
 * API pública do módulo de lançamentos, usada por {@code dashboard}, {@code arquivo} e {@code
 * usuario}. Todo método recebe o id do usuário dono, já obtido de {@code UsuarioLogado.id()}.
 */
public interface LancamentoFacade {

  /**
   * Cria um lançamento com a mesma validação do cadastro manual (RN001 + categorias ativas do
   * usuário). Cada chamada é uma transação própria (RN007).
   *
   * @throws br.com.poupamais.shared.exception.RegraNegocioException (422) dado inválido ou
   *     categoria inativa; a mensagem serve de "motivo" na importação
   * @throws br.com.poupamais.shared.exception.RecursoNaoEncontradoException (404) categoria
   *     inexistente ou de outro usuário
   */
  LancamentoResumo criar(Long idUsuario, NovoLancamento novo);

  /**
   * Mesmos filtros e ordenação (ano, mês e id decrescentes) da listagem da API (RN013).
   *
   * @throws br.com.poupamais.shared.exception.RecursoNaoEncontradoException (404) se {@code
   *     categoriaId} não for do usuário
   */
  List<LancamentoResumo> listar(Long idUsuario, FiltroLancamento filtro);

  /** Só lançamentos ATIVOS dos meses pedidos (dashboard, RN003/RN008), na ordem da listagem. */
  List<LancamentoResumo> listarAtivos(Long idUsuario, Collection<Periodo> periodos);

  /** Todos os lançamentos do usuário, de qualquer status (exportação de dados LGPD, RF016). */
  List<LancamentoResumo> listarTodos(Long idUsuario);
}
