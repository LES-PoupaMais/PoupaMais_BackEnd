/**
 * Receitas e despesas: CRUD, filtros e status ATIVO/INATIVO/DELETADO (RF001 a RF005, RF008).
 *
 * <p>API pública = só a pasta {@code publico} (e subpastas, cada uma com {@code @NamedInterface}),
 * quando existir. {@code endpoints}, {@code service} e {@code model} são internos. Ver CLAUDE.md.
 */
@ApplicationModule(displayName = "Lançamento")
package br.com.poupamais.lancamento;

import org.springframework.modulith.ApplicationModule;
