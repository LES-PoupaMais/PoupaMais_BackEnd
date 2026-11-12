package br.com.poupamais.shared.security;

import java.time.Instant;
import java.util.Optional;

/**
 * Consulta usada pelo {@link JwtAuthenticationFilter} para derrubar sessões após troca ou
 * redefinição de senha (RN014, RN015). Implementada pelo módulo {@code usuario}; enquanto não
 * houver bean, o filtro não faz essa checagem.
 */
public interface SenhaAlteradaConsulta {

  /**
   * Instante da última troca de senha ({@code usuario.senha_alterada_em}) do usuário. Vazio se a
   * senha nunca foi trocada. Tokens com {@code iat} anterior a esse instante são recusados.
   */
  Optional<Instant> senhaAlteradaEm(Long idUsuario);
}
