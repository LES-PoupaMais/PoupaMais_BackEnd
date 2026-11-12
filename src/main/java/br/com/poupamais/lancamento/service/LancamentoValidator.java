package br.com.poupamais.lancamento.service;

import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import br.com.poupamais.shared.domain.Periodo;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * RN001 em um só lugar: usado pelo cadastro/edição manual e pela importação (via {@code
 * LancamentoFacade.criar}). Devolve os dados normalizados (descrição sem espaços nas pontas, valor
 * com 2 casas, categorias sem repetição) ou lança {@link RegraNegocioException} com a mensagem do
 * primeiro problema encontrado.
 */
@Component
public class LancamentoValidator {

  public static final int TAMANHO_MAXIMO_DESCRICAO = 100;

  /** Maior valor que cabe em {@code numeric(16,2)}. */
  public static final BigDecimal VALOR_MAXIMO = new BigDecimal("99999999999999.99");

  static final String MSG_DESCRICAO_OBRIGATORIA = "A descrição é obrigatória";
  static final String MSG_DESCRICAO_LONGA = "A descrição deve ter no máximo 100 caracteres";
  static final String MSG_TIPO_OBRIGATORIO = "O tipo é obrigatório (RECEITA ou DESPESA)";
  static final String MSG_MES_INVALIDO = "O mês deve estar entre 1 e 12";
  static final String MSG_ANO_INVALIDO =
      "O ano deve estar entre " + Periodo.ANO_MINIMO + " e " + Periodo.ANO_MAXIMO;
  static final String MSG_VALOR_OBRIGATORIO = "O valor é obrigatório";
  static final String MSG_VALOR_POSITIVO = "O valor deve ser maior que zero";
  static final String MSG_VALOR_GRANDE = "O valor excede o máximo permitido";
  static final String MSG_CATEGORIA_OBRIGATORIA = "Informe pelo menos uma categoria";

  public NovoLancamento validar(NovoLancamento dados) {
    if (dados == null) {
      throw new RegraNegocioException(MSG_DESCRICAO_OBRIGATORIA);
    }
    String descricao = validarDescricao(dados.descricao());
    if (dados.tipo() == null) {
      throw new RegraNegocioException(MSG_TIPO_OBRIGATORIO);
    }
    if (dados.mes() == null || dados.mes() < 1 || dados.mes() > 12) {
      throw new RegraNegocioException(MSG_MES_INVALIDO);
    }
    if (dados.ano() == null
        || dados.ano() < Periodo.ANO_MINIMO
        || dados.ano() > Periodo.ANO_MAXIMO) {
      throw new RegraNegocioException(MSG_ANO_INVALIDO);
    }
    BigDecimal valor = validarValor(dados.valor());
    List<Long> categoriaIds = validarCategorias(dados.categoriaIds());
    return new NovoLancamento(
        descricao, dados.tipo(), dados.mes(), dados.ano(), valor, categoriaIds);
  }

  private static String validarDescricao(String descricao) {
    String normalizada = descricao == null ? "" : descricao.strip();
    if (normalizada.isEmpty()) {
      throw new RegraNegocioException(MSG_DESCRICAO_OBRIGATORIA);
    }
    if (normalizada.length() > TAMANHO_MAXIMO_DESCRICAO) {
      throw new RegraNegocioException(MSG_DESCRICAO_LONGA);
    }
    return normalizada;
  }

  private static BigDecimal validarValor(BigDecimal valor) {
    if (valor == null) {
      throw new RegraNegocioException(MSG_VALOR_OBRIGATORIO);
    }
    BigDecimal arredondado = valor.setScale(2, RoundingMode.HALF_UP);
    if (arredondado.signum() <= 0) {
      throw new RegraNegocioException(MSG_VALOR_POSITIVO);
    }
    if (arredondado.compareTo(VALOR_MAXIMO) > 0) {
      throw new RegraNegocioException(MSG_VALOR_GRANDE);
    }
    return arredondado;
  }

  /** Ids nulos são descartados; repetidos contam uma vez (ordem preservada). */
  private static List<Long> validarCategorias(List<Long> ids) {
    if (ids == null || ids.isEmpty() || ids.stream().allMatch(Objects::isNull)) {
      throw new RegraNegocioException(MSG_CATEGORIA_OBRIGATORIA);
    }
    return List.copyOf(new LinkedHashSet<>(ids.stream().filter(Objects::nonNull).toList()));
  }
}
