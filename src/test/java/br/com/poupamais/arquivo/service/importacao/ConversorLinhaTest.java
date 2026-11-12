package br.com.poupamais.arquivo.service.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConversorLinhaTest {

  private final ConversorLinha conversor = new ConversorLinha();

  private static Map<String, String> campos(
      String descricao, String tipo, String mes, String ano, String valor, String categorias) {
    Map<String, String> campos = new HashMap<>();
    campos.put("descricao", descricao);
    campos.put("tipo", tipo);
    campos.put("mes", mes);
    campos.put("ano", ano);
    campos.put("valor", valor);
    campos.put("categorias", categorias);
    return campos;
  }

  @Test
  void deveConverterLinhaValidaComTipoMinusculoECategoriasMultiplas() {
    DadosLinha dados =
        conversor.converter(
            campos(
                " iFood ", "despesa", "8", "2026", "420,00", "Alimentação| Lazer |alimentação||"));

    assertThat(dados.descricao()).isEqualTo("iFood");
    assertThat(dados.tipo()).isEqualTo(TipoLancamento.DESPESA);
    assertThat(dados.mes()).isEqualTo(8);
    assertThat(dados.ano()).isEqualTo(2026);
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
        campos("", "RECEITA", "8", "2026", "1", "A"), ConversorLinha.MSG_DESCRICAO_OBRIGATORIA);
    assertMotivo(
        campos(null, "RECEITA", "8", "2026", "1", "A"), ConversorLinha.MSG_DESCRICAO_OBRIGATORIA);
    assertMotivo(
        campos("x".repeat(101), "RECEITA", "8", "2026", "1", "A"),
        ConversorLinha.MSG_DESCRICAO_LONGA);
    assertMotivo(campos("a", "TRANSFERENCIA", "8", "2026", "1", "A"), ConversorLinha.MSG_TIPO);
    assertMotivo(campos("a", "RECEITA", "13", "2026", "1", "A"), ConversorLinha.MSG_MES);
    assertMotivo(campos("a", "RECEITA", "ago", "2026", "1", "A"), ConversorLinha.MSG_MES);
    assertMotivo(campos("a", "RECEITA", "8", "1999", "1", "A"), ConversorLinha.MSG_ANO);
    assertMotivo(
        campos("a", "RECEITA", "8", "2026", "1", " | "), ConversorLinha.MSG_CATEGORIA_OBRIGATORIA);
    assertMotivo(
        campos("a", "RECEITA", "8", "2026", "1", "c".repeat(256)),
        ConversorLinha.MSG_CATEGORIA_LONGA);
  }

  private void assertMotivo(Map<String, String> campos, String motivo) {
    assertThatThrownBy(() -> conversor.converter(campos))
        .isInstanceOf(LinhaInvalidaException.class)
        .hasMessage(motivo);
  }
}
