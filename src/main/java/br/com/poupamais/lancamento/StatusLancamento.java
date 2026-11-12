package br.com.poupamais.lancamento;

/**
 * Status de um lançamento (RN004) e suas transições válidas (RN006): ATIVO ⇄ INATIVO, ATIVO/INATIVO
 * → DELETADO e DELETADO → INATIVO (restaurar).
 */
public enum StatusLancamento {
  ATIVO,
  INATIVO,
  DELETADO;

  /** Se a transição deste status para {@code novo} é permitida. Mesmo status não é transição. */
  public boolean podeIrPara(StatusLancamento novo) {
    if (novo == null || novo == this) {
      return false;
    }
    return switch (this) {
      case ATIVO, INATIVO -> true;
      case DELETADO -> novo == INATIVO;
    };
  }
}
