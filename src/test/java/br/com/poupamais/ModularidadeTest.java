package br.com.poupamais;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Garante as fronteiras do monólito modular: um módulo só usa a pasta publico/ de outro (nunca
 * endpoints/, service/ ou model/), e cada subpasta de publico/ precisa do próprio @NamedInterface;
 * e não há dependência cíclica entre módulos.
 */
class ModularidadeTest {

  @Test
  void modulosRespeitamAsFronteiras() {
    ApplicationModules.of(PoupaMaisApplication.class).verify();
  }
}
