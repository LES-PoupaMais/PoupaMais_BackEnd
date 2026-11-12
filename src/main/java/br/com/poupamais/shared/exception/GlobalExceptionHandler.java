package br.com.poupamais.shared.exception;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Converte exceções no {@link ErroResponse}; nenhum controller precisa de try/catch (RNF007). */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErroResponse> validacao(MethodArgumentNotValidException ex) {
    List<ErroResponse.ErroCampo> erros =
        ex.getBindingResult().getFieldErrors().stream()
            .map(erro -> new ErroResponse.ErroCampo(erro.getField(), erro.getDefaultMessage()))
            .toList();
    return resposta(HttpStatus.BAD_REQUEST, new ErroResponse(400, "Dados inválidos", erros));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErroResponse> corpoInvalido(HttpMessageNotReadableException ex) {
    return resposta(
        HttpStatus.BAD_REQUEST,
        new ErroResponse(400, "Não foi possível ler os dados enviados. Confira o formato."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErroResponse> arquivoGrande(MaxUploadSizeExceededException ex) {
    return resposta(
        HttpStatus.UNPROCESSABLE_ENTITY,
        new ErroResponse(422, "O arquivo excede o tamanho máximo de 2 MB."));
  }

  @ExceptionHandler(RecursoNaoEncontradoException.class)
  public ResponseEntity<ErroResponse> naoEncontrado(RecursoNaoEncontradoException ex) {
    return resposta(HttpStatus.NOT_FOUND, new ErroResponse(404, ex.getMessage()));
  }

  @ExceptionHandler(ConflitoException.class)
  public ResponseEntity<ErroResponse> conflito(ConflitoException ex) {
    return resposta(HttpStatus.CONFLICT, new ErroResponse(409, ex.getMessage()));
  }

  @ExceptionHandler(RegraNegocioException.class)
  public ResponseEntity<ErroResponse> regraNegocio(RegraNegocioException ex) {
    return resposta(HttpStatus.UNPROCESSABLE_ENTITY, new ErroResponse(422, ex.getMessage()));
  }

  @ExceptionHandler(ErroAutenticacaoException.class)
  public ResponseEntity<ErroResponse> naoAutenticado(ErroAutenticacaoException ex) {
    return resposta(HttpStatus.UNAUTHORIZED, new ErroResponse(401, ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErroResponse> inesperado(Exception ex) {
    // Só o tipo e a mensagem técnica no log; nunca dados do usuário (RNF019)
    log.error("Erro inesperado: {}", ex.getClass().getName(), ex);
    return resposta(
        HttpStatus.INTERNAL_SERVER_ERROR,
        new ErroResponse(500, "Ocorreu um erro inesperado. Tente novamente em instantes."));
  }

  private static ResponseEntity<ErroResponse> resposta(HttpStatus status, ErroResponse corpo) {
    return ResponseEntity.status(status).body(corpo);
  }
}
