package br.com.poupamais.arquivo.service.exportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.poupamais.arquivo.service.importacao.ConversorLinha;
import br.com.poupamais.arquivo.service.importacao.LeitorCsv;
import br.com.poupamais.arquivo.service.importacao.LinhaCsv;
import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExportadoresTest {

  private static final List<LancamentoResumo> DADOS =
      List.of(
          new LancamentoResumo(
              1L,
              "Feira; sábado",
              TipoLancamento.DESPESA,
              8,
              2026,
              new BigDecimal("1234.5"),
              StatusLancamento.INATIVO,
              List.of(
                  new CategoriaResumo(1L, "Alimentação", true),
                  new CategoriaResumo(2L, "Lazer", true)),
              LocalDate.of(2026, 8, 1),
              null),
          new LancamentoResumo(
              2L,
              "Salário",
              TipoLancamento.RECEITA,
              7,
              2026,
              new BigDecimal("8500.00"),
              StatusLancamento.ATIVO,
              List.of(new CategoriaResumo(3L, "Salário", true)),
              LocalDate.of(2026, 7, 1),
              null));

  @Test
  void csvDeveTerBomCabecalhoComStatusEValorComVirgula() {
    String texto = new String(new ExportadorCsv().exportar(DADOS), StandardCharsets.UTF_8);

    assertThat(texto)
        .startsWith("﻿descricao;tipo;mes;ano;valor;categorias;status\r\n")
        .contains("\"Feira; sábado\";DESPESA;8;2026;1234,50;Alimentação|Lazer;INATIVO\r\n")
        .contains("Salário;RECEITA;7;2026;8500,00;Salário;ATIVO");
  }

  @Test
  void csvExportadoDeveSerReimportavel() {
    byte[] csv = new ExportadorCsv().exportar(DADOS);
    ConversorLinha conversor = new ConversorLinha();

    List<LinhaCsv> linhas = new LeitorCsv().ler(csv);

    assertThat(linhas).hasSize(2).allMatch(l -> l.erro() == null);
    var primeira = conversor.converter(linhas.getFirst().campos());
    assertThat(primeira.descricao()).isEqualTo("Feira; sábado");
    assertThat(primeira.valor()).isEqualByComparingTo("1234.50");
    assertThat(primeira.categorias()).containsExactly("Alimentação", "Lazer");
  }

  @Test
  void xlsxDeveTerCabecalhoEValoresNumericosComDuasCasas() throws Exception {
    byte[] bytes = new ExportadorXlsx().exportar(DADOS);

    try (XSSFWorkbook planilha = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet aba = planilha.getSheet(ExportadorXlsx.ABA);
      Row cabecalho = aba.getRow(0);
      assertThat(cabecalho.getCell(0).getStringCellValue()).isEqualTo("descricao");
      assertThat(cabecalho.getCell(6).getStringCellValue()).isEqualTo("status");
      Row linha = aba.getRow(1);
      assertThat(linha.getCell(0).getStringCellValue()).isEqualTo("Feira; sábado");
      assertThat(linha.getCell(2).getNumericCellValue()).isEqualTo(8);
      assertThat(linha.getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(linha.getCell(4).getNumericCellValue()).isEqualTo(1234.5);
      assertThat(linha.getCell(4).getCellStyle().getDataFormatString()).isEqualTo("0.00");
      assertThat(linha.getCell(5).getStringCellValue()).isEqualTo("Alimentação|Lazer");
      assertThat(linha.getCell(6).getStringCellValue()).isEqualTo("INATIVO");
      assertThat(aba.getLastRowNum()).isEqualTo(2);
    }
  }

  @Test
  void factoryDeveEscolherPeloFormatoSemDiferenciarMaiusculas() {
    ExportadorFactory fabrica =
        new ExportadorFactory(List.of(new ExportadorCsv(), new ExportadorXlsx()));

    assertThat(fabrica.para("csv")).isInstanceOf(ExportadorCsv.class);
    assertThat(fabrica.para(" XLSX ")).isInstanceOf(ExportadorXlsx.class);
    assertThatThrownBy(() -> fabrica.para("pdf"))
        .isInstanceOf(FormatoInvalidoException.class)
        .hasMessage("Formato inválido. Use: csv ou xlsx");
    assertThatThrownBy(() -> fabrica.para(null)).isInstanceOf(FormatoInvalidoException.class);
  }

  @Test
  void contentTypesDevemSerOsCorretos() {
    assertThat(new ExportadorCsv().contentType()).isEqualTo("text/csv;charset=UTF-8");
    assertThat(new ExportadorXlsx().contentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
  }
}
