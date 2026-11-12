package br.com.poupamais.usuario.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.poupamais.shared.exception.ErroAutenticacaoException;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.shared.security.JwtService;
import br.com.poupamais.shared.security.JwtService.TokenGerado;
import br.com.poupamais.usuario.model.FotoPerfil;
import br.com.poupamais.usuario.model.FotoPerfilRepository;
import br.com.poupamais.usuario.model.Usuario;
import br.com.poupamais.usuario.model.UsuarioRepository;
import br.com.poupamais.usuario.service.PerfilService.Perfil;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PerfilServiceImplTest {

  private static final Instant AGORA = Instant.parse("2026-11-12T10:00:00.900Z");
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

  private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
  private final FotoPerfilRepository fotos = mock(FotoPerfilRepository.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final JwtService jwtService = mock(JwtService.class);
  private final PerfilServiceImpl service =
      new PerfilServiceImpl(usuarios, fotos, encoder, jwtService);

  private Usuario ana;

  @BeforeEach
  void configurar() {
    service.usarRelogio(Clock.fixed(AGORA, ZoneOffset.UTC));
    ana =
        Usuario.builder()
            .id(1L)
            .nome("Ana")
            .email("ana@email.com")
            .senha(encoder.encode("senhaAtual"))
            .build();
    when(usuarios.findById(1L)).thenReturn(Optional.of(ana));
    when(usuarios.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void deveBuscarPerfilInformandoSeTemFoto() {
    when(fotos.existsById(1L)).thenReturn(true);

    Perfil perfil = service.buscar(1L);

    assertThat(perfil.usuario()).isSameAs(ana);
    assertThat(perfil.possuiFoto()).isTrue();
  }

  @Test
  void usuarioInexistenteDeveSerTratadoComoSessaoInvalida() {
    when(usuarios.findById(2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.buscar(2L)).isInstanceOf(ErroAutenticacaoException.class);
  }

  @Test
  void deveAtualizarNomeComTrim() {
    Perfil perfil = service.atualizarNome(1L, "  Ana Maria ");

    assertThat(perfil.usuario().getNome()).isEqualTo("Ana Maria");
    assertThat(perfil.usuario().getEmail()).isEqualTo("ana@email.com");
  }

  @Test
  void deveRecusarNomeVazio() {
    assertThatThrownBy(() -> service.atualizarNome(1L, " "))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("O nome é obrigatório");
  }

  @Test
  void deveTrocarSenhaGravarDataEmSegundosEDevolverTokenNovo() {
    TokenGerado novo = new TokenGerado("novo", AGORA.plusSeconds(60));
    when(jwtService.gerar(1L)).thenReturn(novo);

    TokenGerado token = service.trocarSenha(1L, "senhaAtual", "senhaNova");

    assertThat(token).isEqualTo(novo);
    assertThat(encoder.matches("senhaNova", ana.getSenha())).isTrue();
    assertThat(ana.getSenhaAlteradaEm()).isEqualTo("2026-11-12T10:00:00Z");
    verify(usuarios).save(ana);
  }

  @Test
  void deveRecusarTrocaComSenhaAtualErradaCom422() {
    assertThatThrownBy(() -> service.trocarSenha(1L, "errada", "senhaNova"))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("A senha atual está incorreta");
    assertThatThrownBy(() -> service.trocarSenha(1L, null, "senhaNova"))
        .isInstanceOf(RegraNegocioException.class);
    verify(usuarios, never()).save(any());
  }

  @Test
  void deveRecusarNovaSenhaIgualAAtual() {
    assertThatThrownBy(() -> service.trocarSenha(1L, "senhaAtual", "senhaAtual"))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("A nova senha deve ser diferente da atual");
  }

  @Test
  void deveRecusarNovaSenhaCurta() {
    assertThatThrownBy(() -> service.trocarSenha(1L, "senhaAtual", "123"))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("A senha deve ter entre 6 e 72 caracteres");
  }

  @Test
  void deveBuscarFotoOu404() {
    FotoPerfil foto = FotoPerfil.builder().idUsuario(1L).conteudo(PNG).build();
    when(fotos.findById(1L)).thenReturn(Optional.of(foto));
    when(fotos.findById(2L)).thenReturn(Optional.empty());

    assertThat(service.buscarFoto(1L)).isSameAs(foto);
    assertThatThrownBy(() -> service.buscarFoto(2L))
        .isInstanceOf(RecursoNaoEncontradoException.class)
        .hasMessage("Foto de perfil não encontrada");
  }

  @Test
  void deveSalvarFotoComTipoDetectadoPelosBytes() {
    when(fotos.findById(1L)).thenReturn(Optional.empty());

    service.salvarFoto(1L, PNG);

    ArgumentCaptor<FotoPerfil> captor = ArgumentCaptor.forClass(FotoPerfil.class);
    verify(fotos).save(captor.capture());
    assertThat(captor.getValue().getIdUsuario()).isEqualTo(1L);
    assertThat(captor.getValue().getTipoConteudo()).isEqualTo("image/png");
    assertThat(captor.getValue().getDataAtualizacao()).isEqualTo(AGORA);
  }

  @Test
  void deveSubstituirFotoExistente() {
    FotoPerfil existente =
        FotoPerfil.builder().idUsuario(1L).conteudo(new byte[] {1}).tipoConteudo("x").build();
    when(fotos.findById(1L)).thenReturn(Optional.of(existente));

    service.salvarFoto(1L, PNG);

    verify(fotos).save(existente);
    assertThat(existente.getConteudo()).isEqualTo(PNG);
  }

  @Test
  void deveAceitarFotoComExatamente2MB() {
    byte[] limite = Arrays.copyOf(PNG, 2 * 1024 * 1024);
    when(fotos.findById(1L)).thenReturn(Optional.empty());

    service.salvarFoto(1L, limite);

    verify(fotos).save(any());
  }

  @Test
  void deveRecusarFotoMaiorQue2MB() {
    byte[] grande = Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1);

    assertThatThrownBy(() -> service.salvarFoto(1L, grande))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("A foto deve ter no máximo 2 MB");
    verify(fotos, never()).save(any());
  }

  @Test
  void deveRecusarArquivoQueNaoEhImagemMesmoComNomeDeImagem() {
    assertThatThrownBy(() -> service.salvarFoto(1L, "GIF89a...".getBytes()))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("A foto deve ser uma imagem JPG, PNG ou WEBP");
    assertThatThrownBy(() -> service.salvarFoto(1L, null))
        .isInstanceOf(RegraNegocioException.class);
  }

  @Test
  void deveRemoverFotoSoSeExistir() {
    when(fotos.existsById(1L)).thenReturn(true, false);

    service.removerFoto(1L);
    service.removerFoto(1L);

    verify(fotos).deleteById(1L);
  }
}
