package br.com.poupamais.usuario.endpoints.dto;

import br.com.poupamais.lancamento.publico.enums.StatusLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Arquivo {@code meus-dados-poupamais.json} da exportação de dados do titular (RF016, RN016). Sem
 * senha, hash, tokens nem os bytes da foto (só {@code possuiFoto}).
 */
public record DadosTitularResponse(
    Instant geradoEm,
    PerfilExportado perfil,
    List<CategoriaExportada> categorias,
    List<LancamentoExportado> lancamentos) {

  public record PerfilExportado(
      Long id,
      String nome,
      String email,
      LocalDate dataCadastro,
      Instant aceiteTermosEm,
      boolean possuiFoto) {}

  public record CategoriaExportada(Long id, String descricao, boolean ativa) {}

  public record CategoriaDoLancamento(Long id, String descricao) {}

  public record LancamentoExportado(
      Long id,
      String descricao,
      TipoLancamento tipo,
      LocalDate data,
      BigDecimal valor,
      StatusLancamento status,
      List<CategoriaDoLancamento> categorias,
      OffsetDateTime dataCadastro,
      OffsetDateTime dataAtualizacao,
      OffsetDateTime dataExclusao) {}
}
