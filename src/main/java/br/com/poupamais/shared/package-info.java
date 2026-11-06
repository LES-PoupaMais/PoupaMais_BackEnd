/**
 * Núcleo compartilhado: segurança, exceções e handler global, configurações e value objects comuns.
 * Módulo aberto: todos os outros módulos podem usar qualquer classe daqui.
 */
@ApplicationModule(displayName = "Compartilhado", type = ApplicationModule.Type.OPEN)
package br.com.poupamais.shared;

import org.springframework.modulith.ApplicationModule;
