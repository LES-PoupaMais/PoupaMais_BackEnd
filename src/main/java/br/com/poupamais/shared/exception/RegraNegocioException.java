package br.com.poupamais.shared.exception;

import java.util.Objects;

/**
 * Violação de regra de negócio (HTTP 422). A mensagem é exibida ao usuário: escreva em português,
 * sem termos técnicos (RNF007).
 */
public class RegraNegocioException extends RuntimeException {

  private final CodigoErro codigo;

  public RegraNegocioException(CodigoErro codigo, String mensagem) {
    super(mensagem);
    this.codigo = Objects.requireNonNull(codigo, "codigo");
  }

  /** Código estável que vai no campo {@code codigo} da resposta de erro. */
  public CodigoErro codigo() {
    return codigo;
  }
}
