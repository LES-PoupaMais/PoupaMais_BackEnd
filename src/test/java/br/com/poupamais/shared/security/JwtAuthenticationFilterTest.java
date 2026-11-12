package br.com.poupamais.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

  private static final Instant EMISSAO = Instant.parse("2026-11-12T10:00:00Z");

  private final JwtService jwtService =
      new JwtService(JwtServiceTest.SEGREDO, 480, Clock.fixed(EMISSAO, ZoneOffset.UTC));

  @SuppressWarnings("unchecked")
  private final ObjectProvider<SenhaAlteradaConsulta> provider = mock(ObjectProvider.class);

  private final SenhaAlteradaConsulta consulta = mock(SenhaAlteradaConsulta.class);
  private final JwtAuthenticationFilter filtro = new JwtAuthenticationFilter(jwtService, provider);

  @AfterEach
  void limpar() {
    SecurityContextHolder.clearContext();
  }

  private MockFilterChain executar(String authorization) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/privado");
    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }
    MockFilterChain chain = new MockFilterChain();
    filtro.doFilter(request, new MockHttpServletResponse(), chain);
    return chain;
  }

  @Test
  void deveAutenticarComTokenValido() throws Exception {
    String token = jwtService.gerar(7L).token();

    MockFilterChain chain = executar("Bearer " + token);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(UsuarioLogado.id()).isEqualTo(7L);
  }

  @Test
  void deveSeguirSemAutenticarQuandoNaoHaHeader() throws Exception {
    MockFilterChain chain = executar(null);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(provider, never()).getIfAvailable();
  }

  @Test
  void deveIgnorarHeaderQueNaoEhBearer() throws Exception {
    executar("Basic dXN1YXJpbzpzZW5oYQ==");

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void deveSeguirSemAutenticarComTokenInvalido() throws Exception {
    MockFilterChain chain = executar("Bearer token-invalido");

    assertThat(chain.getRequest()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void deveRecusarTokenEmitidoAntesDaTrocaDeSenha() throws Exception {
    when(provider.getIfAvailable()).thenReturn(consulta);
    when(consulta.situacao(7L)).thenReturn(situacao(EMISSAO.plusSeconds(60)));

    executar("Bearer " + jwtService.gerar(7L).token());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void deveAceitarTokenEmitidoNoMesmoSegundoDaTrocaDeSenha() throws Exception {
    when(provider.getIfAvailable()).thenReturn(consulta);
    when(consulta.situacao(7L)).thenReturn(situacao(EMISSAO.plusMillis(400)));

    executar("Bearer " + jwtService.gerar(7L).token());

    assertThat(UsuarioLogado.id()).isEqualTo(7L);
  }

  @Test
  void deveAceitarTokenQuandoSenhaNuncaFoiTrocada() throws Exception {
    when(provider.getIfAvailable()).thenReturn(consulta);
    when(consulta.situacao(any())).thenReturn(situacao(null));

    executar("Bearer " + jwtService.gerar(7L).token());

    assertThat(UsuarioLogado.id()).isEqualTo(7L);
  }

  @Test
  void deveRecusarTokenDeUsuarioQueNaoExisteMais() throws Exception {
    when(provider.getIfAvailable()).thenReturn(consulta);
    when(consulta.situacao(7L)).thenReturn(Optional.empty());

    MockFilterChain chain = executar("Bearer " + jwtService.gerar(7L).token());

    assertThat(chain.getRequest()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void deveAceitarTokenEmitidoDepoisDaTrocaDeSenha() throws Exception {
    when(provider.getIfAvailable()).thenReturn(consulta);
    when(consulta.situacao(7L)).thenReturn(situacao(EMISSAO.minusSeconds(3600)));

    executar("Bearer " + jwtService.gerar(7L).token());

    assertThat(UsuarioLogado.id()).isEqualTo(7L);
  }

  private static Optional<SenhaAlteradaConsulta.SituacaoUsuario> situacao(Instant alteradaEm) {
    return Optional.of(new SenhaAlteradaConsulta.SituacaoUsuario(alteradaEm));
  }
}
