package br.com.poupamais.lancamento.api;

import br.com.poupamais.lancamento.FiltroLancamento;
import br.com.poupamais.lancamento.LancamentoResumo;
import br.com.poupamais.lancamento.NovoLancamento;
import br.com.poupamais.lancamento.api.dto.CategoriaDoLancamentoResponse;
import br.com.poupamais.lancamento.api.dto.LancamentoFiltroParams;
import br.com.poupamais.lancamento.api.dto.LancamentoRequest;
import br.com.poupamais.lancamento.api.dto.LancamentoResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * DTO ⇄ records do módulo. Os nomes das categorias já vêm no {@link LancamentoResumo}, buscados uma
 * vez por listagem pelo service.
 */
@Component
public class LancamentoMapper {

  public NovoLancamento paraNovo(LancamentoRequest r) {
    return new NovoLancamento(
        r.descricao(), r.tipo(), r.mes(), r.ano(), r.valor(), r.categoriaIds());
  }

  public FiltroLancamento paraFiltro(LancamentoFiltroParams p) {
    return new FiltroLancamento(
        p.descricao(), p.mes(), p.ano(), p.tipo(), p.status(), p.categoriaId());
  }

  public LancamentoResponse paraResponse(LancamentoResumo l) {
    return new LancamentoResponse(
        l.id(),
        l.descricao(),
        l.tipo(),
        l.mes(),
        l.ano(),
        l.valor(),
        l.status(),
        l.categorias().stream()
            .map(c -> new CategoriaDoLancamentoResponse(c.id(), c.descricao()))
            .toList(),
        l.dataCadastro(),
        l.dataAtualizacao());
  }

  public List<LancamentoResponse> paraResponse(List<LancamentoResumo> lancamentos) {
    return lancamentos.stream().map(this::paraResponse).toList();
  }
}
