package br.com.poupamais.usuario.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ExcluirContaRequest(@NotBlank(message = "A senha é obrigatória") String senha) {}
