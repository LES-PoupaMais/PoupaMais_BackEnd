package br.com.poupamais.shared.exception;

/** Duplicidade, como e-mail já cadastrado ou categoria com a mesma descrição (HTTP 409). */
public class ConflitoException extends RuntimeException {

  public ConflitoException(String mensagem) {
    super(mensagem);
  }
}
