package br.com.poupamais.lancamento.publico.dto;

import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;

/**
 * Filtros da listagem (RF005), com a mesma semântica dos parâmetros de {@code GET
 * /api/lancamentos}: campos nulos não filtram; sem {@code status} = ATIVO + INATIVO (nunca DELETADO
 * sem pedir); {@code descricao} = contém, sem diferenciar maiúsculas.
 */
public record FiltroLancamento(
    String descricao,
    Integer mes,
    Integer ano,
    TipoLancamento tipo,
    StatusLancamento status,
    Long categoriaId) {

  /** Sem nenhum filtro (ATIVO + INATIVO do usuário). */
  public static FiltroLancamento vazio() {
    return new FiltroLancamento(null, null, null, null, null, null);
  }
}
