package br.com.poupamais.usuario.service.impl;

import br.com.poupamais.categoria.CategoriaFacade;
import br.com.poupamais.shared.exception.ConflitoException;
import br.com.poupamais.shared.exception.ErroAutenticacaoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.shared.security.JwtService;
import br.com.poupamais.usuario.model.Usuario;
import br.com.poupamais.usuario.model.UsuarioRepository;
import br.com.poupamais.usuario.service.AuthService;
import br.com.poupamais.usuario.service.RegrasConta;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  static final String MSG_EMAIL_DUPLICADO = "Este e-mail já está cadastrado";
  static final String MSG_ACEITE = "É preciso aceitar a Política de Privacidade";
  static final String MSG_LOGIN_INVALIDO = "E-mail ou senha inválidos";

  private final UsuarioRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final CategoriaFacade categoriaFacade;
  private final JwtService jwtService;
  private Clock relogio = Clock.systemUTC();

  void usarRelogio(Clock relogio) {
    this.relogio = relogio;
  }

  @Override
  @Transactional
  public Usuario cadastrar(String nome, String email, String senha, boolean aceiteTermos) {
    if (!aceiteTermos) {
      throw new RegraNegocioException(MSG_ACEITE);
    }
    String nomeNormalizado = RegrasConta.normalizarNome(nome);
    String emailNormalizado = RegrasConta.normalizarEmail(email);
    RegrasConta.validarSenha(senha);
    if (repository.existsByEmail(emailNormalizado)) {
      throw new ConflitoException(MSG_EMAIL_DUPLICADO);
    }
    Usuario usuario;
    try {
      usuario =
          repository.saveAndFlush(
              Usuario.builder()
                  .nome(nomeNormalizado)
                  .email(emailNormalizado)
                  .senha(passwordEncoder.encode(senha))
                  .aceiteTermosEm(relogio.instant())
                  .build());
    } catch (DataIntegrityViolationException e) {
      // Corrida no UNIQUE do e-mail entre a checagem e o insert
      throw new ConflitoException(MSG_EMAIL_DUPLICADO);
    }
    // RN002: mesma transação; se falhar, o cadastro inteiro é desfeito
    categoriaFacade.criarPadroes(usuario.getId());
    return usuario;
  }

  @Override
  @Transactional(readOnly = true)
  public Sessao login(String email, String senha) {
    Usuario usuario =
        repository
            .findByEmail(RegrasConta.normalizarEmail(email))
            .filter(u -> senha != null && passwordEncoder.matches(senha, u.getSenha()))
            .orElseThrow(() -> new ErroAutenticacaoException(MSG_LOGIN_INVALIDO));
    return new Sessao(jwtService.gerar(usuario.getId()), usuario);
  }
}
