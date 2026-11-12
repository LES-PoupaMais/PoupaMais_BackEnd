/**
 * Categorias do usuário: CRUD, inativar e reativar, categorias padrão (RF011, RN002).
 *
 * <p>API pública = só a pasta {@code publico} (e subpastas, cada uma com {@code @NamedInterface}),
 * quando existir. {@code endpoints}, {@code service} e {@code model} são internos. Ver CLAUDE.md.
 */
@ApplicationModule(displayName = "Categoria")
package br.com.poupamais.categoria;

import org.springframework.modulith.ApplicationModule;
