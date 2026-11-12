package br.com.poupamais.usuario.service;

import java.util.Optional;

/** Detecta o tipo da imagem pelos bytes (assinatura), não pela extensão nem pelo header (RN015). */
public final class TipoFoto {

  public static final int TAMANHO_MAXIMO = 2 * 1024 * 1024;

  private TipoFoto() {}

  public static Optional<String> detectar(byte[] b) {
    if (b == null) {
      return Optional.empty();
    }
    if (b.length >= 3 && u(b[0]) == 0xFF && u(b[1]) == 0xD8 && u(b[2]) == 0xFF) {
      return Optional.of("image/jpeg");
    }
    if (b.length >= 4 && u(b[0]) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
      return Optional.of("image/png");
    }
    if (b.length >= 12
        && b[0] == 'R'
        && b[1] == 'I'
        && b[2] == 'F'
        && b[3] == 'F'
        && b[8] == 'W'
        && b[9] == 'E'
        && b[10] == 'B'
        && b[11] == 'P') {
      return Optional.of("image/webp");
    }
    return Optional.empty();
  }

  private static int u(byte b) {
    return b & 0xFF;
  }
}
