package br.com.poupamais.lancamento.model;

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

  /** Ordenação da listagem: ano, mês e id decrescentes. */
  Sort ORDENACAO = Sort.by(Sort.Order.desc("ano"), Sort.Order.desc("mes"), Sort.Order.desc("id"));

  @EntityGraph(attributePaths = "categorias")
  Optional<Lancamento> findByIdAndIdUsuario(Long id, Long idUsuario);

  /** Busca já com as categorias, sem N+1. */
  @Override
  @EntityGraph(attributePaths = "categorias")
  List<Lancamento> findAll(Specification<Lancamento> spec, Sort sort);
}
