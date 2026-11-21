package br.com.poupamais.shared.config;

import br.com.poupamais.shared.exception.CodigoErro;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cabeçalho do Swagger/OpenAPI: versão do contrato e a tabela de códigos de erro (gerada do {@link
 * CodigoErro}, então não fica desatualizada).
 */
@Configuration
public class OpenApiConfiguration {

  static final String VERSAO_CONTRATO = "1.2.0";

  @Bean
  OpenAPI openApi() {
    return new OpenAPI()
        .info(new Info().title("PoupaMais API").version(VERSAO_CONTRATO).description(descricao()));
  }

  static String descricao() {
    String linhas =
        Arrays.stream(CodigoErro.values())
            .map(c -> "| `" + c.name() + "` | " + c.status() + " | " + c.quando() + " |")
            .collect(Collectors.joining("\n"));
    return """
    API REST do PoupaMais. Toda resposta de erro tem o formato \
    `{status, codigo, mensagem, erros?}`: `codigo` é estável (o front decide por ele) e \
    `mensagem` é o texto pronto para exibir. `erros[{campo, mensagem}]` só vem em `VALIDACAO`.

    | Código | Status | Quando |
    | --- | --- | --- |
    """
        + linhas;
  }
}
