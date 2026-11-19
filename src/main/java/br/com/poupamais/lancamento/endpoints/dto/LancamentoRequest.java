package br.com.poupamais.lancamento.endpoints.dto;

import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Corpo do {@code POST}/{@code PUT} de lançamento. {@code data} em ISO ({@code "2026-11-05"}); dia
 * inexistente ({@code "2026-02-31"}) → 400. A faixa 2000–2100 é conferida no {@code
 * LancamentoValidator} (422). {@code status} é opcional: ATIVO ou INATIVO (no POST, ausente =
 * ATIVO; no PUT, ausente = mantém o atual); DELETADO → 400.
 */
public record LancamentoRequest(
    @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 100, message = "A descrição deve ter no máximo 100 caracteres")
        String descricao,
    @NotNull(message = "O tipo é obrigatório (RECEITA ou DESPESA)") TipoLancamento tipo,
    @NotNull(message = "A data é obrigatória") LocalDate data,
    @NotNull(message = "O valor é obrigatório")
        @Positive(message = "O valor deve ser maior que zero")
        @Digits(
            integer = 14,
            fraction = 2,
            message = "O valor deve ter no máximo 14 dígitos inteiros e 2 casas decimais")
        BigDecimal valor,
    @NotEmpty(message = "Informe pelo menos uma categoria") List<Long> categoriaIds,
    @Schema(
            description =
                "Opcional. ATIVO ou INATIVO. No POST, ausente = ATIVO; no PUT, ausente = mantém o"
                    + " atual. DELETADO → 400 (a lixeira é pelo DELETE).",
            allowableValues = {"ATIVO", "INATIVO"},
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @StatusAtivoOuInativo
        StatusLancamento status) {}
