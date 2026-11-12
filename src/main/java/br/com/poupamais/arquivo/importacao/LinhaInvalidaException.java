package br.com.poupamais.arquivo.importacao;

/** Linha recusada pela importação; a mensagem vira o "motivo" da falha (RN007). */
public class LinhaInvalidaException extends RuntimeException {

  public LinhaInvalidaException(String motivo) {
    super(motivo);
  }
}
