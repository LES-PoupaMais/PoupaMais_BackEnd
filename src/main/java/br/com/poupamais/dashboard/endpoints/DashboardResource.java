package br.com.poupamais.dashboard.endpoints;

import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse;
import br.com.poupamais.dashboard.service.DashboardService;
import br.com.poupamais.shared.endpoints.dto.PeriodoParams;
import br.com.poupamais.shared.security.UsuarioLogado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard do mês selecionado (RF006 / UC005). */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardResource {

  private final DashboardService service;

  @GetMapping
  public DashboardResponse consultar(@Valid @ModelAttribute PeriodoParams params) {
    return service.consultar(UsuarioLogado.id(), params.paraPeriodo());
  }
}
