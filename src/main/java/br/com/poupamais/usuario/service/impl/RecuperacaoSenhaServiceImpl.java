package br.com.poupamais.usuario.service.impl;

import br.com.poupamais.notificacao.EmailFacade;
import br.com.poupamais.notificacao.EnvioEmailException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.usuario.model.TokenRecuperacaoSenha;
import br.com.poupamais.usuario.model.TokenRecuperacaoSenhaRepository;
import br.com.poupamais.usuario.model.Usuario;
import br.com.poupamais.usuario.model.UsuarioRepository;
import br.com.poupamais.usuario.service.RecuperacaoSenhaService;
import br.com.poupamais.usuario.service.RegrasConta;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecuperacaoSenhaServiceImpl implements RecuperacaoSenhaService {

  static final Duration VALIDADE = Duration.ofMinutes(30);
  static final int BYTES_TOKEN = 32;
  static final String MSG_TOKEN_INVALIDO =
      "Link de redefinição inválido ou expirado. Solicite um novo.";

  private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaServiceImpl.class);
  private static final SecureRandom ALEATORIO = new SecureRandom();

  private final UsuarioRepository usuarioRepository;
  private final TokenRecuperacaoSenhaRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailFacade emailFacade;
  private final String frontUrl;
  private Clock relogio = Clock.systemUTC();

  public RecuperacaoSenhaServiceImpl(
      UsuarioRepository usuarioRepository,
      TokenRecuperacaoSenhaRepository tokenRepository,
      PasswordEncoder passwordEncoder,
      EmailFacade emailFacade,
      @Value("${poupamais.front-url}") String frontUrl) {
    this.usuarioRepository = usuarioRepository;
    this.tokenRepository = tokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.emailFacade = emailFacade;
    this.frontUrl =
        frontUrl.endsWith("/") ? frontUrl.substring(0, frontUrl.length() - 1) : frontUrl;
  }

  void usarRelogio(Clock relogio) {
    this.relogio = relogio;
  }

  @Override
  @Transactional
  public void solicitar(String email) {
    Optional<Usuario> encontrado =
        usuarioRepository.findByEmail(RegrasConta.normalizarEmail(email));
    if (encontrado.isEmpty()) {
      return; // RN014: não revela se o e-mail existe
    }
    Usuario usuario = encontrado.get();
    Instant agora = relogio.instant();
    tokenRepository.invalidarPendentes(usuario.getId(), agora);

    byte[] bytes = new byte[BYTES_TOKEN];
    ALEATORIO.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    tokenRepository.save(
        TokenRecuperacaoSenha.builder()
            .idUsuario(usuario.getId())
            .tokenHash(hash(token))
            .expiraEm(agora.plus(VALIDADE))
            .dataCadastro(agora)
            .build());

    try {
      emailFacade.enviarRecuperacaoSenha(
          usuario.getEmail(), usuario.getNome(), frontUrl + "/redefinir-senha?token=" + token);
    } catch (EnvioEmailException e) {
      // RN014: resposta igual mesmo com falha; sem e-mail, link ou token no log (RNF019)
      log.warn("Falha ao enviar o e-mail de recuperação de senha: {}", e.getMessage());
    }
  }

  @Override
  @Transactional
  public void redefinir(String token, String novaSenha) {
    Instant agora = relogio.instant();
    TokenRecuperacaoSenha registro =
        Optional.ofNullable(token)
            .filter(t -> !t.isBlank())
            .flatMap(t -> tokenRepository.findByTokenHash(hash(t.strip())))
            .filter(t -> t.valido(agora))
            .orElseThrow(() -> new RegraNegocioException(MSG_TOKEN_INVALIDO));
    RegrasConta.validarSenha(novaSenha);
    // Uso único: marca este e qualquer outro token pendente do usuário como usado
    tokenRepository.invalidarPendentes(registro.getIdUsuario(), agora);
    Usuario usuario =
        usuarioRepository
            .findById(registro.getIdUsuario())
            .orElseThrow(() -> new RegraNegocioException(MSG_TOKEN_INVALIDO));
    usuario.setSenha(passwordEncoder.encode(novaSenha));
    // RN014: derruba todas as sessões abertas
    usuario.setSenhaAlteradaEm(RegrasConta.agoraEmSegundos(relogio));
    usuarioRepository.save(usuario);
  }

  /** SHA-256 em hex (64 caracteres), o que fica no banco. */
  static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponível", e);
    }
  }
}
