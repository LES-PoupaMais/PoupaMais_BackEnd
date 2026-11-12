package br.com.poupamais.shared.security;

import br.com.poupamais.shared.exception.ErroAutenticacaoException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Id do usuário autenticado, extraído do token JWT. É a única fonte do {@code id_usuario} na API
 * (RN005): nunca use id vindo do body ou da query.
 *
 * <pre>{@code Long idUsuario = UsuarioLogado.id();}</pre>
 */
public final class UsuarioLogado {

  private UsuarioLogado() {}

  /**
   * Id do usuário da requisição atual.
   *
   * @throws ErroAutenticacaoException (401) se não houver usuário autenticado
   */
  public static Long id() {
    Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
    if (autenticacao != null && autenticacao.getPrincipal() instanceof Long id) {
      return id;
    }
    throw new ErroAutenticacaoException("Sessão inválida ou expirada. Faça login novamente.");
  }
}
