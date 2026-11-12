/**
 * Conta do usuário: cadastro, login, perfil e foto, troca e recuperação de senha e LGPD (RF009,
 * RF010, RF012 a RF017).
 *
 * <p>API pública = só a pasta {@code publico} (e subpastas, cada uma com {@code @NamedInterface}),
 * quando existir. {@code endpoints}, {@code service} e {@code model} são internos. Ver CLAUDE.md.
 */
@ApplicationModule(displayName = "Usuário")
package br.com.poupamais.usuario;

import org.springframework.modulith.ApplicationModule;
