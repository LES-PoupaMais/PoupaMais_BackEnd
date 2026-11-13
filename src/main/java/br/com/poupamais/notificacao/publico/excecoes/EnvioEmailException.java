package br.com.poupamais.notificacao.publico.excecoes;

/**
 * Falha ao enviar um e-mail. A mensagem é genérica e não contém destinatário, link nem token
 * (RNF019).
 */
public class EnvioEmailException extends RuntimeException {

  public EnvioEmailException(String mensagem, Throwable causa) {
    super(mensagem, causa);
  }
}
