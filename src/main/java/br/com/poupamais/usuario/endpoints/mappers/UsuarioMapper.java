package br.com.poupamais.usuario.endpoints.mappers;

import br.com.poupamais.shared.security.JwtService.TokenGerado;
import br.com.poupamais.usuario.endpoints.dto.LoginResponse;
import br.com.poupamais.usuario.endpoints.dto.PerfilResponse;
import br.com.poupamais.usuario.endpoints.dto.TokenResponse;
import br.com.poupamais.usuario.endpoints.dto.UsuarioResponse;
import br.com.poupamais.usuario.model.entidades.Usuario;
import br.com.poupamais.usuario.service.AuthService.Sessao;
import br.com.poupamais.usuario.service.PerfilService.Perfil;
import org.springframework.stereotype.Component;

/** Entidade → DTO. Nunca expõe a senha. */
@Component
public class UsuarioMapper {

  public UsuarioResponse paraResponse(Usuario usuario) {
    return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
  }

  public LoginResponse paraResponse(Sessao sessao) {
    return new LoginResponse(
        sessao.token().token(), sessao.token().expiraEm(), paraResponse(sessao.usuario()));
  }

  public TokenResponse paraResponse(TokenGerado token) {
    return new TokenResponse(token.token(), token.expiraEm());
  }

  public PerfilResponse paraResponse(Perfil perfil) {
    Usuario u = perfil.usuario();
    return new PerfilResponse(
        u.getId(), u.getNome(), u.getEmail(), perfil.possuiFoto(), u.getDataCadastro());
  }
}
