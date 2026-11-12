package br.com.poupamais.dashboard.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Query de {@code GET /api/dashboard}; sem valor = mês/ano atual. */
public record DashboardParams(
    @Min(value = 1, message = "O mês deve estar entre 1 e 12")
        @Max(value = 12, message = "O mês deve estar entre 1 e 12")
        Integer mes,
    @Min(value = 2000, message = "O ano deve estar entre 2000 e 2100")
        @Max(value = 2100, message = "O ano deve estar entre 2000 e 2100")
        Integer ano) {}
