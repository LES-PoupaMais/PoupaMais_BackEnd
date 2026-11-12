package br.com.poupamais.lancamento.endpoints.dto;

import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import jakarta.validation.constraints.NotNull;

/** Só ATIVO ou INATIVO; DELETADO vai pelo DELETE e a volta pelo restaurar. */
public record StatusRequest(@NotNull(message = "O status é obrigatório") StatusLancamento status) {}
