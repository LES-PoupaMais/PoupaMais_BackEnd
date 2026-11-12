package br.com.poupamais.lancamento.model.entidades;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Chave da associativa {@code categorias_lancamentos}. */
@Embeddable
@Getter
@NoArgsConstructor
public class CategoriaLancamentoId implements Serializable {

  @Column(name = "id_categoria", nullable = false)
  private Long idCategoria;

  @Column(name = "id_lancamento", nullable = false)
  private Long idLancamento;

  public CategoriaLancamentoId(Long idCategoria, Long idLancamento) {
    this.idCategoria = idCategoria;
    this.idLancamento = idLancamento;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof CategoriaLancamentoId outro
        && Objects.equals(idCategoria, outro.idCategoria)
        && Objects.equals(idLancamento, outro.idLancamento);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idCategoria, idLancamento);
  }
}
