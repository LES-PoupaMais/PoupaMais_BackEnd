package br.com.poupamais.usuario.service.impl;

import br.com.poupamais.shared.security.SenhaAlteradaConsulta;
import br.com.poupamais.usuario.model.repositories.UsuarioRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação de {@link SenhaAlteradaConsulta} para o filtro JWT: uma consulta por requisição
 * autenticada; vazio = conta excluída (token recusado).
 */
@Component
@RequiredArgsConstructor
public class SessaoUsuarioConsulta implements SenhaAlteradaConsulta {

  private final UsuarioRepository repository;

  @Override
  @Transactional(readOnly = true)
  public Optional<SituacaoUsuario> situacao(Long idUsuario) {
    return repository.situacao(idUsuario);
  }
}
