package br.com.poupamais.shared.exception;

/**
 * Falha de autenticação (HTTP 401): credenciais inválidas ou requisição sem usuário autenticado. A
 * mensagem é exibida ao usuário (RNF007).
 */
public class ErroAutenticacaoException extends RuntimeException {

  public ErroAutenticacaoException(String mensagem) {
    super(mensagem);
  }
}
