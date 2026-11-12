package br.com.poupamais.shared.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.poupamais.shared.exception.ErroAutenticacaoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class UsuarioLogadoTest {

  @AfterEach
  void limpar() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void deveLancarErroDeAutenticacaoSemUsuarioNoContexto() {
    assertThatThrownBy(UsuarioLogado::id)
        .isInstanceOf(ErroAutenticacaoException.class)
        .hasMessage("Sessão inválida ou expirada. Faça login novamente.");
  }

  @Test
  void deveLancarErroQuandoPrincipalNaoEhId() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("anonimo", null));

    assertThatThrownBy(UsuarioLogado::id).isInstanceOf(ErroAutenticacaoException.class);
  }
}
