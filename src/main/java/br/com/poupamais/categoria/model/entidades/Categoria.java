package br.com.poupamais.categoria.model.entidades;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Categoria de um usuário (RN002). O dono é referenciado só pelo id (fronteira de módulo). */
@Entity
@Table(name = "categoria")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Categoria {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "id_usuario", nullable = false, updatable = false)
  private Long idUsuario;

  @Column(nullable = false, length = 255)
  private String descricao;

  @Builder.Default
  @Column(nullable = false)
  private boolean ativa = true;

  @Column(name = "data_cadastro", nullable = false, updatable = false)
  private LocalDate dataCadastro;

  @PrePersist
  void antesDeInserir() {
    if (dataCadastro == null) {
      dataCadastro = LocalDate.now();
    }
  }
}
