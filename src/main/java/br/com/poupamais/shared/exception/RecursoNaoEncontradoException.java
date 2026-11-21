package br.com.poupamais.shared.exception;

import java.util.Objects;

/**
 * Recurso inexistente ou de outro usuário (HTTP 404). Nunca revele que o recurso existe para outro
 * usuário (RN005).
 */
public class RecursoNaoEncontradoException extends RuntimeException {

  private final CodigoErro codigo;

  public RecursoNaoEncontradoException(CodigoErro codigo, String mensagem) {
    super(mensagem);
    this.codigo = Objects.requireNonNull(codigo, "codigo");
  }

  /** Código estável que vai no campo {@code codigo} da resposta de erro. */
  public CodigoErro codigo() {
    return codigo;
  }
}
