/**
 * Envio de e-mails transacionais (RNF020).
 *
 * <p>API pública = só a pasta {@code publico} (e subpastas, cada uma com {@code @NamedInterface}),
 * quando existir. {@code endpoints}, {@code service} e {@code model} são internos. Ver CLAUDE.md.
 */
@ApplicationModule(displayName = "Notificação")
package br.com.poupamais.notificacao;

import org.springframework.modulith.ApplicationModule;
