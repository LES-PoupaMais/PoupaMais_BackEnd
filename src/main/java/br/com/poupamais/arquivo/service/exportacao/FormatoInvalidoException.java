package br.com.poupamais.arquivo.service.exportacao;

/** Parâmetro {@code formato} da exportação ausente ou não suportado (HTTP 400). */
public class FormatoInvalidoException extends RuntimeException {

  public FormatoInvalidoException(String mensagem) {
    super(mensagem);
  }
}
