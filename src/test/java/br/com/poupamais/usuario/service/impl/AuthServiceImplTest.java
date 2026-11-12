package br.com.poupamais.usuario.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.poupamais.categoria.publico.CategoriaFacade;
import br.com.poupamais.shared.exception.ConflitoException;
import br.com.poupamais.shared.exception.ErroAutenticacaoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.shared.security.JwtService;
import br.com.poupamais.shared.security.JwtService.TokenGerado;
import br.com.poupamais.usuario.model.entidades.Usuario;
import br.com.poupamais.usuario.model.repositories.UsuarioRepository;
import br.com.poupamais.usuario.service.AuthService.Sessao;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceImplTest {

  private static final Instant AGORA = Instant.parse("2026-11-12T10:00:00Z");

  private final UsuarioRepository repository = mock(UsuarioRepository.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final CategoriaFacade categoriaFacade = mock(CategoriaFacade.class);
  private final JwtService jwtService = mock(JwtService.class);
  private final AuthServiceImpl service =
      new AuthServiceImpl(repository, encoder, categoriaFacade, jwtService);

  @BeforeEach
  void configurar() {
    service.usarRelogio(Clock.fixed(AGORA, ZoneOffset.UTC));
    when(repository.saveAndFlush(any()))
        .thenAnswer(inv -> {
          Usuario u = inv.getArgument(0);
          u.setId(1L);
          return u;
        });
  }

  @Test
  void deveCadastrarNormalizandoDadosComHashECategoriasPadrao() {
    Usuario criado = service.cadastrar("  Ana  ", " Ana@Email.COM ", "123456", true);

    ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
    verify(repository).saveAndFlush(captor.capture());
    Usuario salvo = captor.getValue();
    assertThat(criado.getId()).isEqualTo(1L);
    assertThat(salvo.getNome()).isEqualTo("Ana");
    assertThat(salvo.getEmail()).isEqualTo("ana@email.com");
    assertThat(salvo.getSenha()).startsWith("$2").isNotEqualTo("123456");
    assertThat(encoder.matches("123456", salvo.getSenha())).isTrue();
    assertThat(salvo.getAceiteTermosEm()).isEqualTo(AGORA);
    verify(categoriaFacade).criarPadroes(1L);
  }

  @Test
  void deveRecusarCadastroSemAceiteDosTermos() {
    assertThatThrownBy(() -> service.cadastrar("Ana", "ana@email.com", "123456", false))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("É preciso aceitar a Política de Privacidade");
    verifyNoInteractions(repository, categoriaFacade);
  }

  @Test
  void deveRecusarSenhaCurtaNoCadastro() {
    assertThatThrownBy(() -> service.cadastrar("Ana", "ana@email.com", "12345", true))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("A senha deve ter entre 6 e 72 caracteres");
  }

  @Test
  void deveRecusarEmailJaCadastradoSemDiferenciarMaiusculas() {
    when(repository.existsByEmail("ana@email.com")).thenReturn(true);

    assertThatThrownBy(() -> service.cadastrar("Ana", "ANA@email.com", "123456", true))
        .isInstanceOf(ConflitoException.class)
        .hasMessage("Este e-mail já está cadastrado");
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void deveConverterCorridaNoEmailUnicoEm409() {
    doThrow(new DataIntegrityViolationException("uk")).when(repository).saveAndFlush(any());

    assertThatThrownBy(() -> service.cadastrar("Ana", "ana@email.com", "123456", true))
        .isInstanceOf(ConflitoException.class);
    verifyNoInteractions(categoriaFacade);
  }

  @Test
  void deveFazerLoginComEmailEmQualquerCaixa() {
    Usuario ana = usuario();
    when(repository.findByEmail("ana@email.com")).thenReturn(Optional.of(ana));
    TokenGerado token = new TokenGerado("jwt", AGORA.plusSeconds(60));
    when(jwtService.gerar(1L)).thenReturn(token);

    Sessao sessao = service.login(" ANA@Email.com ", "123456");

    assertThat(sessao.token()).isEqualTo(token);
    assertThat(sessao.usuario()).isSameAs(ana);
  }

  @Test
  void deveRecusarLoginComSenhaErradaOuEmailInexistenteComMesmaMensagem() {
    when(repository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario()));
    when(repository.findByEmail("outro@email.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login("ana@email.com", "errada"))
        .isInstanceOf(ErroAutenticacaoException.class)
        .hasMessage("E-mail ou senha inválidos");
    assertThatThrownBy(() -> service.login("outro@email.com", "123456"))
        .isInstanceOf(ErroAutenticacaoException.class)
        .hasMessage("E-mail ou senha inválidos");
    assertThatThrownBy(() -> service.login("ana@email.com", null))
        .isInstanceOf(ErroAutenticacaoException.class);
    verifyNoInteractions(jwtService);
  }

  private Usuario usuario() {
    return Usuario.builder()
        .id(1L)
        .nome("Ana")
        .email("ana@email.com")
        .senha(encoder.encode("123456"))
        .build();
  }
}
