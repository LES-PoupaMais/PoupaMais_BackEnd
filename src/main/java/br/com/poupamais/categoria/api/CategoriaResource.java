package br.com.poupamais.categoria.api;

import br.com.poupamais.categoria.api.dto.CategoriaRequest;
import br.com.poupamais.categoria.api.dto.CategoriaResponse;
import br.com.poupamais.categoria.api.dto.CategoriaStatusRequest;
import br.com.poupamais.categoria.service.CategoriaService;
import br.com.poupamais.shared.security.UsuarioLogado;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Categorias do usuário logado (RF011, UC009). */
@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaResource {

  private final CategoriaService service;
  private final CategoriaMapper mapper;

  @GetMapping
  public List<CategoriaResponse> listar(@RequestParam(required = false) Boolean ativa) {
    return mapper.paraResponse(service.listar(UsuarioLogado.id(), ativa));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CategoriaResponse criar(@Valid @RequestBody CategoriaRequest request) {
    return mapper.paraResponse(service.criar(UsuarioLogado.id(), request.descricao()));
  }

  @PutMapping("/{id}")
  public CategoriaResponse editar(
      @PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
    return mapper.paraResponse(service.editar(UsuarioLogado.id(), id, request.descricao()));
  }

  @PatchMapping("/{id}/status")
  public CategoriaResponse alterarStatus(
      @PathVariable Long id, @Valid @RequestBody CategoriaStatusRequest request) {
    return mapper.paraResponse(service.alterarStatus(UsuarioLogado.id(), id, request.ativa()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void excluir(@PathVariable Long id) {
    service.excluir(UsuarioLogado.id(), id);
  }
}
