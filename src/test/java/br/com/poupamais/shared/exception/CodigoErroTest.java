package br.com.poupamais.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CodigoErroTest {

  private static final Path TESTES = Path.of("src/test/java");

  @Test
  void todoCodigoEhMaiusculoComUnderscoreETemStatusEDescricao() {
    for (CodigoErro codigo : CodigoErro.values()) {
      assertThat(codigo.name()).matches("[A-Z]+(_[A-Z]+)*");
      assertThat(codigo.status()).as(codigo.name()).isBetween(400, 599);
      assertThat(codigo.quando()).as(codigo.name()).isNotBlank();
    }
  }

  @Test
  void deveDarOCodigoPadraoDeCadaStatusGenerico() {
    assertThat(CodigoErro.padraoDoStatus(400)).isEqualTo(CodigoErro.REQUISICAO_INVALIDA);
    assertThat(CodigoErro.padraoDoStatus(401)).isEqualTo(CodigoErro.NAO_AUTENTICADO);
    assertThat(CodigoErro.padraoDoStatus(403)).isEqualTo(CodigoErro.ACESSO_NEGADO);
    assertThat(CodigoErro.padraoDoStatus(404)).isEqualTo(CodigoErro.NAO_ENCONTRADO);
    assertThat(CodigoErro.padraoDoStatus(405)).isEqualTo(CodigoErro.METODO_NAO_PERMITIDO);
    assertThat(CodigoErro.padraoDoStatus(413)).isEqualTo(CodigoErro.ARQUIVO_GRANDE_DEMAIS);
    assertThat(CodigoErro.padraoDoStatus(415)).isEqualTo(CodigoErro.TIPO_CONTEUDO_NAO_SUPORTADO);
    assertThat(CodigoErro.padraoDoStatus(429)).isEqualTo(CodigoErro.REQUISICAO_INVALIDA);
    assertThat(CodigoErro.padraoDoStatus(500)).isEqualTo(CodigoErro.ERRO_INTERNO);
    assertThat(CodigoErro.padraoDoStatus(503)).isEqualTo(CodigoErro.ERRO_INTERNO);
  }

  /**
   * Regra da equipe: cada código é coberto por pelo menos um teste (conferindo {@code $.codigo} na
   * resposta ou o código da exceção). Código novo sem teste reprova aqui.
   */
  @Test
  void todoCodigoApareceEmPeloMenosUmTeste() {
    String outrosTestes = lerTestesMenosEste();

    List<String> semTeste =
        Arrays.stream(CodigoErro.values())
            .map(CodigoErro::name)
            .filter(nome ->
                !outrosTestes.contains("\"" + nome + "\"")
                    && !outrosTestes.contains("CodigoErro." + nome))
            .toList();

    assertThat(semTeste).as("códigos sem nenhum teste").isEmpty();
  }

  private static String lerTestesMenosEste() {
    try (Stream<Path> arquivos = Files.walk(TESTES)) {
      return arquivos
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> !p.getFileName().toString().equals("CodigoErroTest.java"))
          .map(CodigoErroTest::ler)
          .collect(Collectors.joining("\n"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String ler(Path arquivo) {
    try {
      return Files.readString(arquivo);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
