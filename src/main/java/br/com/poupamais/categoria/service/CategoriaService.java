package br.com.poupamais.categoria.service;

import br.com.poupamais.categoria.model.entidades.Categoria;
import br.com.poupamais.shared.domain.Periodo;
import java.util.List;

/** Regras de categoria usadas pela API (RF011, RN002, RN005). */
public interface CategoriaService {

  List<Categoria> listar(Long idUsuario, Boolean ativa);

  /**
   * Mesma lista (e ordem) de {@link #listar}, com a quantidade de lançamentos ATIVO/INATIVO de cada
   * categoria cuja data cai no período. Sem lançamento no período → 0.
   */
  List<CategoriaComQuantidade> listarComQuantidade(Long idUsuario, Boolean ativa, Periodo periodo);

  Categoria criar(Long idUsuario, String descricao);

  Categoria editar(Long idUsuario, Long id, String descricao);

  Categoria alterarStatus(Long idUsuario, Long id, boolean ativa);

  void excluir(Long idUsuario, Long id);
}
