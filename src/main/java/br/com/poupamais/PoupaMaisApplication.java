package br.com.poupamais;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Sem o usuário em memória do Spring Security: a API só autentica por JWT (shared/security), e essa
// autoconfiguração criava um usuário "user" com senha aleatória escrita no log ("Using generated
// security password"), inclusive em produção.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PoupaMaisApplication {

  public static void main(String[] args) {
    SpringApplication.run(PoupaMaisApplication.class, args);
  }
}
