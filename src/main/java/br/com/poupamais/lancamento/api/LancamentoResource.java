package br.com.poupamais.lancamento.api;

import br.com.poupamais.lancamento.api.dto.LancamentoFiltroParams;
import br.com.poupamais.lancamento.api.dto.LancamentoRequest;
import br.com.poupamais.lancamento.api.dto.LancamentoResponse;
import br.com.poupamais.lancamento.api.dto.StatusRequest;
import br.com.poupamais.lancamento.service.LancamentoService;
import br.com.poupamais.shared.security.UsuarioLogado;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lançamentos do usuário logado (RF001–RF005, RF008 / UC001, UC002, UC004, UC010). */
@RestController
@RequestMapping("/api/lancamentos")
@RequiredArgsConstructor
public class LancamentoResource {

  private final LancamentoService service;
  private final LancamentoMapper mapper;

  @GetMapping
  public List<LancamentoResponse> listar(@ModelAttribute LancamentoFiltroParams filtro) {
    return mapper.paraResponse(service.listar(UsuarioLogado.id(), mapper.paraFiltro(filtro)));
  }

  @GetMapping("/{id}")
  public LancamentoResponse buscar(@PathVariable Long id) {
    return mapper.paraResponse(service.buscar(UsuarioLogado.id(), id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public LancamentoResponse criar(@Valid @RequestBody LancamentoRequest request) {
    return mapper.paraResponse(service.criar(UsuarioLogado.id(), mapper.paraNovo(request)));
  }

  @PutMapping("/{id}")
  public LancamentoResponse editar(
      @PathVariable Long id, @Valid @RequestBody LancamentoRequest request) {
    return mapper.paraResponse(service.editar(UsuarioLogado.id(), id, mapper.paraNovo(request)));
  }

  @PatchMapping("/{id}/status")
  public LancamentoResponse alterarStatus(
      @PathVariable Long id, @Valid @RequestBody StatusRequest request) {
    return mapper.paraResponse(service.alterarStatus(UsuarioLogado.id(), id, request.status()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void excluir(@PathVariable Long id) {
    service.excluir(UsuarioLogado.id(), id);
  }

  @PostMapping("/{id}/restaurar")
  public LancamentoResponse restaurar(@PathVariable Long id) {
    return mapper.paraResponse(service.restaurar(UsuarioLogado.id(), id));
  }
}
