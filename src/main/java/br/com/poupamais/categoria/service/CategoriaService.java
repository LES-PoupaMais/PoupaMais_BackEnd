package br.com.poupamais.categoria.service;

import br.com.poupamais.categoria.model.Categoria;
import java.util.List;

/** Regras de categoria usadas pela API (RF011, RN002, RN005). */
public interface CategoriaService {

  List<Categoria> listar(Long idUsuario, Boolean ativa);

  Categoria criar(Long idUsuario, String descricao);

  Categoria editar(Long idUsuario, Long id, String descricao);

  Categoria alterarStatus(Long idUsuario, Long id, boolean ativa);

  void excluir(Long idUsuario, Long id);
}
