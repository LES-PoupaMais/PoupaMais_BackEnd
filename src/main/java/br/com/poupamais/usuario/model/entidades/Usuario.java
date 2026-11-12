package br.com.poupamais.usuario.model.entidades;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Conta do usuário (RF009). E-mail sempre em minúsculas; senha só como hash BCrypt (RN011). */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 150)
  private String nome;

  @Column(nullable = false, length = 100, updatable = false)
  private String email;

  @Column(nullable = false, length = 255)
  private String senha;

  @Column(name = "data_cadastro", nullable = false, updatable = false)
  private LocalDate dataCadastro;

  @Column(name = "aceite_termos_em", nullable = false, updatable = false)
  private Instant aceiteTermosEm;

  @Column(name = "senha_alterada_em")
  private Instant senhaAlteradaEm;

  @PrePersist
  void antesDeInserir() {
    if (dataCadastro == null) {
      dataCadastro = LocalDate.now();
    }
  }
}
