package br.com.poupamais.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TipoFotoTest {

  static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
  static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
  static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);

  @Test
  void deveDetectarJpegPngEWebpPelosBytes() {
    assertThat(TipoFoto.detectar(JPEG)).contains("image/jpeg");
    assertThat(TipoFoto.detectar(PNG)).contains("image/png");
    assertThat(TipoFoto.detectar(WEBP)).contains("image/webp");
  }

  @Test
  void deveRecusarOutrosConteudos() {
    assertThat(TipoFoto.detectar(null)).isEmpty();
    assertThat(TipoFoto.detectar(new byte[0])).isEmpty();
    assertThat(TipoFoto.detectar("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
    assertThat(TipoFoto.detectar("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1)))
        .isEmpty();
    assertThat(TipoFoto.detectar(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
    assertThat(TipoFoto.detectar(new byte[] {(byte) 0x89, 'P', 'N', 'X'})).isEmpty();
    assertThat(TipoFoto.detectar("RIFF".getBytes(StandardCharsets.US_ASCII))).isEmpty();
  }
}
