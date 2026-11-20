package br.com.poupamais.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

class GlobalExceptionHandlerTest {

  private MockMvc mvc;

  @BeforeEach
  void configurar() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ControllerDeTeste())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void validacaoRetorna400ComErroPorCampo() throws Exception {
    mvc.perform(post("/teste").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
        .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
        .andExpect(jsonPath("$.erros[0].campo").value("nome"))
        .andExpect(jsonPath("$.erros[0].mensagem").value("O nome é obrigatório"));
  }

  @Test
  void corpoIlegivelRetorna400SemListaDeErros() throws Exception {
    mvc.perform(post("/teste").contentType(MediaType.APPLICATION_JSON).content("{nao é json"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.mensagem")
                .value("Não foi possível ler os dados enviados. Confira o formato."))
        .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"))
        .andExpect(jsonPath("$.erros").doesNotExist());
  }

  @Test
  void naoEncontradoRetorna404() throws Exception {
    mvc.perform(get("/teste/404"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("LANCAMENTO_NAO_ENCONTRADO"))
        .andExpect(jsonPath("$.mensagem").value("Lançamento não encontrado"));
  }

  @Test
  void conflitoRetorna409() throws Exception {
    mvc.perform(get("/teste/409"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("EMAIL_JA_CADASTRADO"))
        .andExpect(jsonPath("$.mensagem").value("E-mail já cadastrado"));
  }

  @Test
  void regraDeNegocioRetorna422() throws Exception {
    mvc.perform(get("/teste/422"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("LANCAMENTO_NA_LIXEIRA"))
        .andExpect(jsonPath("$.mensagem").value("Lançamento deletado não pode ser editado"));
  }

  @Test
  void erroDeAutenticacaoRetorna401() throws Exception {
    mvc.perform(get("/teste/401"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"))
        .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos"));
  }

  @Test
  void arquivoGrandeRetorna422() throws Exception {
    mvc.perform(get("/teste/upload"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("ARQUIVO_GRANDE_DEMAIS"))
        .andExpect(jsonPath("$.mensagem").value("O arquivo excede o tamanho máximo de 2 MB."));
  }

  @Test
  void erroInesperadoRetorna500SemDetalhesInternos() throws Exception {
    mvc.perform(get("/teste/500"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
        .andExpect(
            jsonPath("$.mensagem")
                .value("Ocorreu um erro inesperado. Tente novamente em instantes."));
  }

  @Test
  void rotaInexistenteRetorna404NoPadrao() throws Exception {
    mvc.perform(get("/rota/que/nao/existe"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"))
        .andExpect(jsonPath("$.mensagem").value("Recurso não encontrado"));
  }

  @Test
  void metodoNaoSuportadoRetorna405NoPadrao() throws Exception {
    mvc.perform(delete("/teste"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.codigo").value("METODO_NAO_PERMITIDO"))
        .andExpect(jsonPath("$.mensagem").value("Método não permitido para este recurso."));
  }

  @Test
  void tipoDeConteudoNaoSuportadoRetorna415NoPadrao() throws Exception {
    mvc.perform(post("/teste").contentType(MediaType.TEXT_PLAIN).content("nome"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.status").value(415))
        .andExpect(jsonPath("$.codigo").value("TIPO_CONTEUDO_NAO_SUPORTADO"))
        .andExpect(jsonPath("$.mensagem").value(GlobalExceptionHandler.MSG_TIPO_CONTEUDO));
  }

  @Test
  void acessoNegadoRetorna403NoPadrao() throws Exception {
    mvc.perform(get("/teste/403"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"))
        .andExpect(jsonPath("$.mensagem").value(GlobalExceptionHandler.MSG_ACESSO_NEGADO));
  }

  @Test
  void dataInexistenteNoCorpoRetornaValidacaoNoCampo() throws Exception {
    mvc.perform(
            post("/teste/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"2026-02-31\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
        .andExpect(jsonPath("$.erros[0].campo").value("data"));
  }

  @Test
  void pathVariableDeTipoErradoRetorna400ComParametroInvalido() throws Exception {
    mvc.perform(get("/teste/numero/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
        .andExpect(jsonPath("$.mensagem").value("Parâmetro inválido: id. Confira o valor enviado."))
        .andExpect(jsonPath("$.erros").doesNotExist());
  }

  @Test
  void queryParamDeTipoErradoRetorna400ComParametroInvalido() throws Exception {
    mvc.perform(get("/teste/busca").param("ativa", "talvez"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
        .andExpect(
            jsonPath("$.mensagem").value("Parâmetro inválido: ativa. Confira o valor enviado."));
  }

  @Test
  void headerDeTipoErradoRetorna400ComParametroInvalido() throws Exception {
    mvc.perform(get("/teste/cabecalho").header("X-Versao", "dois"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
        .andExpect(
            jsonPath("$.mensagem").value("Parâmetro inválido: X-Versao. Confira o valor enviado."));
  }

  /** Query de objeto ({@code @ModelAttribute}) continua em VALIDACAO, com o erro no campo. */
  @Test
  void queryDeObjetoComTipoErradoContinuaEmValidacao() throws Exception {
    mvc.perform(get("/teste/periodo").param("mes", "xx"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
        .andExpect(jsonPath("$.erros[0].campo").value("mes"));
  }

  @Test
  void queryParamObrigatorioAusenteRetorna400ComParametroAusente() throws Exception {
    mvc.perform(get("/teste/busca"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.codigo").value("PARAMETRO_AUSENTE"))
        .andExpect(jsonPath("$.mensagem").value("Parâmetro obrigatório ausente: ativa."))
        .andExpect(jsonPath("$.erros").doesNotExist());
  }

  @Test
  void partDeMultipartAusenteRetorna400ComParametroAusente() throws Exception {
    mvc.perform(multipart("/teste/arquivo").param("outro", "x"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("PARAMETRO_AUSENTE"))
        .andExpect(jsonPath("$.mensagem").value("Parâmetro obrigatório ausente: arquivo."));
  }

  @Test
  void headerObrigatorioAusenteRetorna400ComParametroAusente() throws Exception {
    mvc.perform(get("/teste/cabecalho"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("PARAMETRO_AUSENTE"))
        .andExpect(jsonPath("$.mensagem").value("Parâmetro obrigatório ausente: X-Versao."));
  }

  /** Accept que a rota não produz: 406 com o corpo em JSON mesmo assim (Content-Type fixado). */
  @Test
  void acceptNaoAtendidoRetorna406EmJsonMesmoSemOClienteAceitarJson() throws Exception {
    mvc.perform(get("/produz/json").accept(MediaType.TEXT_PLAIN))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(406))
        .andExpect(jsonPath("$.codigo").value("TIPO_RESPOSTA_NAO_ACEITO"))
        .andExpect(jsonPath("$.mensagem").value(GlobalExceptionHandler.MSG_TIPO_RESPOSTA));
  }

  /** Sem {@code produces}, o 406 só aparece ao escrever o retorno: o handler também o trata. */
  @Test
  void acceptNaoAtendidoAoEscreverORetornoRetorna406EmJson() throws Exception {
    mvc.perform(get("/teste/objeto").accept("text/csv"))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.codigo").value("TIPO_RESPOSTA_NAO_ACEITO"));
  }

  /**
   * Erro de qualquer handler, pedido com um {@code Accept} que exclui JSON (ex.: quem baixa um
   * CSV), sai mesmo assim em JSON com o status do erro e {@code codigo} — não vira 406 nem 500.
   */
  @Test
  void todoErroSaiEmJsonComCodigoMesmoComAcceptNaoJson() throws Exception {
    for (String accept : List.of("text/csv", "application/octet-stream", "text/plain")) {
      for (String caso : List.of("401", "403", "404", "409", "422", "upload", "500")) {
        mvc.perform(get("/teste/" + caso).accept(accept))
            .andExpect(status().is(caso.equals("upload") ? 422 : Integer.parseInt(caso)))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.codigo").isNotEmpty());
      }
      mvc.perform(
              post("/teste").contentType(MediaType.APPLICATION_JSON).content("{}").accept(accept))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
          .andExpect(jsonPath("$.erros[0].campo").value("nome"));
      mvc.perform(get("/teste/numero/abc").accept(accept))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
      mvc.perform(get("/rota/que/nao/existe").accept(accept))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }
  }

  /**
   * Nenhum caminho de erro sai sem {@code codigo}: todo handler devolve {@link ErroResponse} (que
   * recusa código nulo) e cada caso de erro do controller de teste responde com {@code codigo}.
   */
  @Test
  void nenhumCaminhoDeErroSaiSemCodigo() throws Exception {
    List<Method> handlers =
        Arrays.stream(GlobalExceptionHandler.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(ExceptionHandler.class))
            .toList();
    assertThat(handlers).isNotEmpty();
    for (Method handler : handlers) {
      assertThat(handler.getGenericReturnType().getTypeName())
          .as(handler.getName())
          .isEqualTo(ResponseEntity.class.getName() + "<" + ErroResponse.class.getName() + ">");
    }
    assertThatThrownBy(() -> new ErroResponse(400, null, "x"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new RegraNegocioException(null, "x"))
        .isInstanceOf(NullPointerException.class);

    for (String caso : List.of("401", "403", "404", "409", "422", "upload", "500")) {
      mvc.perform(get("/teste/" + caso)).andExpect(jsonPath("$.codigo").isNotEmpty());
    }
    mvc.perform(post("/teste").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(jsonPath("$.codigo").isNotEmpty());
    mvc.perform(post("/teste").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(jsonPath("$.codigo").isNotEmpty());
    mvc.perform(post("/teste").contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(jsonPath("$.codigo").isNotEmpty());
    mvc.perform(delete("/teste")).andExpect(jsonPath("$.codigo").isNotEmpty());
    mvc.perform(get("/rota/que/nao/existe")).andExpect(jsonPath("$.codigo").isNotEmpty());
    mvc.perform(get("/teste/numero/abc")).andExpect(jsonPath("$.codigo").isNotEmpty());
    mvc.perform(get("/teste/busca")).andExpect(jsonPath("$.codigo").isNotEmpty());
    mvc.perform(multipart("/teste/arquivo")).andExpect(jsonPath("$.codigo").isNotEmpty());
    mvc.perform(get("/produz/json").accept(MediaType.TEXT_PLAIN))
        .andExpect(jsonPath("$.codigo").isNotEmpty());
  }

  record ComData(LocalDate data) {}

  record Pedido(@NotBlank(message = "O nome é obrigatório") String nome) {}

  record Periodo(Integer mes) {}

  @RestController
  static class ControllerDeTeste {

    @PostMapping("/teste")
    void validar(@Valid @RequestBody Pedido pedido) {}

    @PostMapping("/teste/data")
    void data(@RequestBody ComData corpo) {}

    @GetMapping("/teste/numero/{id}")
    void numero(@PathVariable Long id) {}

    @GetMapping("/teste/busca")
    void busca(@RequestParam Boolean ativa) {}

    @GetMapping("/teste/cabecalho")
    void cabecalho(@RequestHeader("X-Versao") Integer versao) {}

    @GetMapping("/teste/periodo")
    void periodo(@ModelAttribute Periodo periodo) {}

    @PostMapping("/teste/arquivo")
    void arquivo(@RequestPart("arquivo") MultipartFile arquivo) {}

    @GetMapping(path = "/produz/json", produces = MediaType.APPLICATION_JSON_VALUE)
    Pedido json() {
      return new Pedido("x");
    }

    @GetMapping("/teste/objeto")
    Pedido objeto() {
      return new Pedido("x");
    }

    @GetMapping("/teste/{caso}")
    void lancar(@PathVariable String caso) {
      switch (caso) {
        case "401" ->
            throw new ErroAutenticacaoException(
                CodigoErro.CREDENCIAIS_INVALIDAS, "E-mail ou senha inválidos");
        case "403" -> throw new AccessDeniedException("negado");
        case "404" ->
            throw new RecursoNaoEncontradoException(
                CodigoErro.LANCAMENTO_NAO_ENCONTRADO, "Lançamento não encontrado");
        case "409" ->
            throw new ConflitoException(CodigoErro.EMAIL_JA_CADASTRADO, "E-mail já cadastrado");
        case "422" ->
            throw new RegraNegocioException(
                CodigoErro.LANCAMENTO_NA_LIXEIRA, "Lançamento deletado não pode ser editado");
        case "upload" -> throw new MaxUploadSizeExceededException(2_097_152);
        default -> throw new IllegalStateException("falha interna com detalhe técnico");
      }
    }
  }
}
