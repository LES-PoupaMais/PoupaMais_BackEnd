package br.com.poupamais.usuario.api;

import br.com.poupamais.shared.security.UsuarioLogado;
import br.com.poupamais.usuario.api.dto.AtualizarPerfilRequest;
import br.com.poupamais.usuario.api.dto.PerfilResponse;
import br.com.poupamais.usuario.api.dto.TokenResponse;
import br.com.poupamais.usuario.api.dto.TrocarSenhaRequest;
import br.com.poupamais.usuario.model.FotoPerfil;
import br.com.poupamais.usuario.service.PerfilService;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Perfil, foto e troca de senha do usuário logado (RF013, RF014). */
@RestController
@RequestMapping("/api/usuarios/me")
@RequiredArgsConstructor
public class PerfilResource {

  private final PerfilService service;
  private final UsuarioMapper mapper;

  @GetMapping
  public PerfilResponse buscar() {
    return mapper.paraResponse(service.buscar(UsuarioLogado.id()));
  }

  @PutMapping
  public PerfilResponse atualizar(@Valid @RequestBody AtualizarPerfilRequest request) {
    return mapper.paraResponse(service.atualizarNome(UsuarioLogado.id(), request.nome()));
  }

  @PutMapping("/senha")
  public TokenResponse trocarSenha(@Valid @RequestBody TrocarSenhaRequest request) {
    return mapper.paraResponse(
        service.trocarSenha(UsuarioLogado.id(), request.senhaAtual(), request.novaSenha()));
  }

  @GetMapping("/foto")
  public ResponseEntity<byte[]> foto() {
    FotoPerfil foto = service.buscarFoto(UsuarioLogado.id());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(foto.getTipoConteudo()))
        .cacheControl(CacheControl.noCache())
        .body(foto.getConteudo());
  }

  // Campo ausente vira bytes nulos e o service responde 422 (tipo inválido)
  @PutMapping(value = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void salvarFoto(@RequestParam(name = "foto", required = false) MultipartFile foto)
      throws IOException {
    service.salvarFoto(UsuarioLogado.id(), foto == null ? null : foto.getBytes());
  }

  @DeleteMapping("/foto")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removerFoto() {
    service.removerFoto(UsuarioLogado.id());
  }
}
