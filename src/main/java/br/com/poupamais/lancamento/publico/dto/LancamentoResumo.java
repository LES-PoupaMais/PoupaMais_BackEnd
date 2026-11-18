package br.com.poupamais.lancamento.publico.dto;

import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Visão pública de um lançamento. {@code data} é a data do lançamento (RN001); os campos de
 * auditoria têm hora (UTC). {@code categorias} vem ordenada por id crescente: a primeira é a
 * "primeira categoria" da RN009. {@code dataExclusao} só é preenchida enquanto está DELETADO.
 */
public record LancamentoResumo(
    Long id,
    String descricao,
    TipoLancamento tipo,
    LocalDate data,
    BigDecimal valor,
    StatusLancamento status,
    List<CategoriaResumo> categorias,
    OffsetDateTime dataCadastro,
    OffsetDateTime dataAtualizacao,
    OffsetDateTime dataExclusao) {}
