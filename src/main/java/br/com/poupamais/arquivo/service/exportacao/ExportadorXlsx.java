package br.com.poupamais.arquivo.service.exportacao;

import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
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
 * Planilha Excel (Apache POI): cabeçalho em negrito, data como célula de data ({@code dd/mm/yyyy})
 * e valor numérico com 2 casas.
 */
@Component
public class ExportadorXlsx implements ExportadorLancamentos {

  static final String ABA = "Lançamentos";
  static final String FORMATO_DATA = "dd/mm/yyyy";

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
      CellStyle data = planilha.createCellStyle();
      data.setDataFormat(planilha.createDataFormat().getFormat(FORMATO_DATA));

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
        linha.createCell(2).setCellValue(l.data());
        linha.getCell(2).setCellStyle(data);
        linha.createCell(3).setCellValue(l.valor().setScale(2, RoundingMode.HALF_UP).doubleValue());
        linha.getCell(3).setCellStyle(moeda);
        linha.createCell(4).setCellValue(ColunasExportacao.categorias(l));
        linha.createCell(5).setCellValue(l.status().name());
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
