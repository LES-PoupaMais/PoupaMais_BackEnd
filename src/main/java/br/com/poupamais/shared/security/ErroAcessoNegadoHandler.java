package br.com.poupamais.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Responde 403 no padrão de erro da API ({@code {status, codigo, mensagem}}, RNF007). Hoje não há
 * papéis (todo usuário autenticado acessa só o que é dele), mas a resposta fica no padrão se o
 * Spring Security negar acesso.
 */
class ErroAcessoNegadoHandler implements AccessDeniedHandler {

  static final String CORPO =
      "{\"status\":403,\"codigo\":\"ACESSO_NEGADO\","
          + "\"mensagem\":\"Você não tem permissão para acessar este recurso.\"}";

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException erro)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(CORPO);
  }
}
