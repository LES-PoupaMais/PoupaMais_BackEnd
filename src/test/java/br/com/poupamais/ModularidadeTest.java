package br.com.poupamais;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Garante as fronteiras do monólito modular: nenhum módulo acessa api/, service/ ou model/ de
 * outro, e não há dependência cíclica entre módulos.
 */
class ModularidadeTest {

  @Test
  void modulosRespeitamAsFronteiras() {
    ApplicationModules.of(PoupaMaisApplication.class).verify();
  }
}
