package br.com.poupamais.notificacao.service.impl;

import br.com.poupamais.notificacao.EmailFacade;
import br.com.poupamais.notificacao.EnvioEmailException;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Implementação SMTP da {@link EmailFacade}. Monta HTML + texto alternativo a partir dos templates
 * em {@code classpath:templates/email/}, substituindo placeholders {@code {{chave}}} (escapados no
 * HTML). Nunca registra destinatário, link ou token em log (RNF019).
 */
@Slf4j
@Service
public class SmtpEmailService implements EmailFacade {

  static final String ASSUNTO_RECUPERACAO = "PoupaMais: redefinição de senha";

  private final JavaMailSender mailSender;
  private final String remetente;
  private final Map<String, String> cacheTemplates = new ConcurrentHashMap<>();

  public SmtpEmailService(
      JavaMailSender mailSender, @Value("${poupamais.mail.remetente}") String remetente) {
    this.mailSender = mailSender;
    this.remetente = remetente;
  }

  @Override
  public void enviarRecuperacaoSenha(String emailDestino, String nome, String linkRedefinicao) {
    Map<String, String> valores = Map.of("nome", nome, "link", linkRedefinicao);
    enviar(emailDestino, ASSUNTO_RECUPERACAO, "recuperacao-senha", valores);
  }

  private void enviar(
      String destino, String assunto, String template, Map<String, String> valores) {
    String html = preencher(carregar(template + ".html"), valores, true);
    String texto = preencher(carregar(template + ".txt"), valores, false);
    try {
      var mensagem = mailSender.createMimeMessage();
      var helper = new MimeMessageHelper(mensagem, true, StandardCharsets.UTF_8.name());
      helper.setFrom(remetente);
      helper.setTo(destino);
      helper.setSubject(assunto);
      helper.setText(texto, html);
      mailSender.send(mensagem);
    } catch (MailException | MessagingException e) {
      log.warn("Falha ao enviar e-mail '{}': {}", template, e.getClass().getSimpleName());
      throw new EnvioEmailException("Não foi possível enviar o e-mail.", e);
    }
  }

  static String preencher(String modelo, Map<String, String> valores, boolean escaparHtml) {
    String resultado = modelo;
    for (var entrada : valores.entrySet()) {
      String valor = entrada.getValue() == null ? "" : entrada.getValue();
      resultado =
          resultado.replace(
              "{{" + entrada.getKey() + "}}", escaparHtml ? HtmlUtils.htmlEscape(valor) : valor);
    }
    return resultado;
  }

  private String carregar(String arquivo) {
    return cacheTemplates.computeIfAbsent(
        arquivo,
        a -> {
          try (InputStream in = new ClassPathResource("templates/email/" + a).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
          } catch (IOException e) {
            throw new UncheckedIOException("Template de e-mail ausente: " + a, e);
          }
        });
  }
}
