package br.com.poupamais.categoria.endpoints.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Mês/ano de {@code GET /api/categorias} usados para contar os lançamentos de cada categoria; o que
 * faltar vem do mês atual (mesma validação do dashboard).
 */
public record CategoriaListagemParams(
    @Min(value = 1, message = "O mês deve estar entre 1 e 12")
        @Max(value = 12, message = "O mês deve estar entre 1 e 12")
        Integer mes,
    @Min(value = 2000, message = "O ano deve estar entre 2000 e 2100")
        @Max(value = 2100, message = "O ano deve estar entre 2000 e 2100")
        Integer ano) {}
