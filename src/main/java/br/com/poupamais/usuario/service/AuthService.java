package br.com.poupamais.usuario.service;

import br.com.poupamais.shared.security.JwtService.TokenGerado;
import br.com.poupamais.usuario.model.entidades.Usuario;

/** Cadastro (RF009, RF015) e login (RF010). */
public interface AuthService {

  Usuario cadastrar(String nome, String email, String senha, boolean aceiteTermos);

  Sessao login(String email, String senha);

  record Sessao(TokenGerado token, Usuario usuario) {}
}
