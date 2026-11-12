package br.com.poupamais.usuario.service;

import br.com.poupamais.usuario.endpoints.dto.DadosTitularResponse;

/** LGPD: exportação dos dados do titular (RF016, RN016). */
public interface ExportacaoDadosService {

  /**
   * Perfil, todas as categorias (ativas e inativas) e todos os lançamentos (ATIVO, INATIVO e
   * DELETADO) do usuário.
   */
  DadosTitularResponse exportar(Long idUsuario);
}
