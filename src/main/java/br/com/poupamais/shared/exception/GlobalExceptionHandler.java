package br.com.poupamais.shared.exception;

import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.MismatchedInputException;

/** Converte exceções no {@link ErroResponse}; nenhum controller precisa de try/catch (RNF007). */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  static final String MSG_ERRO_INTERNO =
      "Ocorreu um erro inesperado. Tente novamente em instantes.";
  static final String MSG_TIPO_CONTEUDO =
      "Formato de conteúdo não suportado nesta rota. Confira o Content-Type.";
  static final String MSG_ACESSO_NEGADO = "Você não tem permissão para acessar este recurso.";

  static final String MSG_DATA_INVALIDA =
      "Data inválida. Use o formato AAAA-MM-DD com um dia que exista.";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErroResponse> validacao(MethodArgumentNotValidException ex) {
    List<ErroResponse.ErroCampo> erros =
        ex.getBindingResult().getFieldErrors().stream()
            .map(erro -> new ErroResponse.ErroCampo(erro.getField(), erro.getDefaultMessage()))
            .toList();
    return resposta(
        HttpStatus.BAD_REQUEST,
        new ErroResponse(400, CodigoErro.VALIDACAO, "Dados inválidos", erros));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErroResponse> corpoInvalido(HttpMessageNotReadableException ex) {
    // Data que não existe ou fora do formato ISO (ex.: "2026-02-31"): erro no campo, como a
    // validação
    if (ex.getCause() instanceof MismatchedInputException mi
        && mi.getTargetType() == LocalDate.class
        && !mi.getPath().isEmpty()) {
      String campo = mi.getPath().getLast().getPropertyName();
      return resposta(
          HttpStatus.BAD_REQUEST,
          new ErroResponse(
              400,
              CodigoErro.VALIDACAO,
              "Dados inválidos",
              List.of(new ErroResponse.ErroCampo(campo, MSG_DATA_INVALIDA))));
    }
    return resposta(
        HttpStatus.BAD_REQUEST,
        new ErroResponse(
            400,
            CodigoErro.CORPO_INVALIDO,
            "Não foi possível ler os dados enviados. Confira o formato."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErroResponse> arquivoGrande(MaxUploadSizeExceededException ex) {
    return resposta(
        HttpStatus.UNPROCESSABLE_ENTITY,
        new ErroResponse(
            422, CodigoErro.ARQUIVO_GRANDE_DEMAIS, "O arquivo excede o tamanho máximo de 2 MB."));
  }

  @ExceptionHandler(RecursoNaoEncontradoException.class)
  public ResponseEntity<ErroResponse> naoEncontrado(RecursoNaoEncontradoException ex) {
    return resposta(HttpStatus.NOT_FOUND, new ErroResponse(404, ex.codigo(), ex.getMessage()));
  }

  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  public ResponseEntity<ErroResponse> rotaInexistente(Exception ex) {
    return resposta(
        HttpStatus.NOT_FOUND,
        new ErroResponse(404, CodigoErro.NAO_ENCONTRADO, "Recurso não encontrado"));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErroResponse> metodoNaoSuportado(
      HttpRequestMethodNotSupportedException ex) {
    return resposta(
        HttpStatus.METHOD_NOT_ALLOWED,
        new ErroResponse(
            405, CodigoErro.METODO_NAO_PERMITIDO, "Método não permitido para este recurso."));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErroResponse> tipoConteudoNaoSuportado(
      HttpMediaTypeNotSupportedException ex) {
    return resposta(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        new ErroResponse(415, CodigoErro.TIPO_CONTEUDO_NAO_SUPORTADO, MSG_TIPO_CONTEUDO));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErroResponse> acessoNegado(AccessDeniedException ex) {
    return resposta(
        HttpStatus.FORBIDDEN, new ErroResponse(403, CodigoErro.ACESSO_NEGADO, MSG_ACESSO_NEGADO));
  }

  @ExceptionHandler(ConflitoException.class)
  public ResponseEntity<ErroResponse> conflito(ConflitoException ex) {
    return resposta(HttpStatus.CONFLICT, new ErroResponse(409, ex.codigo(), ex.getMessage()));
  }

  @ExceptionHandler(RegraNegocioException.class)
  public ResponseEntity<ErroResponse> regraNegocio(RegraNegocioException ex) {
    return resposta(
        HttpStatus.UNPROCESSABLE_ENTITY, new ErroResponse(422, ex.codigo(), ex.getMessage()));
  }

  @ExceptionHandler(ErroAutenticacaoException.class)
  public ResponseEntity<ErroResponse> naoAutenticado(ErroAutenticacaoException ex) {
    return resposta(HttpStatus.UNAUTHORIZED, new ErroResponse(401, ex.codigo(), ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErroResponse> inesperado(Exception ex) {
    // Só o tipo e a mensagem técnica no log; nunca dados do usuário (RNF019)
    log.error("Erro inesperado: {}", ex.getClass().getName(), ex);
    return resposta(
        HttpStatus.INTERNAL_SERVER_ERROR,
        new ErroResponse(500, CodigoErro.ERRO_INTERNO, MSG_ERRO_INTERNO));
  }

  private static ResponseEntity<ErroResponse> resposta(HttpStatus status, ErroResponse corpo) {
    return ResponseEntity.status(status).body(corpo);
  }
}
