package br.com.poupamais.arquivo.service.exportacao;

import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import java.util.List;

/** Strategy de exportação: um formato de arquivo por implementação (RF007). */
public interface ExportadorLancamentos {

  /** Valor do parâmetro {@code formato} (minúsculas), também usado como extensão do arquivo. */
  String formato();

  /** Content type do arquivo gerado. */
  String contentType();

  byte[] exportar(List<LancamentoResumo> lancamentos);
}
