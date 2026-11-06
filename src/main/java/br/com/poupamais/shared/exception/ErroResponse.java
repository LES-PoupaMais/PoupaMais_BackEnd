package br.com.poupamais.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Formato único de erro da API (contrato no CLAUDE.md). {@code erros} só aparece em falhas de
 * validação, com um item por campo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResponse(int status, String mensagem, List<ErroCampo> erros) {

  public ErroResponse(int status, String mensagem) {
    this(status, mensagem, null);
  }

  public record ErroCampo(String campo, String mensagem) {}
}
