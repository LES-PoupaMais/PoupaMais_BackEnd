package br.com.poupamais.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Formato único de erro da API (contrato em doc/contrato-api.md). {@code codigo} vem sempre
 * (estável, para o front decidir o que fazer); {@code mensagem} é o texto pronto para exibir;
 * {@code erros} só aparece em falhas de validação, com um item por campo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resposta de erro da API (RNF007)")
public record ErroResponse(
    @Schema(example = "422") int status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "CATEGORIA_VINCULADA")
        CodigoErro codigo,
    @Schema(example = "Categoria vinculada a lançamentos, apenas inative") String mensagem,
    List<ErroCampo> erros) {

  public ErroResponse {
    // Nenhum erro sai sem código: o front depende dele
    Objects.requireNonNull(codigo, "codigo");
  }

  public ErroResponse(int status, CodigoErro codigo, String mensagem) {
    this(status, codigo, mensagem, null);
  }

  /**
   * Resposta HTTP com este corpo e o {@link #status()}, <b>sempre em JSON</b>, qualquer que seja o
   * {@code Accept}. Com o Content-Type fixado o Spring não negocia o formato de novo: sem isso, um
   * erro numa rota pedida com {@code Accept: text/csv} (ex.: exportar) falharia ao ser escrito e
   * sairia sem {@code codigo} (406 vazio ou 500).
   */
  public ResponseEntity<ErroResponse> comoResposta() {
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(this);
  }

  public record ErroCampo(String campo, String mensagem) {}
}
