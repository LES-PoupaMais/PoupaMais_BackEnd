package br.com.poupamais.arquivo.exportacao;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Factory: escolhe o {@link ExportadorLancamentos} pelo {@code formato}. Formato novo = 1 bean. */
@Component
public class ExportadorFactory {

  private final Map<String, ExportadorLancamentos> exportadores;

  public ExportadorFactory(List<ExportadorLancamentos> exportadores) {
    this.exportadores =
        exportadores.stream()
            .collect(Collectors.toMap(ExportadorLancamentos::formato, Function.identity()));
  }

  public ExportadorLancamentos para(String formato) {
    ExportadorLancamentos exportador =
        formato == null ? null : exportadores.get(formato.strip().toLowerCase(Locale.ROOT));
    if (exportador == null) {
      throw new FormatoInvalidoException(
          "Formato inválido. Use: "
              + String.join(" ou ", exportadores.keySet().stream().sorted().toList()));
    }
    return exportador;
  }
}
