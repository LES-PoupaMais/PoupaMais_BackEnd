package br.com.poupamais.arquivo.exportacao;

import br.com.poupamais.lancamento.LancamentoResumo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.RoundingMode;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Planilha Excel (Apache POI): cabeçalho em negrito, mês/ano/valor numéricos, valor com 2 casas.
 */
@Component
public class ExportadorXlsx implements ExportadorLancamentos {

  static final String ABA = "Lançamentos";

  @Override
  public String formato() {
    return "xlsx";
  }

  @Override
  public String contentType() {
    return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  }

  @Override
  public byte[] exportar(List<LancamentoResumo> lancamentos) {
    try (XSSFWorkbook planilha = new XSSFWorkbook();
        ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
      Sheet aba = planilha.createSheet(ABA);
      CellStyle negrito = planilha.createCellStyle();
      Font fonte = planilha.createFont();
      fonte.setBold(true);
      negrito.setFont(fonte);
      CellStyle moeda = planilha.createCellStyle();
      moeda.setDataFormat(planilha.createDataFormat().getFormat("0.00"));

      Row cabecalho = aba.createRow(0);
      List<String> colunas = ColunasExportacao.CABECALHO;
      for (int i = 0; i < colunas.size(); i++) {
        cabecalho.createCell(i).setCellValue(colunas.get(i));
        cabecalho.getCell(i).setCellStyle(negrito);
      }
      int numero = 1;
      for (LancamentoResumo l : lancamentos) {
        Row linha = aba.createRow(numero++);
        linha.createCell(0).setCellValue(l.descricao());
        linha.createCell(1).setCellValue(l.tipo().name());
        linha.createCell(2).setCellValue(l.mes());
        linha.createCell(3).setCellValue(l.ano());
        linha.createCell(4).setCellValue(l.valor().setScale(2, RoundingMode.HALF_UP).doubleValue());
        linha.getCell(4).setCellStyle(moeda);
        linha.createCell(5).setCellValue(ColunasExportacao.categorias(l));
        linha.createCell(6).setCellValue(l.status().name());
      }
      for (int i = 0; i < colunas.size(); i++) {
        aba.autoSizeColumn(i);
      }
      planilha.write(saida);
      return saida.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
