package br.com.poupamais.lancamento.service;

import br.com.poupamais.lancamento.publico.dto.FiltroLancamento;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import java.util.List;
import org.springframework.data.domain.Page;

/** Regras de lançamento usadas pela API (RF001–RF005, RF008, RN001, RN004–RN006). */
public interface LancamentoService {

  List<LancamentoResumo> listar(Long idUsuario, FiltroLancamento filtro);

  /**
   * Uma página da listagem, com os mesmos filtros e ordenação (data e id decrescentes) de {@link
   * #listar}. {@code pagina} começa em 0; os limites de {@code tamanho} são validados no resource.
   */
  Page<LancamentoResumo> listarPagina(
      Long idUsuario, FiltroLancamento filtro, int pagina, int tamanho);

  LancamentoResumo buscar(Long idUsuario, Long id);

  /**
   * Cadastra já com o status pedido (ATIVO/INATIVO), numa chamada só; {@code null} = ATIVO.
   * DELETADO → 422.
   */
  LancamentoResumo criar(Long idUsuario, NovoLancamento novo, StatusLancamento status);

  /**
   * Edita os dados e, se {@code status} vier (ATIVO/INATIVO), troca o status junto; {@code null}
   * mantém o atual. Lançamento na lixeira → 422. DELETADO → 422.
   */
  LancamentoResumo editar(Long idUsuario, Long id, NovoLancamento dados, StatusLancamento status);

  /** Alterna entre ATIVO e INATIVO. Para excluir/restaurar use os métodos próprios. */
  LancamentoResumo alterarStatus(Long idUsuario, Long id, StatusLancamento status);

  /** Exclusão lógica: status DELETADO (RN004). */
  void excluir(Long idUsuario, Long id);

  /** DELETADO → status que tinha antes da lixeira; sem registro, INATIVO (RN006). */
  LancamentoResumo restaurar(Long idUsuario, Long id);
}
