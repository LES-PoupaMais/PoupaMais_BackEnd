package br.com.poupamais.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.poupamais.shared.exception.RegraNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RegrasContaTest {

  @Test
  void deveNormalizarEmailComTrimEMinusculas() {
    assertThat(RegrasConta.normalizarEmail("  Ana.Silva@Email.COM "))
        .isEqualTo("ana.silva@email.com");
    assertThat(RegrasConta.normalizarEmail(null)).isEmpty();
  }

  @Test
  void deveApararNomeEAceitarAte150Caracteres() {
    assertThat(RegrasConta.normalizarNome("  Ana  ")).isEqualTo("Ana");
    assertThat(RegrasConta.normalizarNome("a".repeat(150))).hasSize(150);
  }

  @Test
  void deveRecusarNomeVazioOuMaiorQue150() {
    assertThatThrownBy(() -> RegrasConta.normalizarNome("   "))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("O nome é obrigatório");
    assertThatThrownBy(() -> RegrasConta.normalizarNome(null))
        .isInstanceOf(RegraNegocioException.class);
    assertThatThrownBy(() -> RegrasConta.normalizarNome("a".repeat(151)))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("O nome deve ter no máximo 150 caracteres");
  }

  @Test
  void deveAceitarSenhaEntre6E72Caracteres() {
    assertThatCode(() -> RegrasConta.validarSenha("123456")).doesNotThrowAnyException();
    assertThatCode(() -> RegrasConta.validarSenha("a".repeat(72))).doesNotThrowAnyException();
  }

  @Test
  void deveRecusarSenhaForaDosLimites() {
    for (String senha : new String[] {null, "12345", "a".repeat(73)}) {
      assertThatThrownBy(() -> RegrasConta.validarSenha(senha))
          .isInstanceOf(RegraNegocioException.class)
          .hasMessage("A senha deve ter entre 6 e 72 caracteres");
    }
  }

  @Test
  void agoraEmSegundosDeveTruncarMilissegundos() {
    Clock relogio = Clock.fixed(Instant.parse("2026-11-12T10:00:00.789Z"), ZoneOffset.UTC);

    assertThat(RegrasConta.agoraEmSegundos(relogio)).isEqualTo("2026-11-12T10:00:00Z");
  }
}
