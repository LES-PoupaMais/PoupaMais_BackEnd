package br.com.poupamais.arquivo.service.exportacao;

import br.com.poupamais.arquivo.service.importacao.LayoutCsv;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

/**
 * CSV no layout da importação + {@code status}: {@code ;}, UTF-8 com BOM (o Excel pt-BR reconhece
 * os acentos) e valor com vírgula decimal ({@code 8500,00}). Reimportável sem ajustes.
 */
@Component
public class ExportadorCsv implements ExportadorLancamentos {

  @Override
  public String formato() {
    return "csv";
  }

  @Override
  public String contentType() {
    return "text/csv;charset=UTF-8";
  }

  @Override
  public byte[] exportar(List<LancamentoResumo> lancamentos) {
    ByteArrayOutputStream saida = new ByteArrayOutputStream();
    try (Writer escritor = new OutputStreamWriter(saida, StandardCharsets.UTF_8);
        CSVPrinter csv = new CSVPrinter(escritor, LayoutCsv.FORMATO)) {
      escritor.write(LayoutCsv.BOM);
      csv.printRecord(ColunasExportacao.CABECALHO);
      for (LancamentoResumo l : lancamentos) {
        csv.printRecord(
            l.descricao(),
            l.tipo().name(),
            l.mes(),
            l.ano(),
            l.valor().setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ','),
            ColunasExportacao.categorias(l),
            l.status().name());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return saida.toByteArray();
  }
}
