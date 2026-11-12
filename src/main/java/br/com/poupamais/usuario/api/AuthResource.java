package br.com.poupamais.usuario.api;

import br.com.poupamais.usuario.api.dto.CadastroRequest;
import br.com.poupamais.usuario.api.dto.LoginRequest;
import br.com.poupamais.usuario.api.dto.LoginResponse;
import br.com.poupamais.usuario.api.dto.RecuperarSenhaRequest;
import br.com.poupamais.usuario.api.dto.RedefinirSenhaRequest;
import br.com.poupamais.usuario.api.dto.UsuarioResponse;
import br.com.poupamais.usuario.service.AuthService;
import br.com.poupamais.usuario.service.RecuperacaoSenhaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rotas públicas de autenticação (RF009, RF010, RF012). */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthResource {

  private final AuthService authService;
  private final RecuperacaoSenhaService recuperacaoService;
  private final UsuarioMapper mapper;

  @PostMapping("/cadastro")
  @ResponseStatus(HttpStatus.CREATED)
  public UsuarioResponse cadastrar(@Valid @RequestBody CadastroRequest request) {
    return mapper.paraResponse(
        authService.cadastrar(
            request.nome(),
            request.email(),
            request.senha(),
            Boolean.TRUE.equals(request.aceiteTermos())));
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return mapper.paraResponse(authService.login(request.email(), request.senha()));
  }

  @PostMapping("/recuperar-senha")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void recuperarSenha(@Valid @RequestBody RecuperarSenhaRequest request) {
    recuperacaoService.solicitar(request.email());
  }

  @PostMapping("/redefinir-senha")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request) {
    recuperacaoService.redefinir(request.token(), request.novaSenha());
  }
}
