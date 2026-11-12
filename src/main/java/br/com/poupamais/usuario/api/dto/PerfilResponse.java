package br.com.poupamais.usuario.api.dto;

import java.time.LocalDate;

public record PerfilResponse(
    Long id, String nome, String email, boolean possuiFoto, LocalDate dataCadastro) {}
