package br.com.poupamais.shared.domain;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Competência de um lançamento (mês/ano). Concentra a aritmética de meses usada pelo dashboard
 * (RN008: os 4 meses que terminam no selecionado, atravessando a virada do ano).
 */
public record Periodo(int mes, int ano) implements Comparable<Periodo> {

  public static final int ANO_MINIMO = 2000;
  public static final int ANO_MAXIMO = 2100;
  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

  public Periodo {
    if (mes < 1 || mes > 12) {
      throw new IllegalArgumentException("O mês deve estar entre 1 e 12.");
    }
    if (ano < ANO_MINIMO || ano > ANO_MAXIMO) {
      throw new IllegalArgumentException(
          "O ano deve estar entre " + ANO_MINIMO + " e " + ANO_MAXIMO + ".");
    }
  }

  /** Mês atual no fuso de Brasília (seleção padrão do dashboard, RN003). */
  public static Periodo atual() {
    return de(YearMonth.now(FUSO));
  }

  public static Periodo de(YearMonth anoMes) {
    return new Periodo(anoMes.getMonthValue(), anoMes.getYear());
  }

  public Periodo anterior() {
    return de(paraYearMonth().minusMonths(1));
  }

  /** Os {@code quantidade} meses que terminam neste, do mais antigo para este. */
  public List<Periodo> ultimos(int quantidade) {
    if (quantidade < 1) {
      throw new IllegalArgumentException("A quantidade de meses deve ser pelo menos 1.");
    }
    List<Periodo> periodos = new ArrayList<>(quantidade);
    Periodo atual = this;
    periodos.add(atual);
    for (int i = 1; i < quantidade; i++) {
      atual = atual.anterior();
      periodos.add(atual);
    }
    Collections.reverse(periodos);
    return List.copyOf(periodos);
  }

  /** Nome do mês em português com inicial maiúscula, ex.: "Agosto". */
  public String nomeMes() {
    String nome = paraYearMonth().getMonth().getDisplayName(TextStyle.FULL, PT_BR);
    return nome.substring(0, 1).toUpperCase(PT_BR) + nome.substring(1);
  }

  public YearMonth paraYearMonth() {
    return YearMonth.of(ano, mes);
  }

  @Override
  public int compareTo(Periodo outro) {
    return paraYearMonth().compareTo(outro.paraYearMonth());
  }
}
