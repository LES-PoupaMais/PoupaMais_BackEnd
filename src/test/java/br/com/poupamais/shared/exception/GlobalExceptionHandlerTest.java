package br.com.poupamais.shared.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
        .andExpect(jsonPath("$.erros").doesNotExist());
  }

  @Test
  void naoEncontradoRetorna404() throws Exception {
    mvc.perform(get("/teste/404"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.mensagem").value("Lançamento não encontrado"));
  }

  @Test
  void conflitoRetorna409() throws Exception {
    mvc.perform(get("/teste/409"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.mensagem").value("E-mail já cadastrado"));
  }

  @Test
  void regraDeNegocioRetorna422() throws Exception {
    mvc.perform(get("/teste/422"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.mensagem").value("Lançamento deletado não pode ser editado"));
  }

  @Test
  void erroDeAutenticacaoRetorna401() throws Exception {
    mvc.perform(get("/teste/401"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos"));
  }

  @Test
  void arquivoGrandeRetorna422() throws Exception {
    mvc.perform(get("/teste/upload"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.mensagem").value("O arquivo excede o tamanho máximo de 2 MB."));
  }

  @Test
  void erroInesperadoRetorna500SemDetalhesInternos() throws Exception {
    mvc.perform(get("/teste/500"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(
            jsonPath("$.mensagem")
                .value("Ocorreu um erro inesperado. Tente novamente em instantes."));
  }

  record Pedido(@NotBlank(message = "O nome é obrigatório") String nome) {}

  @RestController
  static class ControllerDeTeste {

    @PostMapping("/teste")
    void validar(@Valid @RequestBody Pedido pedido) {}

    @GetMapping("/teste/{caso}")
    void lancar(@PathVariable String caso) {
      switch (caso) {
        case "401" -> throw new ErroAutenticacaoException("E-mail ou senha inválidos");
        case "404" -> throw new RecursoNaoEncontradoException("Lançamento não encontrado");
        case "409" -> throw new ConflitoException("E-mail já cadastrado");
        case "422" -> throw new RegraNegocioException("Lançamento deletado não pode ser editado");
        case "upload" -> throw new MaxUploadSizeExceededException(2_097_152);
        default -> throw new IllegalStateException("falha interna com detalhe técnico");
      }
    }
  }
}
