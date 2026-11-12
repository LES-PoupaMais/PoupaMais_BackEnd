package br.com.poupamais.lancamento.service;

import br.com.poupamais.lancamento.FiltroLancamento;
import br.com.poupamais.lancamento.LancamentoResumo;
import br.com.poupamais.lancamento.NovoLancamento;
import br.com.poupamais.lancamento.StatusLancamento;
import java.util.List;

/** Regras de lançamento usadas pela API (RF001–RF005, RF008, RN001, RN004–RN006). */
public interface LancamentoService {

  List<LancamentoResumo> listar(Long idUsuario, FiltroLancamento filtro);

  LancamentoResumo buscar(Long idUsuario, Long id);

  LancamentoResumo criar(Long idUsuario, NovoLancamento novo);

  LancamentoResumo editar(Long idUsuario, Long id, NovoLancamento dados);

  /** Alterna entre ATIVO e INATIVO. Para excluir/restaurar use os métodos próprios. */
  LancamentoResumo alterarStatus(Long idUsuario, Long id, StatusLancamento status);

  /** Exclusão lógica: status DELETADO (RN004). */
  void excluir(Long idUsuario, Long id);

  /** DELETADO → INATIVO (RN006). */
  LancamentoResumo restaurar(Long idUsuario, Long id);
}
