package br.com.poupamais.arquivo.service.importacao;

import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.shared.domain.Periodo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Converte os textos de uma linha nos tipos do lançamento e faz a checagem prévia da RN001, para
 * que uma categoria nova só seja criada em linha válida (RN002). A validação definitiva continua
 * sendo a do {@code LancamentoValidator}, chamada por {@code LancamentoFacade.criar}.
 *
 * <p>Valor: aceita {@code 1234,56}, {@code 1234.56}, {@code 1.234,56} e {@code 1,234.56}. Quando
 * aparecem vírgula e ponto, o último é o separador decimal e o outro é separador de milhar. Com só
 * um deles, ele é o decimal, exceto se aparecer mais de uma vez ({@code 1.234.567}), caso em que é
 * milhar. No máximo 2 casas decimais; {@code R$} e espaços são ignorados.
 */
@Component
public class ConversorLinha {

  static final int TAMANHO_MAXIMO_DESCRICAO = 100;
  static final int TAMANHO_MAXIMO_CATEGORIA = 255;
  static final BigDecimal VALOR_MAXIMO = new BigDecimal("99999999999999.99");

  static final String MSG_DESCRICAO_OBRIGATORIA = "A descrição é obrigatória";
  static final String MSG_DESCRICAO_LONGA = "A descrição deve ter no máximo 100 caracteres";
  static final String MSG_TIPO = "Tipo inválido: use RECEITA ou DESPESA";
  static final String MSG_MES = "O mês deve ser um número entre 1 e 12";
  static final String MSG_ANO =
      "O ano deve ser um número entre " + Periodo.ANO_MINIMO + " e " + Periodo.ANO_MAXIMO;
  static final String MSG_VALOR_OBRIGATORIO = "O valor é obrigatório";
  static final String MSG_VALOR_INVALIDO = "Valor inválido: use o formato 1234,56 ou 1234.56";
  static final String MSG_VALOR_POSITIVO = "O valor deve ser maior que zero";
  static final String MSG_VALOR_GRANDE = "O valor excede o máximo permitido";
  static final String MSG_CATEGORIA_OBRIGATORIA = "Informe pelo menos uma categoria";
  static final String MSG_CATEGORIA_LONGA =
      "A descrição da categoria deve ter no máximo 255 caracteres";

  private static final Pattern NUMERO = Pattern.compile("\\d+(\\.\\d{1,2})?");

  public DadosLinha converter(Map<String, String> campos) {
    String descricao = texto(campos.get(LayoutCsv.DESCRICAO));
    if (descricao.isEmpty()) {
      throw new LinhaInvalidaException(MSG_DESCRICAO_OBRIGATORIA);
    }
    if (descricao.length() > TAMANHO_MAXIMO_DESCRICAO) {
      throw new LinhaInvalidaException(MSG_DESCRICAO_LONGA);
    }
    TipoLancamento tipo = tipo(campos.get(LayoutCsv.TIPO));
    int mes = inteiro(campos.get(LayoutCsv.MES), 1, 12, MSG_MES);
    int ano = inteiro(campos.get(LayoutCsv.ANO), Periodo.ANO_MINIMO, Periodo.ANO_MAXIMO, MSG_ANO);
    BigDecimal valor = valor(campos.get(LayoutCsv.VALOR));
    List<String> categorias = categorias(campos.get(LayoutCsv.CATEGORIAS));
    return new DadosLinha(descricao, tipo, mes, ano, valor, categorias);
  }

  private static String texto(String valor) {
    return valor == null ? "" : valor.strip();
  }

  private static TipoLancamento tipo(String valor) {
    try {
      return TipoLancamento.valueOf(texto(valor).toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new LinhaInvalidaException(MSG_TIPO);
    }
  }

  private static int inteiro(String valor, int minimo, int maximo, String mensagem) {
    try {
      int numero = Integer.parseInt(texto(valor));
      if (numero < minimo || numero > maximo) {
        throw new LinhaInvalidaException(mensagem);
      }
      return numero;
    } catch (NumberFormatException e) {
      throw new LinhaInvalidaException(mensagem);
    }
  }

  /** Ver regra do separador de milhar no Javadoc da classe. */
  static BigDecimal valor(String bruto) {
    String valor = texto(bruto).replace("R$", "").replaceAll("\\s", "");
    if (valor.isEmpty()) {
      throw new LinhaInvalidaException(MSG_VALOR_OBRIGATORIO);
    }
    if (valor.startsWith("-")) {
      throw new LinhaInvalidaException(MSG_VALOR_POSITIVO);
    }
    int virgula = valor.lastIndexOf(',');
    int ponto = valor.lastIndexOf('.');
    String normalizado;
    if (virgula >= 0 && ponto >= 0) {
      boolean decimalVirgula = virgula > ponto;
      String milhar = decimalVirgula ? "." : ",";
      normalizado = valor.replace(milhar, "");
      if (decimalVirgula) {
        normalizado = normalizado.replace(',', '.');
      }
    } else if (virgula >= 0) {
      normalizado = umSeparador(valor, ',');
    } else if (ponto >= 0) {
      normalizado = umSeparador(valor, '.');
    } else {
      normalizado = valor;
    }
    if (!NUMERO.matcher(normalizado).matches()) {
      throw new LinhaInvalidaException(MSG_VALOR_INVALIDO);
    }
    BigDecimal numero = new BigDecimal(normalizado).setScale(2);
    if (numero.signum() <= 0) {
      throw new LinhaInvalidaException(MSG_VALOR_POSITIVO);
    }
    if (numero.compareTo(VALOR_MAXIMO) > 0) {
      throw new LinhaInvalidaException(MSG_VALOR_GRANDE);
    }
    return numero;
  }

  private static String umSeparador(String valor, char separador) {
    boolean repetido = valor.indexOf(separador) != valor.lastIndexOf(separador);
    String sep = String.valueOf(separador);
    return repetido ? valor.replace(sep, "") : valor.replace(separador, '.');
  }

  private static List<String> categorias(String valor) {
    List<String> resultado = new ArrayList<>();
    Set<String> vistas = new HashSet<>();
    for (String parte : texto(valor).split(Pattern.quote(LayoutCsv.SEPARADOR_CATEGORIAS))) {
      String categoria = parte.strip();
      if (categoria.isEmpty()) {
        continue;
      }
      if (categoria.length() > TAMANHO_MAXIMO_CATEGORIA) {
        throw new LinhaInvalidaException(MSG_CATEGORIA_LONGA);
      }
      if (vistas.add(categoria.toLowerCase(Locale.ROOT))) {
        resultado.add(categoria);
      }
    }
    if (resultado.isEmpty()) {
      throw new LinhaInvalidaException(MSG_CATEGORIA_OBRIGATORIA);
    }
    return List.copyOf(resultado);
  }
}
