package br.com.poupamais.lancamento.service;

import br.com.poupamais.categoria.CategoriaFacade;
import br.com.poupamais.categoria.CategoriaResumo;
import br.com.poupamais.lancamento.LancamentoResumo;
import br.com.poupamais.lancamento.model.Lancamento;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Entidade → {@link LancamentoResumo}, com os nomes das categorias buscados na {@link
 * CategoriaFacade} uma única vez por lista (sem N+1).
 */
@Component
@RequiredArgsConstructor
public class LancamentoResumoConversor {

  private final CategoriaFacade categoriaFacade;

  public LancamentoResumo converter(Lancamento lancamento) {
    return converter(List.of(lancamento)).getFirst();
  }

  public List<LancamentoResumo> converter(Collection<Lancamento> lancamentos) {
    if (lancamentos.isEmpty()) {
      return List.of();
    }
    Long idUsuario = lancamentos.iterator().next().getIdUsuario();
    Set<Long> ids =
        lancamentos.stream().flatMap(l -> l.idsCategorias().stream()).collect(Collectors.toSet());
    Map<Long, CategoriaResumo> categorias =
        categoriaFacade.buscarPorIds(idUsuario, ids).stream()
            .collect(Collectors.toMap(CategoriaResumo::id, Function.identity()));
    return lancamentos.stream().map(l -> resumo(l, categorias)).toList();
  }

  private static LancamentoResumo resumo(Lancamento l, Map<Long, CategoriaResumo> categorias) {
    List<CategoriaResumo> doLancamento =
        l.idsCategorias().stream().map(categorias::get).filter(Objects::nonNull).toList();
    return new LancamentoResumo(
        l.getId(),
        l.getDescricao(),
        l.getTipo(),
        l.getMes(),
        l.getAno(),
        l.getValor(),
        l.getStatus(),
        doLancamento,
        l.getDataCadastro(),
        l.getDataAtualizacao());
  }
}
