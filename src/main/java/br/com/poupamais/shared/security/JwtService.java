package br.com.poupamais.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Gera e valida os tokens JWT (HS256) da API. O subject é o id do usuário; {@code iat} e {@code
 * exp} vêm do relógio. Segredo em {@code poupamais.jwt.segredo} (JWT_SECRET, mínimo 32 bytes) e
 * validade em {@code poupamais.jwt.expiracao-min} (JWT_EXPIRACAO_MIN, RN011).
 */
@Service
public class JwtService {

  static final int TAMANHO_MINIMO_SEGREDO = 32;

  private final SecretKey chave;
  private final Duration validade;
  private final Clock relogio;

  @Autowired
  public JwtService(
      @Value("${poupamais.jwt.segredo}") String segredo,
      @Value("${poupamais.jwt.expiracao-min}") long expiracaoMin) {
    this(segredo, expiracaoMin, Clock.systemUTC());
  }

  JwtService(String segredo, long expiracaoMin, Clock relogio) {
    byte[] bytes = segredo == null ? new byte[0] : segredo.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < TAMANHO_MINIMO_SEGREDO) {
      throw new IllegalStateException(
          "JWT_SECRET (poupamais.jwt.segredo) precisa ter pelo menos "
              + TAMANHO_MINIMO_SEGREDO
              + " bytes para assinar tokens HS256.");
    }
    if (expiracaoMin <= 0) {
      throw new IllegalStateException(
          "JWT_EXPIRACAO_MIN (poupamais.jwt.expiracao-min) deve ser maior que zero.");
    }
    this.chave = Keys.hmacShaKeyFor(bytes);
    this.validade = Duration.ofMinutes(expiracaoMin);
    this.relogio = relogio;
  }

  /** Gera um token para o usuário. {@code expiraEm} é o que o login devolve ao front. */
  public TokenGerado gerar(Long idUsuario) {
    // JWT guarda datas em segundos: trunca para que expiraEm bata com o claim exp
    Instant agora = relogio.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    Instant expiraEm = agora.plus(validade);
    String token =
        Jwts.builder()
            .subject(String.valueOf(idUsuario))
            .issuedAt(Date.from(agora))
            .expiration(Date.from(expiraEm))
            .signWith(chave)
            .compact();
    return new TokenGerado(token, expiraEm);
  }

  /**
   * Valida assinatura, expiração e formato. Devolve vazio para qualquer token inválido (nunca lança
   * nem loga o token).
   */
  public Optional<TokenValido> validar(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(chave)
              .clock(() -> Date.from(relogio.instant()))
              .build()
              .parseSignedClaims(token)
              .getPayload();
      if (claims.getSubject() == null || claims.getIssuedAt() == null) {
        return Optional.empty();
      }
      return Optional.of(
          new TokenValido(
              Long.valueOf(claims.getSubject()),
              claims.getIssuedAt().toInstant(),
              claims.getExpiration().toInstant()));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Token recém-gerado e o instante em que expira. */
  public record TokenGerado(String token, Instant expiraEm) {}

  /** Dados confiáveis extraídos de um token válido. */
  public record TokenValido(Long idUsuario, Instant emitidoEm, Instant expiraEm) {}
}
