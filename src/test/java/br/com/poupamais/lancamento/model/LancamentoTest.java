package br.com.poupamais.lancamento.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.poupamais.lancamento.StatusLancamento;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LancamentoTest {

  private static Lancamento comStatus(StatusLancamento status) {
    return Lancamento.builder().id(1L).idUsuario(9L).status(status).build();
  }

  @Test
  void deveNascerAtivoSemDataDeAtualizacao() {
    Lancamento lancamento = Lancamento.builder().build();

    assertThat(lancamento.getStatus()).isEqualTo(StatusLancamento.ATIVO);
    assertThat(lancamento.getDataAtualizacao()).isNull();
  }

  @Test
  void deveAlterarStatusValidoEMarcarAtualizacao() {
    Lancamento lancamento = comStatus(StatusLancamento.ATIVO);

    lancamento.alterarStatus(StatusLancamento.INATIVO);

    assertThat(lancamento.getStatus()).isEqualTo(StatusLancamento.INATIVO);
    assertThat(lancamento.getDataAtualizacao()).isEqualTo(LocalDate.now());
  }

  @Test
  void deveRecusarTransicaoInvalidaSemAlterarNada() {
    Lancamento lancamento = comStatus(StatusLancamento.DELETADO);

    assertThatThrownBy(() -> lancamento.alterarStatus(StatusLancamento.ATIVO))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Não é possível alterar o status de DELETADO para ATIVO");
    assertThat(lancamento.getStatus()).isEqualTo(StatusLancamento.DELETADO);
    assertThat(lancamento.getDataAtualizacao()).isNull();
  }

  @Test
  void deveRecusarTransicaoParaNuloOuSemStatusAtual() {
    assertThatThrownBy(() -> comStatus(StatusLancamento.ATIVO).alterarStatus(null))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessageContaining("para vazio");
    assertThatThrownBy(() -> comStatus(null).alterarStatus(StatusLancamento.ATIVO))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessageContaining("de vazio");
  }

  @Test
  void devePreencherDataDeCadastroAoInserirSemSobrescrever() {
    Lancamento novo = comStatus(StatusLancamento.ATIVO);
    Lancamento antigo = comStatus(StatusLancamento.ATIVO);
    antigo.setDataCadastro(LocalDate.of(2026, 1, 1));

    novo.antesDeInserir();
    antigo.antesDeInserir();

    assertThat(novo.getDataCadastro()).isEqualTo(LocalDate.now());
    assertThat(antigo.getDataCadastro()).isEqualTo(LocalDate.of(2026, 1, 1));
  }

  @Test
  void deveDefinirCategoriasSemRepetirEComIdUsuario() {
    Lancamento lancamento = comStatus(StatusLancamento.ATIVO);

    lancamento.definirCategorias(List.of(5L, 2L, 5L));

    assertThat(lancamento.idsCategorias()).containsExactly(2L, 5L);
    assertThat(lancamento.getCategorias())
        .allSatisfy(c -> {
          assertThat(c.getIdUsuario()).isEqualTo(9L);
          assertThat(c.getLancamento()).isSameAs(lancamento);
        });
  }

  @Test
  void deveTrocarCategoriasMantendoAsQueContinuam() {
    Lancamento lancamento = comStatus(StatusLancamento.ATIVO);
    lancamento.definirCategorias(List.of(1L, 2L));
    CategoriaLancamento mantida = lancamento.getCategorias().get(1);

    lancamento.definirCategorias(List.of(2L, 3L));

    assertThat(lancamento.idsCategorias()).containsExactly(2L, 3L);
    assertThat(lancamento.getCategorias()).contains(mantida);
  }

  @Test
  void chaveDaAssociativaComparaPelosDoisIds() {
    assertThat(new CategoriaLancamentoId(1L, 2L))
        .isEqualTo(new CategoriaLancamentoId(1L, 2L))
        .hasSameHashCodeAs(new CategoriaLancamentoId(1L, 2L))
        .isNotEqualTo(new CategoriaLancamentoId(1L, 3L))
        .isNotEqualTo(new CategoriaLancamentoId(4L, 2L))
        .isNotEqualTo("outro");
  }
}
