package br.com.poupamais.usuario.endpoints.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecuperarSenhaRequest(
    @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Informe um e-mail válido")
        @Size(max = 100, message = "O e-mail deve ter no máximo 100 caracteres")
        String email) {}
