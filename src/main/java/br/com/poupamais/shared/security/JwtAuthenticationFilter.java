package br.com.poupamais.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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
  private boolean sessaoAindaValida(JwtService.TokenValido token) {
    SenhaAlteradaConsulta consulta = senhaAlteradaConsulta.getIfAvailable();
    if (consulta == null) {
      return true;
    }
    Optional<Instant> alteradaEm = consulta.senhaAlteradaEm(token.idUsuario());
    return alteradaEm
        .map(instante -> !token.emitidoEm().isBefore(instante.truncatedTo(ChronoUnit.SECONDS)))
        .orElse(true);
  }
}
