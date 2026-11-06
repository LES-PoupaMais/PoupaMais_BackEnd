package br.com.poupamais.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Segurança da API: stateless, rotas públicas de autenticação e Swagger, todo o resto autenticado.
 *
 * <p>PROVISÓRIA: o filtro JWT (JwtService, JwtAuthenticationFilter, checagem de senha_alterada_em)
 * entra no card "Implementar Serviços e Segurança".
 */
@Configuration
public class SecurityConfiguration {

  static final String[] ROTAS_PUBLICAS = {
    "/api/auth/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
  };

  @Bean
  SecurityFilterChain filtros(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            rotas -> rotas.requestMatchers(ROTAS_PUBLICAS).permitAll().anyRequest().authenticated())
        .exceptionHandling(erros ->
            erros.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .build();
  }
}
