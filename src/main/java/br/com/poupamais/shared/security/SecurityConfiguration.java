package br.com.poupamais.shared.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Segurança da API: JWT stateless, CSRF desligado, rotas públicas do contrato (cadastro, login,
 * recuperação de senha e, se {@code SWAGGER_HABILITADO=true}, o Swagger), todo o resto autenticado.
 * CORS vem do {@code WebConfiguration}.
 */
@Configuration
public class SecurityConfiguration {

  static final String[] ROTAS_PUBLICAS_POST = {
    "/api/auth/cadastro",
    "/api/auth/login",
    "/api/auth/recuperar-senha",
    "/api/auth/redefinir-senha"
  };

  static final String[] ROTAS_SWAGGER = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

  @Bean
  SecurityFilterChain filtros(
      HttpSecurity http,
      JwtService jwtService,
      ObjectProvider<SenhaAlteradaConsulta> senhaAlteradaConsulta,
      @Value("${poupamais.swagger-habilitado:true}") boolean swaggerHabilitado)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(rotas -> {
          rotas.requestMatchers(HttpMethod.POST, ROTAS_PUBLICAS_POST).permitAll();
          if (swaggerHabilitado) {
            rotas.requestMatchers(ROTAS_SWAGGER).permitAll();
          }
          rotas.anyRequest().authenticated();
        })
        .exceptionHandling(
            erros -> erros.authenticationEntryPoint(new ErroAutenticacaoEntryPoint()))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService, senhaAlteradaConsulta),
            UsernamePasswordAuthenticationFilter.class)
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .build();
  }

  /** BCrypt para hash de senha (RN011). Injete {@link PasswordEncoder} nos services. */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
