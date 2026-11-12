package br.com.poupamais.lancamento.model.entidades;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Vínculo lançamento–categoria. Mapeado como entidade (e não {@code @ManyToMany}) porque a tabela
 * replica {@code id_usuario}, que garante no banco que os dois são do mesmo dono. A categoria é
 * referenciada só pelo id (fronteira de módulo).
 */
@Entity
@Table(name = "categorias_lancamentos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoriaLancamento {

  @EmbeddedId private CategoriaLancamentoId id;

  @MapsId("idLancamento")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "id_lancamento")
  private Lancamento lancamento;

  @Column(name = "id_usuario", nullable = false, updatable = false)
  private Long idUsuario;

  CategoriaLancamento(Lancamento lancamento, Long idCategoria) {
    this.id = new CategoriaLancamentoId(idCategoria, lancamento.getId());
    this.lancamento = lancamento;
    this.idUsuario = lancamento.getIdUsuario();
  }

  public Long getIdCategoria() {
    return id.getIdCategoria();
  }
}
