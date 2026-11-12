package br.com.poupamais.usuario.api.dto;

import java.time.Instant;

public record TokenResponse(String token, Instant expiraEm) {}
