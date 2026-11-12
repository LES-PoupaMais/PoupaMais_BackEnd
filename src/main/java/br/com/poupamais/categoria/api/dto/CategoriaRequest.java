package br.com.poupamais.categoria.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(
    @NotBlank(message = "A descrição da categoria é obrigatória")
        @Size(max = 255, message = "A descrição da categoria deve ter no máximo 255 caracteres")
        String descricao) {}
