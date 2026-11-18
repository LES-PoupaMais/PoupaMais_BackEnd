package br.com.poupamais.arquivo.service.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConversorLinhaTest {

  private final ConversorLinha conversor = new ConversorLinha();

  private static Map<String, String> campos(
      String descricao, String tipo, String data, String valor, String categorias) {
    Map<String, String> campos = new HashMap<>();
    campos.put("descricao", descricao);
    campos.put("tipo", tipo);
    campos.put("data", data);
    campos.put("valor", valor);
    campos.put("categorias", categorias);
    return campos;
  }

  @Test
  void deveConverterLinhaValidaComTipoMinusculoECategoriasMultiplas() {
    DadosLinha dados =
        conversor.converter(
            campos(
                " iFood ", "despesa", "15/08/2026", "420,00", "Alimentação| Lazer |alimentação||"));

    assertThat(dados.descricao()).isEqualTo("iFood");
    assertThat(dados.tipo()).isEqualTo(TipoLancamento.DESPESA);
    assertThat(dados.data()).isEqualTo(LocalDate.of(2026, 8, 15));
    assertThat(dados.valor()).isEqualByComparingTo("420.00");
    assertThat(dados.categorias()).containsExactly("Alimentação", "Lazer");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '#',
      value = {
        "1234,56#1234.56",
        "1234.56#1234.56",
        "1.234,56#1234.56",
        "1,234.56#1234.56",
        "1.234.567#1234567.00",
        "1.234.567,8#1234567.80",
        "R$ 10,5#10.50",
        "7#7.00"
      })
  void deveConverterValoresComVirgulaOuPonto(String bruto, String esperado) {
    assertThat(ConversorLinha.valor(bruto)).isEqualByComparingTo(esperado);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '#',
      value = {
        "abc#" + ConversorLinha.MSG_VALOR_INVALIDO,
        "1,234#" + ConversorLinha.MSG_VALOR_INVALIDO,
        "10,555#" + ConversorLinha.MSG_VALOR_INVALIDO,
        "1,2,3.4.5#" + ConversorLinha.MSG_VALOR_INVALIDO,
        "-5#" + ConversorLinha.MSG_VALOR_POSITIVO,
        "0,00#" + ConversorLinha.MSG_VALOR_POSITIVO,
        "100000000000000#" + ConversorLinha.MSG_VALOR_GRANDE,
        "' '#" + ConversorLinha.MSG_VALOR_OBRIGATORIO
      })
  void deveRecusarValoresInvalidos(String bruto, String motivo) {
    assertThatThrownBy(() -> ConversorLinha.valor(bruto))
        .isInstanceOf(LinhaInvalidaException.class)
        .hasMessage(motivo);
  }

  @Test
  void deveRecusarCamposInvalidosComMotivo() {
    assertMotivo(
        campos("", "RECEITA", "15/08/2026", "1", "A"), ConversorLinha.MSG_DESCRICAO_OBRIGATORIA);
    assertMotivo(
        campos(null, "RECEITA", "15/08/2026", "1", "A"), ConversorLinha.MSG_DESCRICAO_OBRIGATORIA);
    assertMotivo(
        campos("x".repeat(101), "RECEITA", "15/08/2026", "1", "A"),
        ConversorLinha.MSG_DESCRICAO_LONGA);
    assertMotivo(campos("a", "TRANSFERENCIA", "15/08/2026", "1", "A"), ConversorLinha.MSG_TIPO);
    assertMotivo(campos("a", "RECEITA", " ", "1", "A"), "A data é obrigatória (dd/mm/aaaa)");
    assertMotivo(
        campos("a", "RECEITA", "31/12/1999", "1", "A"),
        "A data deve estar entre 01/01/2000 e 31/12/2100");
    assertMotivo(
        campos("a", "RECEITA", "01/01/2101", "1", "A"),
        "A data deve estar entre 01/01/2000 e 31/12/2100");
    assertMotivo(
        campos("a", "RECEITA", "15/08/2026", "1", " | "), ConversorLinha.MSG_CATEGORIA_OBRIGATORIA);
    assertMotivo(
        campos("a", "RECEITA", "15/08/2026", "1", "c".repeat(256)),
        ConversorLinha.MSG_CATEGORIA_LONGA);
  }

  @ParameterizedTest
  @CsvSource({
    "01/01/2000, 2000-01-01",
    "31/12/2100, 2100-12-31",
    "29/02/2028, 2028-02-29",
    "31/01/2026, 2026-01-31",
    "' 05/11/2026 ', 2026-11-05"
  })
  void deveConverterDataDdMmAaaa(String bruto, LocalDate esperada) {
    assertThat(ConversorLinha.data(bruto)).isEqualTo(esperada);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "31/02/2026",
        "29/02/2026",
        "31/04/2026",
        "31/06/2026",
        "31/09/2026",
        "31/11/2026",
        "00/01/2026",
        "32/01/2026",
        "15/13/2026",
        "5/8/2026",
        "15/08/26",
        "2026-08-15",
        "15-08-2026",
        "agosto"
      })
  void deveRecusarDataQueNaoExisteOuForaDoFormato(String bruto) {
    assertThatThrownBy(() -> ConversorLinha.data(bruto))
        .isInstanceOf(LinhaInvalidaException.class)
        .hasMessage("Data inválida (" + bruto + "). Use dd/mm/aaaa com um dia que exista.");
  }

  @Test
  void mensagemDaDataInvalidaMostraOValorDigitado() {
    assertMotivo(
        campos("a", "DESPESA", "31/02/2026", "1", "A"),
        "Data inválida (31/02/2026). Use dd/mm/aaaa com um dia que exista.");
  }

  private void assertMotivo(Map<String, String> campos, String motivo) {
    assertThatThrownBy(() -> conversor.converter(campos))
        .isInstanceOf(LinhaInvalidaException.class)
        .hasMessage(motivo);
  }
}
