package br.com.poupamais.arquivo.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.poupamais.shared.exception.RegraNegocioException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeitorCsvTest {

  private static final String CABECALHO = "descricao;tipo;mes;ano;valor;categorias\n";
  private final LeitorCsv leitor = new LeitorCsv();

  private static byte[] utf8(String texto) {
    return texto.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void deveLerLinhasComNumeroRealNoArquivo() {
    List<LinhaCsv> linhas =
        leitor.ler(
            utf8(
                CABECALHO
                    + "Salário;RECEITA;8;2026;8500,00;Salário\r\n\n  \nLuz;despesa;8;2026;1.5;Casa"));

    assertThat(linhas).hasSize(2);
    assertThat(linhas.get(0).numero()).isEqualTo(2);
    assertThat(linhas.get(0).campos()).containsEntry("descricao", "Salário");
    assertThat(linhas.get(1).numero()).isEqualTo(5);
    assertThat(linhas.get(1).conteudo()).isEqualTo("Luz;despesa;8;2026;1.5;Casa");
  }

  @Test
  void deveAceitarBomCabecalhoComMaiusculasEspacosOrdemTrocadaEColunaStatus() {
    String texto =
        "﻿ VALOR ; Descricao;TIPO;mes;ano;categorias;status\n10,00;Pão;DESPESA;8;2026;Mercado;ATIVO";

    LinhaCsv linha = leitor.ler(utf8(texto)).getFirst();

    assertThat(linha.erro()).isNull();
    assertThat(linha.campos())
        .containsEntry("valor", "10,00")
        .containsEntry("descricao", "Pão")
        .doesNotContainKey("status");
  }

  @Test
  void deveRespeitarAspasComPontoEVirgulaNaDescricao() {
    LinhaCsv linha =
        leitor.ler(utf8(CABECALHO + "\"Feira; sábado\";DESPESA;8;2026;10;Mercado")).getFirst();

    assertThat(linha.campos()).containsEntry("descricao", "Feira; sábado");
  }

  @Test
  void deveMarcarLinhaComColunasFaltandoOuSobrando() {
    List<LinhaCsv> linhas =
        leitor.ler(
            utf8(
                CABECALHO
                    + "Pão;DESPESA;8;2026;10\nPão;DESPESA;8;2026;10;Mercado;extra\n\"aberta;x"));

    assertThat(linhas.get(0).erro()).startsWith("Faltam colunas");
    assertThat(linhas.get(1).erro()).startsWith("Colunas a mais");
    assertThat(linhas.get(2).erro()).isEqualTo("Linha com aspas mal formadas");
  }

  @Test
  void deveRecusarArquivoVazioOuSoComLinhasEmBranco() {
    assertThatThrownBy(() -> leitor.ler(new byte[0])).hasMessage(LeitorCsv.MSG_VAZIO);
    assertThatThrownBy(() -> leitor.ler(null)).hasMessage(LeitorCsv.MSG_VAZIO);
    assertThatThrownBy(() -> leitor.ler(utf8("\n \n"))).hasMessage(LeitorCsv.MSG_VAZIO);
  }

  @Test
  void deveRecusarArquivoSemCabecalho() {
    assertThatThrownBy(() -> leitor.ler(utf8("Pão;DESPESA;8;2026;10;Mercado\n")))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage(LeitorCsv.MSG_CABECALHO);
    assertThatThrownBy(() -> leitor.ler(utf8("\"quebrado\n"))).hasMessage(LeitorCsv.MSG_CABECALHO);
  }

  @Test
  void deveRecusarArquivoSoComCabecalho() {
    assertThatThrownBy(() -> leitor.ler(utf8(CABECALHO + "\n")))
        .hasMessage(LeitorCsv.MSG_SEM_DADOS);
  }

  @Test
  void deveRecusarArquivoQueNaoETexto() {
    assertThatThrownBy(() -> leitor.ler(new byte[] {(byte) 0xFF, (byte) 0xFE, 0x41}))
        .hasMessage(LeitorCsv.MSG_NAO_TEXTO);
    assertThatThrownBy(() -> leitor.ler(utf8(CABECALHO + "a\0b")))
        .hasMessage(LeitorCsv.MSG_NAO_TEXTO);
  }

  @Test
  void deveRecusarArquivoMaiorQue2Mb() {
    byte[] grande = new byte[(int) LeitorCsv.TAMANHO_MAXIMO + 1];

    assertThatThrownBy(() -> leitor.ler(grande)).hasMessage(LeitorCsv.MSG_GRANDE);
  }

  @Test
  void deveRecusarArquivoComMaisDe5000Linhas() {
    String linha = "Pão;DESPESA;8;2026;10;Mercado\n";

    assertThat(leitor.ler(utf8(CABECALHO + linha.repeat(5000)))).hasSize(5000);
    assertThatThrownBy(() -> leitor.ler(utf8(CABECALHO + linha.repeat(5001))))
        .hasMessage(LeitorCsv.MSG_MUITAS_LINHAS);
  }
}
