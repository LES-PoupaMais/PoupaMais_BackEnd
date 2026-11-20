package br.com.poupamais.shared.exception;

import java.util.Objects;

/** Duplicidade, como e-mail já cadastrado ou categoria com a mesma descrição (HTTP 409). */
public class ConflitoException extends RuntimeException {

  private final CodigoErro codigo;

  public ConflitoException(CodigoErro codigo, String mensagem) {
    super(mensagem);
    this.codigo = Objects.requireNonNull(codigo, "codigo");
  }

  /** Código estável que vai no campo {@code codigo} da resposta de erro. */
  public CodigoErro codigo() {
    return codigo;
  }
}
