package br.com.poupamais.shared.endpoints.dto;

import br.com.poupamais.shared.domain.Periodo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Mês/ano opcionais de query ({@code GET /api/dashboard} e {@code GET /api/categorias}), validados
 * num lugar só: fora de 1–12 / {@value Periodo#ANO_MINIMO}–{@value Periodo#ANO_MAXIMO} → 400 com o
 * erro no campo. O que faltar vem do mês atual ({@link #paraPeriodo()}).
 */
public record PeriodoParams(
    @Min(value = 1, message = MSG_MES) @Max(value = 12, message = MSG_MES) Integer mes,
    @Min(value = Periodo.ANO_MINIMO, message = MSG_ANO)
        @Max(value = Periodo.ANO_MAXIMO, message = MSG_ANO)
        Integer ano) {

  public static final String MSG_MES = "O mês deve estar entre 1 e 12";
  public static final String MSG_ANO =
      "O ano deve estar entre " + Periodo.ANO_MINIMO + " e " + Periodo.ANO_MAXIMO;

  /** O período pedido, completando com o mês/ano atual (fuso de Brasília) o que não veio. */
  public Periodo paraPeriodo() {
    Periodo atual = Periodo.atual();
    return new Periodo(mes != null ? mes : atual.mes(), ano != null ? ano : atual.ano());
  }
}
