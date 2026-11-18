package br.com.poupamais.lancamento.endpoints.mappers;

import br.com.poupamais.lancamento.endpoints.dto.CategoriaDoLancamentoResponse;
import br.com.poupamais.lancamento.endpoints.dto.LancamentoFiltroParams;
import br.com.poupamais.lancamento.endpoints.dto.LancamentoRequest;
import br.com.poupamais.lancamento.endpoints.dto.LancamentoResponse;
import br.com.poupamais.lancamento.endpoints.dto.PaginaResponse;
import br.com.poupamais.lancamento.publico.dto.FiltroLancamento;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * DTO ⇄ records do módulo. Os nomes das categorias já vêm no {@link LancamentoResumo}, buscados uma
 * vez por listagem pelo service.
 */
@Component
public class LancamentoMapper {

  public NovoLancamento paraNovo(LancamentoRequest r) {
    return new NovoLancamento(r.descricao(), r.tipo(), r.data(), r.valor(), r.categoriaIds());
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
        l.data(),
        l.valor(),
        l.status(),
        l.categorias().stream()
            .map(c -> new CategoriaDoLancamentoResponse(c.id(), c.descricao()))
            .toList(),
        l.dataCadastro(),
        l.dataAtualizacao(),
        l.dataExclusao());
  }

  public List<LancamentoResponse> paraResponse(List<LancamentoResumo> lancamentos) {
    return lancamentos.stream().map(this::paraResponse).toList();
  }

  /** Página do Spring → corpo próprio da API ({@code Page} não sai na resposta). */
  public PaginaResponse<LancamentoResponse> paraResponse(Page<LancamentoResumo> pagina) {
    return new PaginaResponse<>(
        paraResponse(pagina.getContent()),
        pagina.getNumber(),
        pagina.getSize(),
        pagina.getTotalElements(),
        pagina.getTotalPages());
  }
}
