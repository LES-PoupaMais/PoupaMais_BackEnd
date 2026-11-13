package br.com.poupamais.lancamento.model.specifications;

import br.com.poupamais.lancamento.model.entidades.CategoriaLancamento;
import br.com.poupamais.lancamento.model.entidades.Lancamento;
import br.com.poupamais.lancamento.publico.dto.FiltroLancamento;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.shared.domain.Periodo;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros combináveis da listagem (RF005), reaproveitados pela exportação. Todo filtro nulo não
 * restringe nada; {@link #doFiltro} sempre inclui {@link #doUsuario} (RN005).
 */
public final class LancamentoSpecifications {

  private LancamentoSpecifications() {}

  public static Specification<Lancamento> doUsuario(Long idUsuario) {
    return (root, query, cb) -> cb.equal(root.get("idUsuario"), idUsuario);
  }

  /** Contém, sem diferenciar maiúsculas; vazio ou só espaços não filtra. */
  public static Specification<Lancamento> descricaoContem(String descricao) {
    if (descricao == null || descricao.isBlank()) {
      return semFiltro();
    }
    String padrao = "%" + escapar(descricao.strip().toLowerCase(Locale.ROOT)) + "%";
    return (root, query, cb) -> cb.like(cb.lower(root.get("descricao")), padrao, '\\');
  }

  public static Specification<Lancamento> doMes(Integer mes) {
    return mes == null ? semFiltro() : (root, query, cb) -> cb.equal(root.get("mes"), mes);
  }

  public static Specification<Lancamento> doAno(Integer ano) {
    return ano == null ? semFiltro() : (root, query, cb) -> cb.equal(root.get("ano"), ano);
  }

  public static Specification<Lancamento> doTipo(TipoLancamento tipo) {
    return tipo == null ? semFiltro() : (root, query, cb) -> cb.equal(root.get("tipo"), tipo);
  }

  /** Sem status = ATIVO + INATIVO: DELETADO (lixeira) só aparece quando pedido (RN004). */
  public static Specification<Lancamento> comStatus(StatusLancamento status) {
    if (status == null) {
      return (root, query, cb) ->
          root.get("status").in(StatusLancamento.ATIVO, StatusLancamento.INATIVO);
    }
    return (root, query, cb) -> cb.equal(root.get("status"), status);
  }

  /** Lançamentos vinculados à categoria (subconsulta: não duplica linhas). */
  public static Specification<Lancamento> daCategoria(Long idCategoria) {
    if (idCategoria == null) {
      return semFiltro();
    }
    return (root, query, cb) -> {
      Subquery<Long> sub = query.subquery(Long.class);
      Root<CategoriaLancamento> cl = sub.from(CategoriaLancamento.class);
      sub.select(cl.get("id").get("idLancamento"))
          .where(
              cb.equal(cl.get("id").get("idCategoria"), idCategoria),
              cb.equal(cl.get("id").get("idLancamento"), root.get("id")));
      return cb.exists(sub);
    };
  }

  /** Lançamentos de qualquer um dos meses informados; lista vazia não devolve nada. */
  public static Specification<Lancamento> dosPeriodos(Collection<Periodo> periodos) {
    return (root, query, cb) -> {
      List<Predicate> meses =
          periodos.stream()
              .map(p ->
                  cb.and(cb.equal(root.get("mes"), p.mes()), cb.equal(root.get("ano"), p.ano())))
              .toList();
      return meses.isEmpty() ? cb.disjunction() : cb.or(meses.toArray(Predicate[]::new));
    };
  }

  /** Todos os filtros da listagem combinados, sempre restritos ao dono. */
  public static Specification<Lancamento> doFiltro(Long idUsuario, FiltroLancamento filtro) {
    return Specification.allOf(
        doUsuario(idUsuario),
        descricaoContem(filtro.descricao()),
        doMes(filtro.mes()),
        doAno(filtro.ano()),
        doTipo(filtro.tipo()),
        comStatus(filtro.status()),
        daCategoria(filtro.categoriaId()));
  }

  private static Specification<Lancamento> semFiltro() {
    return (root, query, cb) -> cb.conjunction();
  }

  private static String escapar(String texto) {
    return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
