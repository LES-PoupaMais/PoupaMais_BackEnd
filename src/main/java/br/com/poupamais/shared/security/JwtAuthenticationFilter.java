package br.com.poupamais.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lê {@code Authorization: Bearer <token>}, valida e autentica o usuário (principal = id do
 * usuário). Token ausente ou inválido segue sem autenticação; rota privada então responde 401 pelo
 * entry point. Não é {@code @Component} para não ser registrado duas vezes como filtro do servlet.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String PREFIXO = "Bearer ";

  private final JwtService jwtService;
  private final ObjectProvider<SenhaAlteradaConsulta> senhaAlteradaConsulta;

  public JwtAuthenticationFilter(
      JwtService jwtService, ObjectProvider<SenhaAlteradaConsulta> senhaAlteradaConsulta) {
    this.jwtService = jwtService;
    this.senhaAlteradaConsulta = senhaAlteradaConsulta;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null
        && header.startsWith(PREFIXO)
        && SecurityContextHolder.getContext().getAuthentication() == null) {
      jwtService
          .validar(header.substring(PREFIXO.length()).trim())
          .filter(this::sessaoAindaValida)
          .ifPresent(token ->
              SecurityContextHolder.getContext()
                  .setAuthentication(
                      UsernamePasswordAuthenticationToken.authenticated(
                          token.idUsuario(), null, List.of())));
    }
    chain.doFilter(request, response);
  }

  // RN014/RN015: token emitido antes da última troca de senha não vale mais.
  // iat tem precisão de segundos, por isso compara com senha_alterada_em truncado: o token novo
  // devolvido na troca de senha (mesmo segundo) continua válido.
  // RF017: token de usuário que não existe mais (conta excluída) é recusado.
  private boolean sessaoAindaValida(JwtService.TokenValido token) {
    SenhaAlteradaConsulta consulta = senhaAlteradaConsulta.getIfAvailable();
    if (consulta == null) {
      return true;
    }
    return consulta
        .situacao(token.idUsuario())
        .map(situacao -> emitidoDepoisDaTroca(token, situacao.senhaAlteradaEm()))
        .orElse(false);
  }

  private static boolean emitidoDepoisDaTroca(JwtService.TokenValido token, Instant alteradaEm) {
    return alteradaEm == null
        || !token.emitidoEm().isBefore(alteradaEm.truncatedTo(ChronoUnit.SECONDS));
  }
}
