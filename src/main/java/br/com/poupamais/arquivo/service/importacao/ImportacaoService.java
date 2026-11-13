package br.com.poupamais.arquivo.service.importacao;

import br.com.poupamais.categoria.publico.CategoriaFacade;
import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.lancamento.publico.LancamentoFacade;
import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import br.com.poupamais.shared.exception.ConflitoException;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Importação CSV (RF003, RN007, RN012, RN002). De propósito <b>não</b> é {@code @Transactional} por
 * arquivo: cada linha válida é gravada na sua própria transação, então uma falha não desfaz as
 * outras.
 *
 * <p>Por linha: converte e confere os campos; procura as categorias citadas (inativa = falha da
 * linha, sem criar nada); só então, numa transação só da linha, cria as inexistentes ({@code
 * buscarOuCriar}) e grava ({@code LancamentoFacade.criar}). As facades usam a propagação padrão
 * ({@code REQUIRED}) e entram nessa transação: se {@code criar} recusar a linha, a categoria criada
 * para ela é desfeita junto. Cache e {@code categoriasCriadas} só recebem as categorias novas
 * depois do commit da linha.
 */
@Service
@RequiredArgsConstructor
public class ImportacaoService {

  private static final Logger log = LoggerFactory.getLogger(ImportacaoService.class);

  static final String MSG_CATEGORIA_INATIVA =
      "A categoria \"%s\" está inativa e não pode ser usada";
  static final String MSG_ERRO_INESPERADO = "Não foi possível salvar esta linha. Tente novamente.";

  private final LeitorCsv leitor;
  private final ConversorLinha conversor;
  private final CategoriaFacade categorias;
  private final LancamentoFacade lancamentos;
  private final TransactionOperations transacao;

  public ResultadoImportacao importar(Long idUsuario, byte[] arquivo) {
    List<LinhaCsv> linhas = leitor.ler(arquivo);
    Importacao importacao = new Importacao(idUsuario);
    linhas.forEach(importacao::processar);
    return new ResultadoImportacao(
        linhas.size(),
        importacao.importados,
        List.copyOf(importacao.falhas),
        List.copyOf(importacao.criadas));
  }

  /** Estado de uma importação (cache das categorias já resolvidas, contadores). */
  private final class Importacao {

    private final Long idUsuario;
    private final Map<String, CategoriaResumo> cache = new HashMap<>();
    private final List<ResultadoImportacao.Falha> falhas = new ArrayList<>();
    private final Set<String> criadas = new LinkedHashSet<>();
    private int importados;

    Importacao(Long idUsuario) {
      this.idUsuario = idUsuario;
    }

    void processar(LinhaCsv linha) {
      if (linha.erro() != null) {
        falhar(linha, linha.erro());
        return;
      }
      try {
        DadosLinha dados = conversor.converter(linha.campos());
        verificarInativas(dados.categorias());
        Map<String, CategoriaResumo> novas = new LinkedHashMap<>();
        transacao.executeWithoutResult(status -> gravar(dados, novas));
        // Só depois do commit: se a linha falhou, as categorias novas dela foram desfeitas
        novas.forEach(cache::put);
        novas.values().forEach(c -> criadas.add(c.descricao()));
        importados++;
      } catch (LinhaInvalidaException
          | RegraNegocioException
          | RecursoNaoEncontradoException
          | ConflitoException e) {
        falhar(linha, e.getMessage());
      } catch (RuntimeException e) {
        // Só o tipo no log; nunca o conteúdo da linha (valores financeiros, RNF019)
        log.error("Erro inesperado na importação: {}", e.getClass().getName(), e);
        falhar(linha, MSG_ERRO_INESPERADO);
      }
    }

    private void verificarInativas(List<String> descricoes) {
      for (String descricao : descricoes) {
        Optional<CategoriaResumo> existente = existente(descricao);
        if (existente.isPresent() && !existente.get().ativa()) {
          throw new LinhaInvalidaException(
              MSG_CATEGORIA_INATIVA.formatted(existente.get().descricao()));
        }
      }
    }

    private Optional<CategoriaResumo> existente(String descricao) {
      String chave = chave(descricao);
      CategoriaResumo emCache = cache.get(chave);
      if (emCache != null) {
        return Optional.of(emCache);
      }
      Optional<CategoriaResumo> encontrada = categorias.buscarPorDescricao(idUsuario, descricao);
      encontrada.ifPresent(c -> cache.put(chave, c));
      return encontrada;
    }

    /** Roda dentro da transação da linha; {@code novas} recebe as categorias criadas nela. */
    private void gravar(DadosLinha dados, Map<String, CategoriaResumo> novas) {
      List<Long> ids = dados.categorias().stream().map(d -> resolver(d, novas)).toList();
      lancamentos.criar(
          idUsuario,
          new NovoLancamento(
              dados.descricao(), dados.tipo(), dados.mes(), dados.ano(), dados.valor(), ids));
    }

    private Long resolver(String descricao, Map<String, CategoriaResumo> novas) {
      Optional<CategoriaResumo> existente = existente(descricao);
      if (existente.isPresent()) {
        return existente.get().id();
      }
      CategoriaResumo criada = categorias.buscarOuCriar(idUsuario, descricao);
      novas.put(chave(descricao), criada);
      return criada.id();
    }

    private void falhar(LinhaCsv linha, String motivo) {
      falhas.add(new ResultadoImportacao.Falha(linha.numero(), linha.conteudo(), motivo));
    }

    private static String chave(String descricao) {
      return descricao.strip().toLowerCase(Locale.ROOT);
    }
  }
}
