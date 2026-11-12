package br.com.poupamais.arquivo.importacao;

import br.com.poupamais.shared.exception.RegraNegocioException;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Lê o arquivo inteiro e confere os limites da RN012 antes de qualquer gravação: tamanho, texto
 * UTF-8 (com ou sem BOM), cabeçalho e número de linhas de dados. Qualquer problema aqui recusa o
 * arquivo todo (422). Linhas em branco são ignoradas (não contam no total nem como falha).
 */
@Component
public class LeitorCsv {

  public static final long TAMANHO_MAXIMO = 2L * 1024 * 1024;
  public static final int MAXIMO_LINHAS = 5000;

  static final String MSG_VAZIO = "O arquivo está vazio.";
  static final String MSG_GRANDE = "O arquivo excede o tamanho máximo de 2 MB.";
  static final String MSG_NAO_TEXTO = "O arquivo não é um CSV de texto válido (use UTF-8).";
  static final String MSG_CABECALHO =
      "Cabeçalho inválido. A primeira linha deve ser: " + String.join(";", LayoutCsv.COLUNAS);
  static final String MSG_SEM_DADOS = "O arquivo não tem linhas de lançamento.";
  static final String MSG_MUITAS_LINHAS =
      "O arquivo excede o máximo de " + MAXIMO_LINHAS + " linhas de lançamento.";

  public List<LinhaCsv> ler(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new RegraNegocioException(MSG_VAZIO);
    }
    if (bytes.length > TAMANHO_MAXIMO) {
      throw new RegraNegocioException(MSG_GRANDE);
    }
    String texto = decodificar(bytes);
    if (!texto.isEmpty() && texto.charAt(0) == LayoutCsv.BOM) {
      texto = texto.substring(1);
    }
    String[] linhas = texto.split("\r\n|\r|\n", -1);

    int indiceCabecalho = 0;
    while (indiceCabecalho < linhas.length && linhas[indiceCabecalho].isBlank()) {
      indiceCabecalho++;
    }
    if (indiceCabecalho == linhas.length) {
      throw new RegraNegocioException(MSG_VAZIO);
    }
    Cabecalho cabecalho = lerCabecalho(linhas[indiceCabecalho]);

    List<String[]> dados = new ArrayList<>();
    for (int i = indiceCabecalho + 1; i < linhas.length; i++) {
      if (!linhas[i].isBlank()) {
        dados.add(new String[] {String.valueOf(i + 1), linhas[i]});
        if (dados.size() > MAXIMO_LINHAS) {
          throw new RegraNegocioException(MSG_MUITAS_LINHAS);
        }
      }
    }
    if (dados.isEmpty()) {
      throw new RegraNegocioException(MSG_SEM_DADOS);
    }
    return dados.stream().map(d -> separar(Integer.parseInt(d[0]), d[1], cabecalho)).toList();
  }

  private static String decodificar(byte[] bytes) {
    try {
      String texto =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
      if (texto.indexOf('\0') >= 0) {
        throw new RegraNegocioException(MSG_NAO_TEXTO);
      }
      return texto;
    } catch (CharacterCodingException e) {
      throw new RegraNegocioException(MSG_NAO_TEXTO);
    }
  }

  /** Nome da coluna (trim, minúsculas) → posição. Colunas extras (ex.: {@code status}) ficam. */
  private record Cabecalho(Map<String, Integer> posicoes, int total) {}

  private static Cabecalho lerCabecalho(String linha) {
    List<String> nomes = campos(linha);
    if (nomes == null) {
      throw new RegraNegocioException(MSG_CABECALHO);
    }
    Map<String, Integer> posicoes = new HashMap<>();
    for (int i = 0; i < nomes.size(); i++) {
      posicoes.putIfAbsent(nomes.get(i).strip().toLowerCase(Locale.ROOT), i);
    }
    if (!posicoes.keySet().containsAll(LayoutCsv.COLUNAS)) {
      throw new RegraNegocioException(MSG_CABECALHO);
    }
    return new Cabecalho(posicoes, nomes.size());
  }

  private static LinhaCsv separar(int numero, String conteudo, Cabecalho cabecalho) {
    int esperadas = cabecalho.total();
    List<String> valores = campos(conteudo);
    if (valores == null) {
      return new LinhaCsv(numero, conteudo, Map.of(), "Linha com aspas mal formadas");
    }
    if (valores.size() < esperadas) {
      return new LinhaCsv(
          numero,
          conteudo,
          Map.of(),
          "Faltam colunas: a linha tem %d e o cabeçalho tem %d"
              .formatted(valores.size(), esperadas));
    }
    if (valores.size() > esperadas) {
      return new LinhaCsv(
          numero,
          conteudo,
          Map.of(),
          "Colunas a mais: a linha tem %d e o cabeçalho tem %d"
              .formatted(valores.size(), esperadas));
    }
    Map<String, String> campos = new HashMap<>();
    LayoutCsv.COLUNAS.forEach(c -> campos.put(c, valores.get(cabecalho.posicoes().get(c))));
    return new LinhaCsv(numero, conteudo, campos, null);
  }

  /** Separa uma linha pelo formato do layout; {@code null} se as aspas estiverem quebradas. */
  private static List<String> campos(String linha) {
    try (CSVParser parser = CSVParser.parse(new StringReader(linha), LayoutCsv.FORMATO)) {
      List<CSVRecord> registros = parser.getRecords();
      if (registros.size() != 1) {
        return null;
      }
      return registros.getFirst().toList();
    } catch (IOException | UncheckedIOException e) {
      return null;
    }
  }
}
