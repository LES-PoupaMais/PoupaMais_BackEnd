package br.com.poupamais.shared.exception;

/**
 * Recurso inexistente ou de outro usuário (HTTP 404). Nunca revele que o recurso existe para outro
 * usuário (RN005).
 */
public class RecursoNaoEncontradoException extends RuntimeException {

  public RecursoNaoEncontradoException(String mensagem) {
    super(mensagem);
  }
}
