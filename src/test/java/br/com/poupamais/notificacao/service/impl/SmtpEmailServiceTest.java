package br.com.poupamais.notificacao.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.poupamais.notificacao.publico.excecoes.EnvioEmailException;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailServiceTest {

  private static final String REMETENTE = "PoupaMais <nao-responda@poupamais.local>";
  private static final String LINK = "http://localhost:3000/redefinir-senha?token=abc123";

  private JavaMailSender mailSender;
  private SmtpEmailService service;

  @BeforeEach
  void configurar() {
    mailSender = mock(JavaMailSender.class);
    when(mailSender.createMimeMessage())
        .thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    service = new SmtpEmailService(mailSender, REMETENTE);
  }

  private MimeMessage enviada() {
    var captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    return captor.getValue();
  }

  private static void coletar(Part parte, List<String> html, List<String> texto) throws Exception {
    if (parte.isMimeType("text/html")) {
      html.add((String) parte.getContent());
    } else if (parte.isMimeType("text/plain")) {
      texto.add((String) parte.getContent());
    } else if (parte.getContent() instanceof Multipart mp) {
      for (int i = 0; i < mp.getCount(); i++) {
        coletar(mp.getBodyPart(i), html, texto);
      }
    }
  }

  @Test
  void deveEnviarRecuperacaoComDestinatarioRemetenteEAssunto() throws Exception {
    service.enviarRecuperacaoSenha("ana@exemplo.com", "Ana", LINK);

    MimeMessage msg = enviada();
    assertThat(msg.getRecipients(Message.RecipientType.TO)[0].toString())
        .isEqualTo("ana@exemplo.com");
    assertThat(msg.getFrom()[0].toString()).contains("nao-responda@poupamais.local");
    assertThat(msg.getSubject()).isEqualTo(SmtpEmailService.ASSUNTO_RECUPERACAO);
  }

  @Test
  void deveIncluirNomeLinkEValidadeNoHtmlENoTexto() throws Exception {
    service.enviarRecuperacaoSenha("ana@exemplo.com", "Ana", LINK);

    msgPartes(
        enviada(),
        html -> {
          assertThat(html).contains("Ana").contains(LINK).contains("30 minutos");
        },
        texto -> {
          assertThat(texto).contains("Ana").contains(LINK).contains("30 minutos");
        });
  }

  @Test
  void deveEscaparHtmlDoNomeContraInjecao() throws Exception {
    service.enviarRecuperacaoSenha("ana@exemplo.com", "<script>alert(1)</script>", LINK);

    msgPartes(
        enviada(),
        html -> {
          assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
        },
        texto -> assertThat(texto).contains("<script>alert(1)</script>"));
  }

  @Test
  void deveLancarEnvioEmailExceptionSemDadosSensiveisQuandoServidorFalhar() {
    doThrow(new MailSendException("smtp fora")).when(mailSender).send(any(MimeMessage.class));

    assertThatThrownBy(() -> service.enviarRecuperacaoSenha("ana@exemplo.com", "Ana", LINK))
        .isInstanceOf(EnvioEmailException.class)
        .hasMessage("Não foi possível enviar o e-mail.")
        .hasCauseInstanceOf(MailSendException.class);
  }

  @Test
  void deveTratarValorNuloComoVazioAoPreencher() {
    var valores = new java.util.HashMap<String, String>();
    valores.put("nome", null);
    assertThat(SmtpEmailService.preencher("Olá, {{nome}}!", valores, true)).isEqualTo("Olá, !");
  }

  private interface Verificacao {
    void verificar(String conteudo);
  }

  private static void msgPartes(MimeMessage msg, Verificacao noHtml, Verificacao noTexto)
      throws Exception {
    msg.saveChanges();
    List<String> html = new ArrayList<>();
    List<String> texto = new ArrayList<>();
    coletar(msg, html, texto);
    assertThat(html).hasSize(1);
    assertThat(texto).hasSize(1);
    noHtml.verificar(html.get(0));
    noTexto.verificar(texto.get(0));
  }
}
