package br.com.poupamais.arquivo.service.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.poupamais.categoria.publico.CategoriaFacade;
import br.com.poupamais.categoria.publico.dto.CategoriaResumo;
import br.com.poupamais.lancamento.publico.LancamentoFacade;
import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class ImportacaoServiceTest {

  private static final Long USUARIO = 7L;
  private static final String CABECALHO = "descricao;tipo;mes;ano;valor;categorias\n";

  @Mock private CategoriaFacade categorias;
  @Mock private LancamentoFacade lancamentos;

  private ImportacaoService service;
  private final AtomicLong ids = new AtomicLong(100);

  @BeforeEach
  void preparar() {
    service =
        new ImportacaoService(
            new LeitorCsv(),
            new ConversorLinha(),
            categorias,
            lancamentos,
            TransactionOperations.withoutTransaction());
    lenient()
        .when(categorias.buscarPorDescricao(eq(USUARIO), anyString()))
        .thenReturn(Optional.empty());
    lenient()
        .when(categorias.buscarPorDescricao(USUARIO, "Salário"))
        .thenReturn(Optional.of(new CategoriaResumo(3L, "Salário", true)));
    lenient()
        .when(categorias.buscarOuCriar(eq(USUARIO), anyString()))
        .thenAnswer(i -> new CategoriaResumo(ids.incrementAndGet(), i.getArgument(1), true));
  }

  private ResultadoImportacao importar(String linhas) {
    return service.importar(USUARIO, (CABECALHO + linhas).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void deveSalvarLinhasValidasEListarInvalidasComNumeroReal() {
    ResultadoImportacao resultado =
        importar(
            "Salário mensal;RECEITA;8;2026;8500,00;Salário\n"
                + "\n"
                + "Sem valor;DESPESA;8;2026;;Pets\n"
                + "iFood;despesa;8;2026;420.00;Alimentação|Lazer\n");

    assertThat(resultado.totalLinhas()).isEqualTo(3);
    assertThat(resultado.importados()).isEqualTo(2);
    assertThat(resultado.falhas())
        .containsExactly(
            new ResultadoImportacao.Falha(
                4, "Sem valor;DESPESA;8;2026;;Pets", ConversorLinha.MSG_VALOR_OBRIGATORIO));
    assertThat(resultado.categoriasCriadas()).containsExactly("Alimentação", "Lazer");
    ArgumentCaptor<NovoLancamento> captor = ArgumentCaptor.forClass(NovoLancamento.class);
    verify(lancamentos, times(2)).criar(eq(USUARIO), captor.capture());
    assertThat(captor.getAllValues().getFirst())
        .isEqualTo(
            new NovoLancamento(
                "Salário mensal",
                TipoLancamento.RECEITA,
                8,
                2026,
                new java.math.BigDecimal("8500.00"),
                List.of(3L)));
  }

  @Test
  void naoDeveCriarCategoriaEmLinhaInvalida() {
    ResultadoImportacao resultado = importar("Pets;DESPESA;13;2026;10;Pets\n");

    assertThat(resultado.importados()).isZero();
    assertThat(resultado.categoriasCriadas()).isEmpty();
    verify(categorias, never()).buscarOuCriar(any(), any());
    verify(lancamentos, never()).criar(any(), any());
  }

  @Test
  void deveReaproveitarCategoriaCriadaEmLinhaAnterior() {
    ResultadoImportacao resultado = importar("A;DESPESA;8;2026;1;Pets\nB;DESPESA;8;2026;2;pets\n");

    assertThat(resultado.importados()).isEqualTo(2);
    assertThat(resultado.categoriasCriadas()).containsExactly("Pets");
    verify(categorias, times(1)).buscarOuCriar(USUARIO, "Pets");
  }

  @Test
  void deveFalharLinhaComCategoriaInativaSemCriarAsOutras() {
    when(categorias.buscarPorDescricao(USUARIO, "Antiga"))
        .thenReturn(Optional.of(new CategoriaResumo(9L, "Antiga", false)));

    ResultadoImportacao resultado = importar("A;DESPESA;8;2026;1;Nova|Antiga\n");

    assertThat(resultado.falhas().getFirst().motivo())
        .isEqualTo("A categoria \"Antiga\" está inativa e não pode ser usada");
    verify(categorias, never()).buscarOuCriar(any(), any());
  }

  @Test
  void naoDeveListarCategoriaCriadaParaLinhaRecusadaPeloCriar() {
    when(lancamentos.criar(eq(USUARIO), any()))
        .thenThrow(new RegraNegocioException("O valor deve ser maior que zero"))
        .thenReturn(null);

    ResultadoImportacao resultado =
        importar("Recusada;DESPESA;8;2026;1;Pets\nAceita;DESPESA;8;2026;2;pets|Lazer\n");

    assertThat(resultado.importados()).isEqualTo(1);
    assertThat(resultado.falhas())
        .extracting(ResultadoImportacao.Falha::motivo)
        .containsExactly("O valor deve ser maior que zero");
    assertThat(resultado.categoriasCriadas()).containsExactly("pets", "Lazer");
    // A "Pets" da linha recusada foi desfeita com ela: não fica no cache e é criada de novo
    verify(categorias).buscarOuCriar(USUARIO, "Pets");
    verify(categorias).buscarOuCriar(USUARIO, "pets");
  }

  @Test
  void deveGravarCategoriasELancamentoDentroDaTransacaoDaLinha() {
    List<String> chamadasNaTransacao = new ArrayList<>();
    TransactionOperations transacao =
        new TransactionOperations() {
          @Override
          public <T> T execute(TransactionCallback<T> acao) {
            chamadasNaTransacao.add("inicio");
            T resultado = acao.doInTransaction(null);
            chamadasNaTransacao.add("fim");
            return resultado;
          }
        };
    when(categorias.buscarOuCriar(eq(USUARIO), anyString()))
        .thenAnswer(i -> {
          chamadasNaTransacao.add("categoria");
          return new CategoriaResumo(50L, i.getArgument(1), true);
        });
    when(lancamentos.criar(eq(USUARIO), any()))
        .thenAnswer(i -> {
          chamadasNaTransacao.add("lancamento");
          return null;
        });
    service =
        new ImportacaoService(
            new LeitorCsv(), new ConversorLinha(), categorias, lancamentos, transacao);

    importar("A;DESPESA;8;2026;1;Pets\n");

    assertThat(chamadasNaTransacao).containsExactly("inicio", "categoria", "lancamento", "fim");
  }

  @Test
  void deveUsarMensagemDaFacadeComoMotivoEContinuar() {
    when(lancamentos.criar(eq(USUARIO), any()))
        .thenThrow(new RegraNegocioException("O valor excede o máximo permitido"))
        .thenThrow(new RecursoNaoEncontradoException("Categoria não encontrada"))
        .thenThrow(new IllegalStateException("banco"))
        .thenReturn(null);

    ResultadoImportacao resultado =
        importar(
            "A;RECEITA;8;2026;1;Salário\nB;RECEITA;8;2026;1;Salário\n"
                + "C;RECEITA;8;2026;1;Salário\nD;RECEITA;8;2026;1;Salário\n");

    assertThat(resultado.importados()).isEqualTo(1);
    assertThat(resultado.falhas())
        .extracting(ResultadoImportacao.Falha::motivo)
        .containsExactly(
            "O valor excede o máximo permitido",
            "Categoria não encontrada",
            ImportacaoService.MSG_ERRO_INESPERADO);
  }

  @Test
  void deveListarLinhaComColunasFaltando() {
    ResultadoImportacao resultado = importar("A;RECEITA;8\n");

    assertThat(resultado.falhas().getFirst().motivo()).startsWith("Faltam colunas");
  }

  @Test
  void deveRecusarArquivoInteiroSemCabecalhoAntesDeProcessar() {
    byte[] semCabecalho = "A;RECEITA;8;2026;1;Salário\n".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> service.importar(USUARIO, semCabecalho))
        .isInstanceOf(RegraNegocioException.class)
        .hasMessage(LeitorCsv.MSG_CABECALHO);
    verifyNoInteractions(lancamentos);
  }

  @Test
  void deveRecusarArquivoComMaisDe5000LinhasSemGravarNada() {
    String linhas = "A;RECEITA;8;2026;1;Salário\n".repeat(LeitorCsv.MAXIMO_LINHAS + 1);

    assertThatThrownBy(() -> importar(linhas)).hasMessage(LeitorCsv.MSG_MUITAS_LINHAS);
    verifyNoInteractions(lancamentos);
  }

  @Test
  void deveRecusarArquivoMaiorQue2MbSemGravarNada() {
    assertThatThrownBy(
            () -> service.importar(USUARIO, new byte[(int) LeitorCsv.TAMANHO_MAXIMO + 1]))
        .hasMessage(LeitorCsv.MSG_GRANDE);
    verifyNoInteractions(lancamentos, categorias);
  }
}
