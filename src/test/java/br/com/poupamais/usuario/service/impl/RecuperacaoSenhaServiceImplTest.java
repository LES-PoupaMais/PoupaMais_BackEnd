package br.com.poupamais.usuario.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.poupamais.notificacao.EmailFacade;
import br.com.poupamais.notificacao.EnvioEmailException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.usuario.model.TokenRecuperacaoSenha;
import br.com.poupamais.usuario.model.TokenRecuperacaoSenhaRepository;
import br.com.poupamais.usuario.model.Usuario;
import br.com.poupamais.usuario.model.UsuarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class RecuperacaoSenhaServiceImplTest {

  private static final Instant AGORA = Instant.parse("2026-11-12T10:00:00.500Z");

  private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
  private final TokenRecuperacaoSenhaRepository tokens =
      mock(TokenRecuperacaoSenhaRepository.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final EmailFacade email = mock(EmailFacade.class);
  private final RecuperacaoSenhaServiceImpl service =
      new RecuperacaoSenhaServiceImpl(usuarios, tokens, encoder, email, "http://front:3000/");

  private final Usuario ana =
      Usuario.builder().id(1L).nome("Ana").email("ana@email.com").senha("hash-antigo").build();

  @BeforeEach
  void relogio() {
    service.usarRelogio(Clock.fixed(AGORA, ZoneOffset.UTC));
  }

  @Test
  void deveGerarTokenGuardarSoOHashEEnviarLink() {
    when(usuarios.findByEmail("ana@email.com")).thenReturn(Optional.of(ana));

    service.solicitar(" ANA@email.com ");

    ArgumentCaptor<TokenRecuperacaoSenha> salvo =
        ArgumentCaptor.forClass(TokenRecuperacaoSenha.class);
    ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
    verify(tokens).invalidarPendentes(1L, AGORA);
    verify(tokens).save(salvo.capture());
    verify(email).enviarRecuperacaoSenha(eq("ana@email.com"), eq("Ana"), link.capture());
    assertThat(link.getValue()).startsWith("http://front:3000/redefinir-senha?token=");
    String token = link.getValue().substring(link.getValue().indexOf('=') + 1);
    assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    assertThat(salvo.getValue().getTokenHash())
        .hasSize(64)
        .isEqualTo(RecuperacaoSenhaServiceImpl.hash(token))
        .doesNotContain(token);
    assertThat(salvo.getValue().getExpiraEm()).isEqualTo(AGORA.plusSeconds(30 * 60));
    assertThat(salvo.getValue().getIdUsuario()).isEqualTo(1L);
  }

  @Test
  void naoDeveFazerNadaQuandoEmailNaoExiste() {
    when(usuarios.findByEmail(anyString())).thenReturn(Optional.empty());

    assertThatCode(() -> service.solicitar("ninguem@email.com")).doesNotThrowAnyException();

    verifyNoInteractions(tokens, email);
  }

  @Test
  void falhaNoEnvioDeEmailNaoDeveMudarAResposta() {
    when(usuarios.findByEmail("ana@email.com")).thenReturn(Optional.of(ana));
    doThrow(new EnvioEmailException("Falha", null))
        .when(email)
        .enviarRecuperacaoSenha(anyString(), anyString(), anyString());

    assertThatCode(() -> service.solicitar("ana@email.com")).doesNotThrowAnyException();

    verify(tokens).save(any());
  }

  @Test
  void deveRedefinirSenhaMarcarTokenUsadoEDerrubarSessoes() {
    TokenRecuperacaoSenha registro = token(AGORA.plusSeconds(60), null);
    when(tokens.findByTokenHash(RecuperacaoSenhaServiceImpl.hash("abc")))
        .thenReturn(Optional.of(registro));
    when(usuarios.findById(1L)).thenReturn(Optional.of(ana));

    service.redefinir("abc", "novaSenha");

    verify(tokens).invalidarPendentes(1L, AGORA);
    verify(usuarios).save(ana);
    assertThat(encoder.matches("novaSenha", ana.getSenha())).isTrue();
    assertThat(ana.getSenhaAlteradaEm()).isEqualTo("2026-11-12T10:00:00Z");
  }

  @Test
  void deveRecusarTokenExpirado() {
    when(tokens.findByTokenHash(anyString()))
        .thenReturn(Optional.of(token(AGORA.minusMillis(1), null)));

    assertInvalido("abc");
  }

  @Test
  void deveRecusarTokenExatamenteNoInstanteDeExpiracao() {
    when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token(AGORA, null)));

    assertInvalido("abc");
  }

  @Test
  void deveRecusarTokenJaUsado() {
    when(tokens.findByTokenHash(anyString()))
        .thenReturn(Optional.of(token(AGORA.plusSeconds(60), AGORA.minusSeconds(5))));

    assertInvalido("abc");
  }

  @Test
  void deveRecusarTokenInexistenteOuVazio() {
    when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

    assertInvalido("nao-existe");
    assertInvalido("  ");
    assertInvalido(null);
  }

  @Test
  void deveRecusarNovaSenhaCurtaSemGastarOToken() {
    when(tokens.findByTokenHash(anyString()))
        .thenReturn(Optional.of(token(AGORA.plusSeconds(60), null)));

    assertThatThrownBy(() -> service.redefinir("abc", "123"))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("A senha deve ter entre 6 e 72 caracteres");
    verify(tokens, never()).invalidarPendentes(any(), any());
  }

  @Test
  void deveRecusarQuandoUsuarioDoTokenNaoExisteMais() {
    when(tokens.findByTokenHash(anyString()))
        .thenReturn(Optional.of(token(AGORA.plusSeconds(60), null)));
    when(usuarios.findById(1L)).thenReturn(Optional.empty());

    assertInvalido("abc");
  }

  @Test
  void hashDeveSerSha256EmHex() {
    assertThat(RecuperacaoSenhaServiceImpl.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  private void assertInvalido(String token) {
    assertThatThrownBy(() -> service.redefinir(token, "novaSenha"))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Link de redefinição inválido ou expirado. Solicite um novo.");
    verify(usuarios, never()).save(any());
  }

  private static TokenRecuperacaoSenha token(Instant expiraEm, Instant usadoEm) {
    return TokenRecuperacaoSenha.builder()
        .id(10L)
        .idUsuario(1L)
        .tokenHash("h")
        .expiraEm(expiraEm)
        .usadoEm(usadoEm)
        .build();
  }
}
