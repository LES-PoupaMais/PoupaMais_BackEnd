package br.com.poupamais.usuario.service;

/** Recuperação e redefinição de senha por e-mail (RF012, RN014). */
public interface RecuperacaoSenhaService {

  /** Nunca lança por e-mail inexistente nem por falha de envio: a resposta é sempre a mesma. */
  void solicitar(String email);

  void redefinir(String token, String novaSenha);
}
