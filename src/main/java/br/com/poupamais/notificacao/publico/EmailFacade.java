package br.com.poupamais.notificacao.publico;

import br.com.poupamais.notificacao.publico.excecoes.EnvioEmailException;

/**
 * API pública do módulo de notificação: envio de e-mails transacionais (RNF020).
 *
 * <p>Os métodos expressam intenções de negócio. Em falha de envio, lançam {@link
 * EnvioEmailException} (sem dados sensíveis na mensagem). O chamador decide como reagir.
 */
public interface EmailFacade {

  /**
   * Envia o e-mail de recuperação de senha (RF012, RN014), informando a validade de 30 minutos.
   *
   * <p><b>Importante (RN014):</b> a resposta da recuperação de senha deve ser sempre igual, exista
   * ou não o e-mail. Quem chama <b>não deve</b> propagar {@link EnvioEmailException} ao usuário:
   * capture-a, registre o ocorrido sem o e-mail completo e sem o link, e responda normalmente.
   *
   * @param emailDestino endereço do destinatário
   * @param nome nome do usuário, exibido na saudação (escapado no HTML)
   * @param linkRedefinicao link completo, já montado por quem chama (contém o token: nunca logar)
   * @throws EnvioEmailException se o servidor de e-mail recusar ou estiver indisponível
   */
  void enviarRecuperacaoSenha(String emailDestino, String nome, String linkRedefinicao);
}
