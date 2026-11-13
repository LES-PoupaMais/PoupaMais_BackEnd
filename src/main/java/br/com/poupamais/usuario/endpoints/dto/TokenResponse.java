package br.com.poupamais.usuario.endpoints.dto;

import java.time.Instant;

public record TokenResponse(String token, Instant expiraEm) {}
