package br.com.poupamais.shared.exception;

/**
 * Violação de regra de negócio (HTTP 422). A mensagem é exibida ao usuário: escreva em português,
 * sem termos técnicos (RNF007).
 */
public class RegraNegocioException extends RuntimeException {

  public RegraNegocioException(String mensagem) {
    super(mensagem);
  }
}
