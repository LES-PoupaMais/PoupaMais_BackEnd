package br.com.poupamais.usuario.model.repositories;

import br.com.poupamais.shared.security.SenhaAlteradaConsulta.SituacaoUsuario;
import br.com.poupamais.usuario.model.entidades.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

  /** Busca pelo e-mail já normalizado (trim + minúsculas). */
  Optional<Usuario> findByEmail(String email);

  boolean existsByEmail(String email);

  /** Usado pelo filtro JWT a cada requisição: só a coluna necessária, sem carregar a entidade. */
  @Query(
      "select new br.com.poupamais.shared.security.SenhaAlteradaConsulta$SituacaoUsuario("
          + "u.senhaAlteradaEm) from Usuario u where u.id = :id")
  Optional<SituacaoUsuario> situacao(@Param("id") Long id);
}
