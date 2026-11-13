package br.com.poupamais.usuario.endpoints.dto;

import java.time.LocalDate;

public record PerfilResponse(
    Long id, String nome, String email, boolean possuiFoto, LocalDate dataCadastro) {}
