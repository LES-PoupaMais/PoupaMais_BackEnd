package br.com.poupamais.categoria.model.repositories;

import br.com.poupamais.categoria.model.entidades.Categoria;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso às categorias, sempre filtrando pelo dono (RN005). */
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

  Optional<Categoria> findByIdAndIdUsuario(Long id, Long idUsuario);

  List<Categoria> findByIdUsuarioAndIdIn(Long idUsuario, Collection<Long> ids);

  Optional<Categoria> findByIdUsuarioAndDescricaoIgnoreCase(Long idUsuario, String descricao);

  boolean existsByIdUsuarioAndDescricaoIgnoreCase(Long idUsuario, String descricao);

  boolean existsByIdUsuarioAndDescricaoIgnoreCaseAndIdNot(
      Long idUsuario, String descricao, Long id);

  /** Todas as categorias do usuário: ativas primeiro, depois em ordem alfabética. */
  @Query(
      "select c from Categoria c where c.idUsuario = :idUsuario"
          + " order by c.ativa desc, lower(c.descricao) asc")
  List<Categoria> listarDoUsuario(@Param("idUsuario") Long idUsuario);

  /** Categorias do usuário com o status informado, em ordem alfabética. */
  @Query(
      "select c from Categoria c where c.idUsuario = :idUsuario and c.ativa = :ativa"
          + " order by lower(c.descricao) asc")
  List<Categoria> listarDoUsuarioPorStatus(
      @Param("idUsuario") Long idUsuario, @Param("ativa") boolean ativa);

  /**
   * Se a categoria está vinculada a algum lançamento (de qualquer status, RN002). Consulta nativa
   * direto na associativa para não depender do módulo {@code lancamento}, que é quem depende deste.
   */
  @Query(
      value =
          "select exists(select 1 from poupamais.categorias_lancamentos cl"
              + " where cl.id_categoria = :idCategoria and cl.id_usuario = :idUsuario)",
      nativeQuery = true)
  boolean possuiLancamentos(
      @Param("idCategoria") Long idCategoria, @Param("idUsuario") Long idUsuario);

  /** Quantos lançamentos uma categoria tem no período (linha da consulta agregada). */
  interface QuantidadePorCategoria {
    Long getIdCategoria();

    Long getQuantidade();
  }

  /**
   * Lançamentos ATIVO/INATIVO do usuário com {@code data} entre {@code inicio} e {@code fim}
   * (inclusive), agrupados por categoria: uma única consulta para a listagem inteira (sem N+1).
   * Categoria sem lançamento no período não aparece. Nativa pelo mesmo motivo de {@link
   * #possuiLancamentos}: o módulo {@code categoria} não pode depender de {@code lancamento}.
   */
  @Query(
      value =
          "select cl.id_categoria as idCategoria, count(*) as quantidade"
              + " from poupamais.categorias_lancamentos cl"
              + " join poupamais.lancamento l"
              + " on l.id = cl.id_lancamento and l.id_usuario = cl.id_usuario"
              + " where cl.id_usuario = :idUsuario"
              + " and l.status in ('ATIVO', 'INATIVO')"
              + " and l.data between :inicio and :fim"
              + " group by cl.id_categoria",
      nativeQuery = true)
  List<QuantidadePorCategoria> contarLancamentosPorCategoria(
      @Param("idUsuario") Long idUsuario,
      @Param("inicio") LocalDate inicio,
      @Param("fim") LocalDate fim);
}
