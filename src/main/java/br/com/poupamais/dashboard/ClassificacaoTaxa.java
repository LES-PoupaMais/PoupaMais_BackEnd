package br.com.poupamais.dashboard;

import java.math.BigDecimal;

/** Faixas da taxa de gasto (RN010): até 70%, até 100% e acima de 100%. */
public enum ClassificacaoTaxa {
  DENTRO_DO_LIMITE,
  ATENCAO,
  ACIMA_DO_LIMITE;

  private static final BigDecimal LIMITE_DENTRO = new BigDecimal("70");
  private static final BigDecimal LIMITE_ATENCAO = new BigDecimal("100");

  /** Classifica a taxa (em %); {@code null} quando a taxa não está disponível (receitas = 0). */
  public static ClassificacaoTaxa de(BigDecimal taxa) {
    if (taxa == null) {
      return null;
    }
    if (taxa.compareTo(LIMITE_DENTRO) <= 0) {
      return DENTRO_DO_LIMITE;
    }
    if (taxa.compareTo(LIMITE_ATENCAO) <= 0) {
      return ATENCAO;
    }
    return ACIMA_DO_LIMITE;
  }
}
