package br.com.poupamais.lancamento.service;

import br.com.poupamais.categoria.publico.CategoriaFacade;
import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.lancamento.model.entidades.Lancamento;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        l.getData(),
        l.getValor(),
        l.getStatus(),
        doLancamento,
        emUtc(l.getDataCadastro()),
        emUtc(l.getDataAtualizacao()),
        emUtc(l.getDataExclusao()));
  }

  /** O driver pode devolver o instante no fuso da JVM; a API responde sempre em UTC. */
  private static OffsetDateTime emUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
