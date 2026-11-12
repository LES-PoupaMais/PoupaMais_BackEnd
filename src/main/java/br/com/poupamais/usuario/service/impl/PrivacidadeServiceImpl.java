package br.com.poupamais.usuario.service.impl;

import br.com.poupamais.shared.exception.ErroAutenticacaoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.usuario.model.entidades.Usuario;
import br.com.poupamais.usuario.model.repositories.UsuarioRepository;
import br.com.poupamais.usuario.service.PrivacidadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrivacidadeServiceImpl implements PrivacidadeService {

  static final String MSG_SENHA_INCORRETA = "Senha incorreta";

  private final UsuarioRepository repository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public void excluirConta(Long idUsuario, String senha) {
    Usuario usuario =
        repository
            .findById(idUsuario)
            .orElseThrow(() -> new ErroAutenticacaoException(PerfilServiceImpl.MSG_SESSAO));
    if (senha == null || !passwordEncoder.matches(senha, usuario.getSenha())) {
      throw new RegraNegocioException(MSG_SENHA_INCORRETA);
    }
    // RN016: DELETE físico; foto, tokens, categorias e lançamentos caem em cascata no banco
    repository.delete(usuario);
  }
}
