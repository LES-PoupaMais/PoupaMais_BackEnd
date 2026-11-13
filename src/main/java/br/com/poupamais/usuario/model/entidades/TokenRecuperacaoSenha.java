package br.com.poupamais.usuario.model.entidades;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Token de recuperação de senha (RN014). Só o hash SHA-256 é guardado; uso único. */
@Entity
@Table(name = "token_recuperacao_senha")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRecuperacaoSenha {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "id_usuario", nullable = false, updatable = false)
  private Long idUsuario;

  @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
  private String tokenHash;

  @Column(name = "expira_em", nullable = false, updatable = false)
  private Instant expiraEm;

  @Column(name = "usado_em")
  private Instant usadoEm;

  @Column(name = "data_cadastro", nullable = false, updatable = false)
  private Instant dataCadastro;

  /** Ainda pode ser usado: não usado e não expirado. */
  public boolean valido(Instant agora) {
    return usadoEm == null && agora.isBefore(expiraEm);
  }
}
