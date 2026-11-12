package br.com.poupamais.arquivo.importacao;

import br.com.poupamais.categoria.CategoriaFacade;
import br.com.poupamais.categoria.CategoriaResumo;
import br.com.poupamais.lancamento.LancamentoFacade;
import br.com.poupamais.lancamento.NovoLancamento;
import br.com.poupamais.shared.exception.ConflitoException;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.util.ArrayList;
import java.util.HashMap;
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

/**
 * Importação CSV (RF003, RN007, RN012, RN002). De propósito <b>não</b> é {@code @Transactional}:
 * cada linha válida é gravada pela {@code LancamentoFacade.criar} na sua própria transação, então
 * uma falha não desfaz as outras.
 *
 * <p>Por linha: converte e confere os campos; procura as categorias citadas (inativa = falha da
 * linha, sem criar nada); só então cria as inexistentes ({@code buscarOuCriar}) e grava.
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
        List<Long> ids = dados.categorias().stream().map(this::resolver).toList();
        lancamentos.criar(
            idUsuario,
            new NovoLancamento(
                dados.descricao(), dados.tipo(), dados.mes(), dados.ano(), dados.valor(), ids));
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

    private Long resolver(String descricao) {
      Optional<CategoriaResumo> existente = existente(descricao);
      if (existente.isPresent()) {
        return existente.get().id();
      }
      CategoriaResumo criada = categorias.buscarOuCriar(idUsuario, descricao);
      cache.put(chave(descricao), criada);
      criadas.add(criada.descricao());
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
