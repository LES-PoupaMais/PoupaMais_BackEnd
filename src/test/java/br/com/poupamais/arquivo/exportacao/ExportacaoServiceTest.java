package br.com.poupamais.arquivo.exportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.poupamais.categoria.CategoriaResumo;
import br.com.poupamais.lancamento.FiltroLancamento;
import br.com.poupamais.lancamento.LancamentoFacade;
import br.com.poupamais.lancamento.LancamentoResumo;
import br.com.poupamais.lancamento.StatusLancamento;
import br.com.poupamais.lancamento.TipoLancamento;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportacaoServiceTest {

  private static final Long USUARIO = 7L;
  private static final FiltroLancamento FILTRO =
      new FiltroLancamento(null, 8, 2026, null, StatusLancamento.DELETADO, null);

  @Mock private LancamentoFacade lancamentos;
  private ExportacaoService service;

  @BeforeEach
  void preparar() {
    service =
        new ExportacaoService(
            lancamentos, new ExportadorFactory(List.of(new ExportadorCsv(), new ExportadorXlsx())));
    // 02:00 UTC de 13/11 ainda é 12/11 em São Paulo
    service.usarRelogio(
        Clock.fixed(Instant.parse("2026-11-13T02:00:00Z"), ZoneId.of("America/Sao_Paulo")));
  }

  @Test
  void deveExportarComOsFiltrosRecebidosENomeComData() {
    when(lancamentos.listar(USUARIO, FILTRO))
        .thenReturn(
            List.of(
                new LancamentoResumo(
                    1L,
                    "Pão",
                    TipoLancamento.DESPESA,
                    8,
                    2026,
                    BigDecimal.TEN,
                    StatusLancamento.DELETADO,
                    List.of(new CategoriaResumo(1L, "Mercado", true)),
                    null,
                    null)));

    ArquivoExportado arquivo = service.exportar(USUARIO, "csv", FILTRO);

    assertThat(arquivo.nome()).isEqualTo("lancamentos-2026-11-12.csv");
    assertThat(arquivo.contentType()).startsWith("text/csv");
    assertThat(new String(arquivo.conteudo(), StandardCharsets.UTF_8))
        .contains("Pão;DESPESA;8;2026;10,00;Mercado;DELETADO");
  }

  @Test
  void deveRecusarExportacaoSemDados() {
    when(lancamentos.listar(USUARIO, FILTRO)).thenReturn(List.of());

    assertThatThrownBy(() -> service.exportar(USUARIO, "xlsx", FILTRO))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage("Não há dados para exportação");
  }

  @Test
  void formatoInvalidoNaoDeveConsultarOBanco() {
    assertThatThrownBy(() -> service.exportar(USUARIO, "pdf", FILTRO))
        .isInstanceOf(FormatoInvalidoException.class);
    verify(lancamentos, never()).listar(any(), any());
  }
}
