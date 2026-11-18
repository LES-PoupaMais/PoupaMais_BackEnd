package br.com.poupamais.lancamento.model.specifications;

import br.com.poupamais.lancamento.model.entidades.CategoriaLancamento;
import br.com.poupamais.lancamento.model.entidades.Lancamento;
import br.com.poupamais.lancamento.publico.dto.FiltroLancamento;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.shared.domain.Periodo;
import jakarta.persistence.criteria.LocalDateField;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros combináveis da listagem (RF005), reaproveitados pela exportação. Todo filtro nulo não
 * restringe nada; {@link #doFiltro} sempre inclui {@link #doUsuario} (RN005).
 */
public final class LancamentoSpecifications {

  private static final String DATA = "data";

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

  /**
   * Filtro de mês/ano da listagem sobre a data do lançamento. Com os dois: intervalo do mês (do dia
   * 1 ao último dia). Só o ano: 01/01 a 31/12. Só o mês: aquele mês de qualquer ano. Mês fora de
   * 1–12 ou ano fora de 2000–2100 não encontram nada (nenhuma data gravada fica fora dessa faixa).
   */
  public static Specification<Lancamento> doPeriodo(Integer mes, Integer ano) {
    if (mes == null && ano == null) {
      return semFiltro();
    }
    boolean mesInvalido = mes != null && (mes < 1 || mes > 12);
    boolean anoInvalido = ano != null && (ano < Periodo.ANO_MINIMO || ano > Periodo.ANO_MAXIMO);
    if (mesInvalido || anoInvalido) {
      return (root, query, cb) -> cb.disjunction();
    }
    if (ano == null) {
      return (root, query, cb) ->
          cb.equal(cb.extract(LocalDateField.MONTH, root.<LocalDate>get(DATA)), mes);
    }
    if (mes == null) {
      return entre(LocalDate.of(ano, 1, 1), LocalDate.of(ano, 12, 31));
    }
    Periodo periodo = new Periodo(mes, ano);
    return entre(periodo.inicio(), periodo.fim());
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

  /**
   * Lançamentos dos meses informados, como um único intervalo de datas: do dia 1 do mês mais antigo
   * ao último dia do mais recente (o dashboard pede meses seguidos, RN008). Lista vazia não devolve
   * nada.
   */
  public static Specification<Lancamento> dosPeriodos(Collection<Periodo> periodos) {
    if (periodos == null || periodos.isEmpty()) {
      return (root, query, cb) -> cb.disjunction();
    }
    Periodo primeiro = Collections.min(periodos);
    Periodo ultimo = Collections.max(periodos);
    return entre(primeiro.inicio(), ultimo.fim());
  }

  /** Todos os filtros da listagem combinados, sempre restritos ao dono. */
  public static Specification<Lancamento> doFiltro(Long idUsuario, FiltroLancamento filtro) {
    return Specification.allOf(
        doUsuario(idUsuario),
        descricaoContem(filtro.descricao()),
        doPeriodo(filtro.mes(), filtro.ano()),
        doTipo(filtro.tipo()),
        comStatus(filtro.status()),
        daCategoria(filtro.categoriaId()));
  }

  private static Specification<Lancamento> entre(LocalDate inicio, LocalDate fim) {
    return (root, query, cb) -> cb.between(root.<LocalDate>get(DATA), inicio, fim);
  }

  private static Specification<Lancamento> semFiltro() {
    return (root, query, cb) -> cb.conjunction();
  }

  private static String escapar(String texto) {
    return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
