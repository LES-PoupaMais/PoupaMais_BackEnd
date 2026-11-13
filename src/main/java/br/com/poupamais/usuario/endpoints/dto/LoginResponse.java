package br.com.poupamais.usuario.endpoints.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiraEm, UsuarioResponse usuario) {}
