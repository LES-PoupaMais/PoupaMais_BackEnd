package br.com.poupamais.shared.exception;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Substitui o {@code /error} padrão do Spring Boot: erro que não passa pelo {@link
 * GlobalExceptionHandler} (falha num filtro, recusa do servidor de aplicação, como 413) também sai
 * no {@link ErroResponse}, com {@code codigo}. Sem status no despacho (acesso direto a {@code
 * /error}) responde 404, como uma rota inexistente.
 */
@Hidden
@RestController
public class ErroPadraoController implements ErrorController {

  @RequestMapping("${server.error.path:/error}")
  public ResponseEntity<ErroResponse> erro(HttpServletRequest request) {
    Object atributo = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int status = atributo instanceof Integer s && s >= 400 ? s : 404;
    if (status >= 500) {
      status = 500;
    }
    CodigoErro codigo = CodigoErro.padraoDoStatus(status);
    return new ErroResponse(status, codigo, mensagem(codigo)).comoResposta();
  }

  static String mensagem(CodigoErro codigo) {
    return switch (codigo) {
      case NAO_AUTENTICADO -> "Sessão inválida ou expirada. Faça login novamente.";
      case ACESSO_NEGADO -> GlobalExceptionHandler.MSG_ACESSO_NEGADO;
      case NAO_ENCONTRADO -> "Recurso não encontrado";
      case METODO_NAO_PERMITIDO -> "Método não permitido para este recurso.";
      case ARQUIVO_GRANDE_DEMAIS -> "O arquivo excede o tamanho máximo de 2 MB.";
      case TIPO_CONTEUDO_NAO_SUPORTADO -> GlobalExceptionHandler.MSG_TIPO_CONTEUDO;
      case ERRO_INTERNO -> GlobalExceptionHandler.MSG_ERRO_INTERNO;
      default -> "Não foi possível processar a requisição. Confira os dados enviados.";
    };
  }
}
