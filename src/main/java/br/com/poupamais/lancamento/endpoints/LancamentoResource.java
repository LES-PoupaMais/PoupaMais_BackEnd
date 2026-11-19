package br.com.poupamais.lancamento.endpoints;

import br.com.poupamais.lancamento.endpoints.dto.LancamentoFiltroParams;
import br.com.poupamais.lancamento.endpoints.dto.LancamentoRequest;
import br.com.poupamais.lancamento.endpoints.dto.LancamentoResponse;
import br.com.poupamais.lancamento.endpoints.dto.PaginaResponse;
import br.com.poupamais.lancamento.endpoints.dto.PaginacaoParams;
import br.com.poupamais.lancamento.endpoints.dto.StatusRequest;
import br.com.poupamais.lancamento.endpoints.mappers.LancamentoMapper;
import br.com.poupamais.lancamento.service.LancamentoService;
import br.com.poupamais.shared.security.UsuarioLogado;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
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
  public PaginaResponse<LancamentoResponse> listar(
      @ModelAttribute LancamentoFiltroParams filtro,
      @Valid @ModelAttribute PaginacaoParams paginacao) {
    return mapper.paraResponse(
        service.listarPagina(
            UsuarioLogado.id(),
            mapper.paraFiltro(filtro),
            paginacao.pagina(),
            paginacao.tamanho()));
  }

  @GetMapping("/{id}")
  public LancamentoResponse buscar(@PathVariable Long id) {
    return mapper.paraResponse(service.buscar(UsuarioLogado.id(), id));
  }

  @Operation(
      summary = "Cadastrar lançamento",
      description =
          "201 com o lançamento. `status` opcional (ATIVO ou INATIVO; ausente = ATIVO), gravado na"
              + " mesma chamada. `status: DELETADO` → 400.")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public LancamentoResponse criar(@Valid @RequestBody LancamentoRequest request) {
    return mapper.paraResponse(
        service.criar(UsuarioLogado.id(), mapper.paraNovo(request), request.status()));
  }

  @Operation(
      summary = "Editar lançamento",
      description =
          "200 com o lançamento. `status` opcional (ATIVO ou INATIVO; ausente = mantém o atual)."
              + " `status: DELETADO` → 400. Lançamento na lixeira → 422 (restaure antes).")
  @PutMapping("/{id}")
  public LancamentoResponse editar(
      @PathVariable Long id, @Valid @RequestBody LancamentoRequest request) {
    return mapper.paraResponse(
        service.editar(UsuarioLogado.id(), id, mapper.paraNovo(request), request.status()));
  }

  @PatchMapping("/{id}/status")
  public LancamentoResponse alterarStatus(
      @PathVariable Long id, @Valid @RequestBody StatusRequest request) {
    return mapper.paraResponse(service.alterarStatus(UsuarioLogado.id(), id, request.status()));
  }

  @Operation(
      summary = "Mover lançamento para a lixeira",
      description =
          "204. Exclusão lógica: status DELETADO, grava `dataExclusao` e o status que o lançamento"
              + " tinha (ATIVO/INATIVO), usado pelo restaurar. Já na lixeira → 422.")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void excluir(@PathVariable Long id) {
    service.excluir(UsuarioLogado.id(), id);
  }

  @Operation(
      summary = "Restaurar lançamento da lixeira",
      description =
          "200 com o lançamento de volta ao status que tinha antes do DELETE (ATIVO ou INATIVO;"
              + " itens excluídos antes dessa regra voltam como INATIVO) e `dataExclusao: null`."
              + " Fora da lixeira → 422.")
  @PostMapping("/{id}/restaurar")
  public LancamentoResponse restaurar(@PathVariable Long id) {
    return mapper.paraResponse(service.restaurar(UsuarioLogado.id(), id));
  }
}
