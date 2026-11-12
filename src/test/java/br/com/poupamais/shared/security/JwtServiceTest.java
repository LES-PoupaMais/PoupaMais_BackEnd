package br.com.poupamais.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  static final String SEGREDO = "segredo-de-teste-com-mais-de-32-bytes-ok";
  private static final Instant AGORA = Instant.parse("2026-11-12T10:00:00.750Z");

  private final JwtService service =
      new JwtService(SEGREDO, 480, Clock.fixed(AGORA, ZoneOffset.UTC));

  @Test
  void deveGerarTokenValidoComIdDoUsuarioEExpiracao() {
    JwtService.TokenGerado gerado = service.gerar(42L);

    JwtService.TokenValido valido = service.validar(gerado.token()).orElseThrow();

    assertThat(valido.idUsuario()).isEqualTo(42L);
    assertThat(valido.emitidoEm()).isEqualTo(Instant.parse("2026-11-12T10:00:00Z"));
    assertThat(gerado.expiraEm()).isEqualTo(Instant.parse("2026-11-12T18:00:00Z"));
    assertThat(valido.expiraEm()).isEqualTo(gerado.expiraEm());
  }

  @Test
  void deveRecusarTokenExpirado() {
    String token = service.gerar(1L).token();
    JwtService depois =
        new JwtService(SEGREDO, 480, Clock.fixed(AGORA.plus(Duration.ofHours(9)), ZoneOffset.UTC));

    assertThat(depois.validar(token)).isEmpty();
  }

  @Test
  void deveRecusarTokenAssinadoComOutroSegredo() {
    JwtService outro =
        new JwtService(
            "outro-segredo-diferente-com-mais-de-32-bytes",
            480,
            Clock.fixed(AGORA, ZoneOffset.UTC));

    assertThat(service.validar(outro.gerar(1L).token())).isEmpty();
  }

  @Test
  void deveRecusarTokenMalformadoOuVazio() {
    assertThat(service.validar("isto.nao.eh-um-jwt")).isEmpty();
    assertThat(service.validar("abc")).isEmpty();
    assertThat(service.validar("")).isEmpty();
    assertThat(service.validar(null)).isEmpty();
  }

  @Test
  void deveRecusarTokenSemSubjectOuComSubjectNaoNumerico() {
    var chave = Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8));
    Date emitido = Date.from(AGORA);
    Date expira = Date.from(AGORA.plus(Duration.ofHours(1)));
    String semSubject =
        Jwts.builder().issuedAt(emitido).expiration(expira).signWith(chave).compact();
    String subjectTexto =
        Jwts.builder()
            .subject("abc")
            .issuedAt(emitido)
            .expiration(expira)
            .signWith(chave)
            .compact();

    assertThat(service.validar(semSubject)).isEmpty();
    assertThat(service.validar(subjectTexto)).isEmpty();
  }

  @Test
  void deveFalharNaInicializacaoComSegredoCurto() {
    assertThatThrownBy(() -> new JwtService("curto", 480))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("pelo menos 32 bytes");
    assertThatThrownBy(() -> new JwtService(null, 480)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void deveFalharNaInicializacaoComExpiracaoInvalida() {
    assertThatThrownBy(() -> new JwtService(SEGREDO, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_EXPIRACAO_MIN");
  }
}
