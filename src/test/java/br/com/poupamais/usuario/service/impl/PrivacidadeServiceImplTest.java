package br.com.poupamais.usuario.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.poupamais.shared.exception.ErroAutenticacaoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.usuario.model.Usuario;
import br.com.poupamais.usuario.model.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PrivacidadeServiceImplTest {

  private final UsuarioRepository repository = mock(UsuarioRepository.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final PrivacidadeServiceImpl service = new PrivacidadeServiceImpl(repository, encoder);

  private final Usuario ana =
      Usuario.builder().id(1L).nome("Ana").senha(encoder.encode("123456")).build();

  @Test
  void deveExcluirContaComSenhaCorreta() {
    when(repository.findById(1L)).thenReturn(Optional.of(ana));

    service.excluirConta(1L, "123456");

    verify(repository).delete(ana);
  }

  @Test
  void deveRecusarExclusaoComSenhaErradaCom422() {
    when(repository.findById(1L)).thenReturn(Optional.of(ana));

    assertThatThrownBy(() -> service.excluirConta(1L, "errada"))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Senha incorreta");
    assertThatThrownBy(() -> service.excluirConta(1L, null))
        .isInstanceOf(RegraNegocioException.class);
    verify(repository, never()).delete(any());
  }

  @Test
  void usuarioInexistenteDeveSerSessaoInvalida() {
    when(repository.findById(2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.excluirConta(2L, "123456"))
        .isInstanceOf(ErroAutenticacaoException.class);
  }
}
