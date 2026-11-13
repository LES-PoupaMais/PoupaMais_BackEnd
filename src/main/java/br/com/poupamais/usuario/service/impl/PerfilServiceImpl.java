package br.com.poupamais.usuario.service.impl;

import br.com.poupamais.shared.exception.ErroAutenticacaoException;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.shared.security.JwtService;
import br.com.poupamais.shared.security.JwtService.TokenGerado;
import br.com.poupamais.usuario.model.entidades.FotoPerfil;
import br.com.poupamais.usuario.model.entidades.Usuario;
import br.com.poupamais.usuario.model.repositories.FotoPerfilRepository;
import br.com.poupamais.usuario.model.repositories.UsuarioRepository;
import br.com.poupamais.usuario.service.PerfilService;
import br.com.poupamais.usuario.service.RegrasConta;
import br.com.poupamais.usuario.service.TipoFoto;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PerfilServiceImpl implements PerfilService {

  static final String MSG_SESSAO = "Sessão inválida ou expirada. Faça login novamente.";
  static final String MSG_SENHA_ATUAL = "A senha atual está incorreta";
  static final String MSG_SENHA_IGUAL = "A nova senha deve ser diferente da atual";
  static final String MSG_FOTO_NAO_ENCONTRADA = "Foto de perfil não encontrada";
  static final String MSG_FOTO_TIPO = "A foto deve ser uma imagem JPG, PNG ou WEBP";
  static final String MSG_FOTO_TAMANHO = "A foto deve ter no máximo 2 MB";

  private final UsuarioRepository usuarioRepository;
  private final FotoPerfilRepository fotoRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private Clock relogio = Clock.systemUTC();

  void usarRelogio(Clock relogio) {
    this.relogio = relogio;
  }

  @Override
  @Transactional(readOnly = true)
  public Perfil buscar(Long idUsuario) {
    return perfil(usuario(idUsuario));
  }

  @Override
  @Transactional
  public Perfil atualizarNome(Long idUsuario, String nome) {
    Usuario usuario = usuario(idUsuario);
    usuario.setNome(RegrasConta.normalizarNome(nome));
    return perfil(usuarioRepository.save(usuario));
  }

  @Override
  @Transactional
  public TokenGerado trocarSenha(Long idUsuario, String senhaAtual, String novaSenha) {
    Usuario usuario = usuario(idUsuario);
    // 422 e não 401: senha errada aqui não pode derrubar a sessão no front (RN015)
    if (senhaAtual == null || !passwordEncoder.matches(senhaAtual, usuario.getSenha())) {
      throw new RegraNegocioException(MSG_SENHA_ATUAL);
    }
    RegrasConta.validarSenha(novaSenha);
    if (novaSenha.equals(senhaAtual)) {
      throw new RegraNegocioException(MSG_SENHA_IGUAL);
    }
    usuario.setSenha(passwordEncoder.encode(novaSenha));
    usuario.setSenhaAlteradaEm(RegrasConta.agoraEmSegundos(relogio));
    usuarioRepository.save(usuario);
    // Gerado depois (mesmo segundo ou após): continua válido no filtro
    return jwtService.gerar(idUsuario);
  }

  @Override
  @Transactional(readOnly = true)
  public FotoPerfil buscarFoto(Long idUsuario) {
    return fotoRepository
        .findById(idUsuario)
        .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_FOTO_NAO_ENCONTRADA));
  }

  @Override
  @Transactional
  public void salvarFoto(Long idUsuario, byte[] conteudo) {
    if (conteudo != null && conteudo.length > TipoFoto.TAMANHO_MAXIMO) {
      throw new RegraNegocioException(MSG_FOTO_TAMANHO);
    }
    String tipo =
        TipoFoto.detectar(conteudo).orElseThrow(() -> new RegraNegocioException(MSG_FOTO_TIPO));
    usuario(idUsuario);
    FotoPerfil foto =
        fotoRepository
            .findById(idUsuario)
            .orElseGet(() -> FotoPerfil.builder().idUsuario(idUsuario).build());
    foto.setConteudo(conteudo);
    foto.setTipoConteudo(tipo);
    foto.setDataAtualizacao(relogio.instant());
    fotoRepository.save(foto);
  }

  @Override
  @Transactional
  public void removerFoto(Long idUsuario) {
    if (fotoRepository.existsById(idUsuario)) {
      fotoRepository.deleteById(idUsuario);
    }
  }

  private Usuario usuario(Long idUsuario) {
    // O filtro já recusa token de usuário excluído; isto cobre a corrida com a exclusão
    return usuarioRepository
        .findById(idUsuario)
        .orElseThrow(() -> new ErroAutenticacaoException(MSG_SESSAO));
  }

  private Perfil perfil(Usuario usuario) {
    return new Perfil(usuario, fotoRepository.existsById(usuario.getId()));
  }
}
