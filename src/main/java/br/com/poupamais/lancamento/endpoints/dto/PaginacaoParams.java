package br.com.poupamais.lancamento.endpoints.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Paginação de {@code GET /api/lancamentos}: {@code pagina} começa em 0 (padrão 0) e {@code
 * tamanho} vai de 1 a {@value #TAMANHO_MAXIMO} (padrão {@value #TAMANHO_PADRAO}). Fora disso → 400.
 */
public record PaginacaoParams(
    @Min(value = 0, message = "A página deve ser maior ou igual a 0") Integer pagina,
    @Min(value = 1, message = "O tamanho da página deve ser de 1 a 100")
        @Max(value = TAMANHO_MAXIMO, message = "O tamanho da página deve ser de 1 a 100")
        Integer tamanho) {

  public static final int TAMANHO_PADRAO = 12;
  public static final int TAMANHO_MAXIMO = 100;

  public PaginacaoParams {
    pagina = pagina == null ? 0 : pagina;
    tamanho = tamanho == null ? TAMANHO_PADRAO : tamanho;
  }
}
