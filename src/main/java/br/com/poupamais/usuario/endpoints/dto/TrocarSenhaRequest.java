package br.com.poupamais.usuario.endpoints.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TrocarSenhaRequest(
    @NotBlank(message = "A senha atual é obrigatória") String senhaAtual,
    @NotNull(message = "A nova senha é obrigatória")
        @Size(min = 6, max = 72, message = "A senha deve ter entre 6 e 72 caracteres")
        String novaSenha) {}
