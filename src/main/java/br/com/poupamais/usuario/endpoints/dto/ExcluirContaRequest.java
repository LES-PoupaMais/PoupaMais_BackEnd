package br.com.poupamais.usuario.endpoints.dto;

import jakarta.validation.constraints.NotBlank;

public record ExcluirContaRequest(@NotBlank(message = "A senha é obrigatória") String senha) {}
