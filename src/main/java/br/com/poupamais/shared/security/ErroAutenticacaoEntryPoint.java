package br.com.poupamais.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** Responde 401 no padrão de erro da API ({@code {status, mensagem}}, RNF007). */
class ErroAutenticacaoEntryPoint implements AuthenticationEntryPoint {

  static final String CORPO =
      "{\"status\":401,\"mensagem\":\"Sessão inválida ou expirada. Faça login novamente.\"}";

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException erro)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(CORPO);
  }
}
