package br.com.poupamais.shared.exception;

import java.util.Objects;

/**
 * Falha de autenticação (HTTP 401): credenciais inválidas ou requisição sem usuário autenticado. A
 * mensagem é exibida ao usuário (RNF007).
 */
public class ErroAutenticacaoException extends RuntimeException {

  private final CodigoErro codigo;

  public ErroAutenticacaoException(CodigoErro codigo, String mensagem) {
    super(mensagem);
    this.codigo = Objects.requireNonNull(codigo, "codigo");
  }

  /** Código estável que vai no campo {@code codigo} da resposta de erro. */
  public CodigoErro codigo() {
    return codigo;
  }
}
