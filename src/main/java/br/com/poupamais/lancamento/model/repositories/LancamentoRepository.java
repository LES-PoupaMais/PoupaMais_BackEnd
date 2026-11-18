package br.com.poupamais.lancamento.model.repositories;

import br.com.poupamais.lancamento.model.entidades.Lancamento;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Acesso aos lançamentos, sempre filtrando pelo dono (RN005). */
public interface LancamentoRepository
    extends JpaRepository<Lancamento, Long>, JpaSpecificationExecutor<Lancamento> {

  /** Ordenação da listagem: data e id decrescentes (mais recente primeiro; empate estável). */
  Sort ORDENACAO = Sort.by(Sort.Order.desc("data"), Sort.Order.desc("id"));

  @EntityGraph(attributePaths = "categorias")
  Optional<Lancamento> findByIdAndIdUsuario(Long id, Long idUsuario);

  /** Busca já com as categorias, sem N+1. */
  @Override
  @EntityGraph(attributePaths = "categorias")
  List<Lancamento> findAll(Specification<Lancamento> spec, Sort sort);

  /**
   * Carrega com as categorias os lançamentos de uma página. A página em si vem de {@code
   * findAll(spec, pageable)} sem o fetch: com fetch de coleção o Hibernate cortaria em memória.
   */
  @EntityGraph(attributePaths = "categorias")
  List<Lancamento> findByIdIn(Collection<Long> ids);
}
