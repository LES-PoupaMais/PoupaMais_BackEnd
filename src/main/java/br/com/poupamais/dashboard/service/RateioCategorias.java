package br.com.poupamais.dashboard.service;

import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse.DespesaCategoria;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * "Despesas por Categoria" (RN009): o valor de cada despesa é dividido igualmente entre as suas
 * categorias; os centavos que sobram vão para a primeira (menor id). A soma das fatias é sempre o
 * total das despesas.
 */
@Component
public class RateioCategorias {

  static final String SEM_CATEGORIA = "Sem categoria";

  public List<DespesaCategoria> ratear(List<LancamentoResumo> despesas) {
    Map<Long, DespesaCategoria> fatias = new LinkedHashMap<>();
    for (LancamentoResumo despesa : despesas) {
      List<CategoriaResumo> categorias =
          despesa.categorias().stream().sorted(Comparator.comparing(CategoriaResumo::id)).toList();
      if (categorias.isEmpty()) {
        somar(fatias, null, SEM_CATEGORIA, despesa.valor());
        continue;
      }
      BigDecimal quantidade = BigDecimal.valueOf(categorias.size());
      BigDecimal parte = despesa.valor().divide(quantidade, 2, RoundingMode.DOWN);
      BigDecimal sobra = despesa.valor().subtract(parte.multiply(quantidade));
      for (int i = 0; i < categorias.size(); i++) {
        CategoriaResumo categoria = categorias.get(i);
        somar(fatias, categoria.id(), categoria.descricao(), i == 0 ? parte.add(sobra) : parte);
      }
    }
    return fatias.values().stream()
        .sorted(
            Comparator.comparing(DespesaCategoria::valor)
                .reversed()
                .thenComparing(
                    DespesaCategoria::categoriaId, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  private static void somar(
      Map<Long, DespesaCategoria> fatias, Long id, String descricao, BigDecimal valor) {
    fatias.merge(
        id,
        new DespesaCategoria(id, descricao, valor.setScale(2, RoundingMode.HALF_UP)),
        (atual, novo) -> new DespesaCategoria(id, descricao, atual.valor().add(novo.valor())));
  }
}
