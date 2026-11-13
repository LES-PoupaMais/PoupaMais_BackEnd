package br.com.poupamais.usuario.service;

import br.com.poupamais.shared.security.JwtService.TokenGerado;
import br.com.poupamais.usuario.model.entidades.FotoPerfil;
import br.com.poupamais.usuario.model.entidades.Usuario;

/** Perfil, foto e troca de senha do usuário logado (RF013, RF014, RN015). */
public interface PerfilService {

  Perfil buscar(Long idUsuario);

  Perfil atualizarNome(Long idUsuario, String nome);

  /** Troca a senha e devolve um token novo: a sessão atual continua, as outras caem. */
  TokenGerado trocarSenha(Long idUsuario, String senhaAtual, String novaSenha);

  FotoPerfil buscarFoto(Long idUsuario);

  void salvarFoto(Long idUsuario, byte[] conteudo);

  void removerFoto(Long idUsuario);

  record Perfil(Usuario usuario, boolean possuiFoto) {}
}
