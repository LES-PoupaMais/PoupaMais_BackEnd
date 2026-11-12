package br.com.poupamais.categoria.endpoints.dto;

import jakarta.validation.constraints.NotNull;

public record CategoriaStatusRequest(
    @NotNull(message = "Informe se a categoria está ativa") Boolean ativa) {}
