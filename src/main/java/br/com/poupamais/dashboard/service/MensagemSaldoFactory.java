package br.com.poupamais.dashboard.service;

import br.com.poupamais.shared.domain.Periodo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Mensagem descritiva do mês (RN010). Só descreve o saldo: nunca recomenda investimento (RNI003).
 */
@Component
public class MensagemSaldoFactory {

  private static final DecimalFormatSymbols PT_BR =
      DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR"));

  public String criar(Periodo periodo, BigDecimal saldo, boolean temLancamentos) {
    String mes = periodo.nomeMes();
    if (!temLancamentos) {
      return "Nenhum lançamento ativo em " + mes;
    }
    int sinal = saldo.signum();
    if (sinal > 0) {
      return "Saldo positivo de " + moeda(saldo) + " em " + mes;
    }
    if (sinal < 0) {
      return "As despesas superaram as receitas em " + moeda(saldo.abs()) + " em " + mes;
    }
    return "Receitas e despesas se igualaram em " + mes + ": saldo de R$ 0,00";
  }

  /** Formata em reais no padrão pt-BR, ex.: "R$ 4.920,00" (espaço comum após o símbolo). */
  static String moeda(BigDecimal valor) {
    DecimalFormat formato = new DecimalFormat("#,##0.00", PT_BR);
    formato.setRoundingMode(RoundingMode.HALF_UP);
    return "R$ " + formato.format(valor);
  }
}
