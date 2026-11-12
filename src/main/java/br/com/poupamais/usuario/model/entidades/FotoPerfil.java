package br.com.poupamais.usuario.model.entidades;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Foto de perfil (0..1 por usuário, RN015). Separada para não carregar a imagem com o usuário. */
@Entity
@Table(name = "foto_perfil")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FotoPerfil {

  @Id
  @Column(name = "id_usuario")
  private Long idUsuario;

  @Column(nullable = false)
  private byte[] conteudo;

  @Column(name = "tipo_conteudo", nullable = false, length = 30)
  private String tipoConteudo;

  @Column(name = "data_atualizacao", nullable = false)
  private Instant dataAtualizacao;
}
