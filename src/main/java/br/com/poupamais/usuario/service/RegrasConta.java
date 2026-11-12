package br.com.poupamais.usuario.service;

import br.com.poupamais.shared.exception.RegraNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Normalizações e validações da conta (RN011, RN015) num só lugar, usadas pelos services. */
public final class RegrasConta {

  public static final int NOME_MAXIMO = 150;
  public static final int SENHA_MINIMA = 6;
  // Limite do BCrypt: bytes além de 72 são ignorados
  public static final int SENHA_MAXIMA = 72;

  public static final String MSG_NOME_OBRIGATORIO = "O nome é obrigatório";
  public static final String MSG_NOME_LONGO = "O nome deve ter no máximo 150 caracteres";
  public static final String MSG_SENHA_TAMANHO = "A senha deve ter entre 6 e 72 caracteres";

  private RegrasConta() {}

  /** E-mail sempre gravado e comparado em minúsculas: o UNIQUE do banco diferencia maiúsculas. */
  public static String normalizarEmail(String email) {
    return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
  }

  public static String normalizarNome(String nome) {
    String normalizado = nome == null ? "" : nome.strip();
    if (normalizado.isEmpty()) {
      throw new RegraNegocioException(MSG_NOME_OBRIGATORIO);
    }
    if (normalizado.length() > NOME_MAXIMO) {
      throw new RegraNegocioException(MSG_NOME_LONGO);
    }
    return normalizado;
  }

  public static void validarSenha(String senha) {
    if (senha == null || senha.length() < SENHA_MINIMA || senha.length() > SENHA_MAXIMA) {
      throw new RegraNegocioException(MSG_SENHA_TAMANHO);
    }
  }

  /** Agora truncado em segundos, igual ao {@code iat} do JWT (comparação do filtro). */
  public static Instant agoraEmSegundos(Clock relogio) {
    return relogio.instant().truncatedTo(ChronoUnit.SECONDS);
  }
}
