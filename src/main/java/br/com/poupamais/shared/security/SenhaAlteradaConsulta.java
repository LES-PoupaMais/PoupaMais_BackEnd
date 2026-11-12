package br.com.poupamais.shared.security;

import java.time.Instant;
import java.util.Optional;

/**
 * Consulta usada pelo {@link JwtAuthenticationFilter} para derrubar sessões após troca ou
 * redefinição de senha (RN014, RN015) e de contas excluídas (RF017). Implementada pelo módulo
 * {@code usuario}; enquanto não houver bean (ex.: testes web de outros módulos), o filtro não faz
 * essa checagem.
 */
public interface SenhaAlteradaConsulta {

  /**
   * Situação da sessão do usuário, em uma única consulta. Vazio se o usuário <b>não existe mais</b>
   * (conta excluída): o filtro recusa o token.
   */
  Optional<SituacaoUsuario> situacao(Long idUsuario);

  /**
   * @param senhaAlteradaEm última troca de senha ({@code usuario.senha_alterada_em}); {@code null}
   *     se nunca foi trocada. Tokens com {@code iat} anterior a esse instante são recusados.
   */
  record SituacaoUsuario(Instant senhaAlteradaEm) {}
}
