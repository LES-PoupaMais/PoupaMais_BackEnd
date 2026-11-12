package br.com.poupamais.usuario.api;

import br.com.poupamais.shared.security.UsuarioLogado;
import br.com.poupamais.usuario.api.dto.ExcluirContaRequest;
import br.com.poupamais.usuario.service.PrivacidadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** LGPD (RF015–RF017). A exportação de dados (RF016) entra depois do módulo lancamento. */
@RestController
@RequestMapping("/api/usuarios/me")
@RequiredArgsConstructor
public class PrivacidadeResource {

  private final PrivacidadeService service;

  /** POST porque DELETE com body não é confiável em proxies. */
  @PostMapping("/excluir")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void excluirConta(@Valid @RequestBody ExcluirContaRequest request) {
    service.excluirConta(UsuarioLogado.id(), request.senha());
  }
}
