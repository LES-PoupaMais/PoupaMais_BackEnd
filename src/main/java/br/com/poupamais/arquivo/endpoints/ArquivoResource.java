package br.com.poupamais.arquivo.endpoints;

import br.com.poupamais.arquivo.endpoints.dto.ExportacaoParams;
import br.com.poupamais.arquivo.service.exportacao.ArquivoExportado;
import br.com.poupamais.arquivo.service.exportacao.ExportacaoService;
import br.com.poupamais.arquivo.service.exportacao.FormatoInvalidoException;
import br.com.poupamais.arquivo.service.importacao.ImportacaoService;
import br.com.poupamais.arquivo.service.importacao.ResultadoImportacao;
import br.com.poupamais.lancamento.publico.dto.FiltroLancamento;
import br.com.poupamais.shared.exception.ErroResponse;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.shared.security.UsuarioLogado;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importação CSV, exportação CSV/XLSX e template (RF003, RF007 / UC003, UC006–UC008). As rotas
 * ficam sob {@code /api/lancamentos}, mas são caminhos literais: o Spring os prefere a {@code
 * /api/lancamentos/{id}} do módulo {@code lancamento}.
 */
@RestController
@RequestMapping("/api/lancamentos")
@RequiredArgsConstructor
public class ArquivoResource {

  static final String TEMPLATE = "template.csv";
  static final String MSG_SEM_ARQUIVO = "Envie o arquivo CSV no campo \"arquivo\".";

  private final ImportacaoService importacao;
  private final ExportacaoService exportacao;

  @PostMapping(path = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResultadoImportacao importar(
      @RequestParam(name = "arquivo", required = false) MultipartFile arquivo) throws IOException {
    if (arquivo == null || arquivo.isEmpty()) {
      throw new RegraNegocioException(MSG_SEM_ARQUIVO);
    }
    return importacao.importar(UsuarioLogado.id(), arquivo.getBytes());
  }

  @GetMapping("/exportar")
  public ResponseEntity<byte[]> exportar(@ModelAttribute ExportacaoParams params) {
    FiltroLancamento filtro =
        new FiltroLancamento(
            params.descricao(),
            params.mes(),
            params.ano(),
            params.tipo(),
            params.status(),
            params.categoriaId());
    ArquivoExportado arquivo = exportacao.exportar(UsuarioLogado.id(), params.formato(), filtro);
    return anexo(arquivo.nome(), arquivo.contentType(), arquivo.conteudo());
  }

  @GetMapping("/template-csv")
  public ResponseEntity<byte[]> template() {
    try (InputStream entrada = new ClassPathResource(TEMPLATE).getInputStream()) {
      return anexo(TEMPLATE, "text/csv;charset=UTF-8", entrada.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @ExceptionHandler(FormatoInvalidoException.class)
  public ResponseEntity<ErroResponse> formatoInvalido(FormatoInvalidoException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErroResponse(400, ex.getMessage()));
  }

  private static ResponseEntity<byte[]> anexo(String nome, String contentType, byte[] conteudo) {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(nome).build().toString())
        .contentType(MediaType.parseMediaType(contentType))
        .body(conteudo);
  }
}
