package br.com.poupamais.usuario.model.repositories;

import br.com.poupamais.usuario.model.entidades.TokenRecuperacaoSenha;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRecuperacaoSenhaRepository
    extends JpaRepository<TokenRecuperacaoSenha, Long> {

  Optional<TokenRecuperacaoSenha> findByTokenHash(String tokenHash);

  /** Invalida (marca como usados) todos os tokens pendentes do usuário (RN014). */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update TokenRecuperacaoSenha t set t.usadoEm = :agora"
          + " where t.idUsuario = :idUsuario and t.usadoEm is null")
  int invalidarPendentes(@Param("idUsuario") Long idUsuario, @Param("agora") Instant agora);
}
