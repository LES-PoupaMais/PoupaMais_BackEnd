package br.com.poupamais.arquivo.service.exportacao;

import br.com.poupamais.arquivo.service.importacao.LayoutCsv;
import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Cabeçalho e textos comuns aos exportadores: mesmo layout da importação + {@code status}. */
final class ColunasExportacao {

  static final List<String> CABECALHO;

  static {
    List<String> colunas = new ArrayList<>(LayoutCsv.COLUNAS);
    colunas.add(LayoutCsv.STATUS);
    CABECALHO = List.copyOf(colunas);
  }

  private ColunasExportacao() {}

  static String categorias(LancamentoResumo lancamento) {
    return lancamento.categorias().stream()
        .map(CategoriaResumo::descricao)
        .collect(Collectors.joining(LayoutCsv.SEPARADOR_CATEGORIAS));
  }
}
