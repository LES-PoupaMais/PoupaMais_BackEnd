/**
 * Importação CSV e exportação CSV/XLSX de lançamentos (RF003, RF007, RN007, RN012, RN013).
 *
 * <p>API pública = só a pasta {@code publico} (e subpastas, cada uma com {@code @NamedInterface}),
 * quando existir. {@code endpoints}, {@code service} e {@code model} são internos. Ver CLAUDE.md.
 */
@ApplicationModule(displayName = "Arquivo")
package br.com.poupamais.arquivo;

import org.springframework.modulith.ApplicationModule;
