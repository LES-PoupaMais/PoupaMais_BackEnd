package br.com.poupamais.arquivo.service.exportacao;

import br.com.poupamais.lancamento.publico.LancamentoFacade;
import br.com.poupamais.lancamento.publico.dto.FiltroLancamento;
import br.com.poupamais.lancamento.publico.dto.LancamentoResumo;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Exportação CSV/XLSX (RF007, RN013): os mesmos filtros e a mesma ordem da listagem; DELETADO só
 * com {@code status=DELETADO}; sem resultados → 422.
 */
@Service
@RequiredArgsConstructor
public class ExportacaoService {

  static final String MSG_SEM_DADOS = "Não há dados para exportação";
  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

  private final LancamentoFacade lancamentos;
  private final ExportadorFactory fabrica;
  private Clock relogio = Clock.system(FUSO);

  void usarRelogio(Clock relogio) {
    this.relogio = relogio;
  }

  public ArquivoExportado exportar(Long idUsuario, String formato, FiltroLancamento filtro) {
    ExportadorLancamentos exportador = fabrica.para(formato);
    List<LancamentoResumo> dados = lancamentos.listar(idUsuario, filtro);
    if (dados.isEmpty()) {
      throw new RegraNegocioException(MSG_SEM_DADOS);
    }
    String nome = "lancamentos-" + LocalDate.now(relogio) + "." + exportador.formato();
    return new ArquivoExportado(nome, exportador.contentType(), exportador.exportar(dados));
  }
}
