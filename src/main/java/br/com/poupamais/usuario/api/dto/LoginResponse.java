package br.com.poupamais.usuario.api.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiraEm, UsuarioResponse usuario) {}
