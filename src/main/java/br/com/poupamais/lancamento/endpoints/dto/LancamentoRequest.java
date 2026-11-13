package br.com.poupamais.lancamento.endpoints.dto;

import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record LancamentoRequest(
    @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 100, message = "A descrição deve ter no máximo 100 caracteres")
        String descricao,
    @NotNull(message = "O tipo é obrigatório (RECEITA ou DESPESA)") TipoLancamento tipo,
    @NotNull(message = "O mês é obrigatório")
        @Min(value = 1, message = "O mês deve estar entre 1 e 12")
        @Max(value = 12, message = "O mês deve estar entre 1 e 12")
        Integer mes,
    @NotNull(message = "O ano é obrigatório")
        @Min(value = 2000, message = "O ano deve estar entre 2000 e 2100")
        @Max(value = 2100, message = "O ano deve estar entre 2000 e 2100")
        Integer ano,
    @NotNull(message = "O valor é obrigatório")
        @Positive(message = "O valor deve ser maior que zero")
        @Digits(
            integer = 14,
            fraction = 2,
            message = "O valor deve ter no máximo 14 dígitos inteiros e 2 casas decimais")
        BigDecimal valor,
    @NotEmpty(message = "Informe pelo menos uma categoria") List<Long> categoriaIds) {}
