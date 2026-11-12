package br.com.poupamais.usuario.service;

/** LGPD: exclusão de conta (RF017, RN016). */
public interface PrivacidadeService {

  /** Exclusão física e irreversível; o banco apaga os dados do usuário em cascata. */
  void excluirConta(Long idUsuario, String senha);
}
