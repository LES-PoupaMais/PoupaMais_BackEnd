package br.com.poupamais.dashboard.service;

import br.com.poupamais.dashboard.api.dto.DashboardResponse;
import br.com.poupamais.shared.domain.Periodo;

/** Indicadores do dashboard (RN003, RN008 a RN010). */
public interface DashboardService {

  DashboardResponse consultar(Long idUsuario, Periodo periodo);
}
