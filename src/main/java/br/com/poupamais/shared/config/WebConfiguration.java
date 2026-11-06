package br.com.poupamais.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS. O navegador não chama a API em produção (o Next faz as chamadas pelo servidor, padrão BFF);
 * isto serve para o Swagger e testes locais. Origens em CORS_ORIGINS, separadas por vírgula.
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

  private final String[] origens;

  public WebConfiguration(@Value("${poupamais.cors-origins}") String origens) {
    this.origens = origens.split("\\s*,\\s*");
  }

  @Override
  public void addCorsMappings(CorsRegistry registro) {
    registro
        .addMapping("/api/**")
        .allowedOrigins(origens)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowCredentials(true);
  }
}
