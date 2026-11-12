package br.com.poupamais.usuario.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.poupamais.shared.security.SenhaAlteradaConsulta.SituacaoUsuario;
import br.com.poupamais.usuario.model.UsuarioRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessaoUsuarioConsultaTest {

  private final UsuarioRepository repository = mock(UsuarioRepository.class);
  private final SessaoUsuarioConsulta consulta = new SessaoUsuarioConsulta(repository);

  @Test
  void deveDevolverSituacaoDoUsuarioOuVazioSeNaoExiste() {
    SituacaoUsuario situacao = new SituacaoUsuario(Instant.parse("2026-11-12T10:00:00Z"));
    when(repository.situacao(1L)).thenReturn(Optional.of(situacao));
    when(repository.situacao(2L)).thenReturn(Optional.empty());

    assertThat(consulta.situacao(1L)).contains(situacao);
    assertThat(consulta.situacao(2L)).isEmpty();
  }
}
