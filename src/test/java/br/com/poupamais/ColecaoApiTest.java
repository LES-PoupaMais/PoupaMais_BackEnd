package br.com.poupamais;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Garante que as coleções de {@code doc/api} (Postman e Bruno) cobrem todos os endpoints da API, e
 * só eles.
 *
 * <p>Endpoint novo, removido ou com caminho alterado: ajuste {@code doc/api/gerar_colecoes.py} e
 * rode {@code python3 doc/api/gerar_colecoes.py}.
 */
class ColecaoApiTest {

  private static final Path POSTMAN = Path.of("doc/api/postman/PoupaMais.postman_collection.json");
  private static final Path BRUNO = Path.of("doc/api/bruno");
  private static final String COMO_CORRIGIR =
      "Atualize doc/api/gerar_colecoes.py e rode: python3 doc/api/gerar_colecoes.py";
  private static final Pattern METODO_BRU =
      Pattern.compile("^(get|post|put|patch|delete) \\{\\s*\\n\\s*url: (\\S+)", Pattern.MULTILINE);

  private static Set<String> endpointsDaApi;

  @BeforeAll
  static void lerEndpointsDosControllers() throws ClassNotFoundException {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    endpointsDaApi = new TreeSet<>();
    for (var definicao : scanner.findCandidateComponents("br.com.poupamais")) {
      Class<?> controller = Class.forName(definicao.getBeanClassName());
      if (!ehControllerDaApi(controller)) {
        continue;
      }
      RequestMapping daClasse =
          AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
      String prefixo = daClasse == null ? "" : primeiro(daClasse.path());
      for (Method metodo : controller.getDeclaredMethods()) {
        RequestMapping mapeamento =
            AnnotatedElementUtils.findMergedAnnotation(metodo, RequestMapping.class);
        if (mapeamento == null) {
          continue;
        }
        String caminho = normalizar(prefixo + primeiro(mapeamento.path()));
        Arrays.stream(mapeamento.method())
            .forEach(m -> endpointsDaApi.add(m.name() + " " + caminho));
      }
    }
  }

  @Test
  void colecaoPostmanDeveTerTodosOsEndpointsDaApi() {
    assertThat(endpointsDaApi).as("a API deveria ter endpoints").isNotEmpty();
    assertThat(endpointsDoPostman()).as(COMO_CORRIGIR).containsAll(endpointsDaApi);
  }

  @Test
  void colecaoPostmanNaoDeveTerEndpointQueNaoExiste() {
    assertThat(endpointsDaApi).as(COMO_CORRIGIR).containsAll(endpointsDoPostman());
  }

  @Test
  void colecaoBrunoDeveTerTodosOsEndpointsDaApi() {
    assertThat(endpointsDoBruno()).as(COMO_CORRIGIR).containsAll(endpointsDaApi);
  }

  @Test
  void colecaoBrunoNaoDeveTerEndpointQueNaoExiste() {
    assertThat(endpointsDaApi).as(COMO_CORRIGIR).containsAll(endpointsDoBruno());
  }

  private static Set<String> endpointsDoPostman() {
    JsonNode colecao = new ObjectMapper().readTree(POSTMAN.toFile());
    Set<String> endpoints = new TreeSet<>();
    coletarPostman(colecao.get("item"), endpoints);
    return endpoints;
  }

  private static void coletarPostman(JsonNode itens, Set<String> endpoints) {
    for (JsonNode item : itens) {
      if (item.has("item")) {
        coletarPostman(item.get("item"), endpoints);
      } else {
        JsonNode requisicao = item.get("request");
        String url = requisicao.get("url").get("raw").asString();
        endpoints.add(requisicao.get("method").asString() + " " + normalizar(url));
      }
    }
  }

  private static Set<String> endpointsDoBruno() {
    Set<String> endpoints = new TreeSet<>();
    try (Stream<Path> arquivos = Files.walk(BRUNO)) {
      List<Path> requisicoes =
          arquivos
              .filter(p -> p.toString().endsWith(".bru"))
              .filter(p -> !p.getFileName().toString().equals("folder.bru"))
              .filter(p -> !p.getFileName().toString().equals("collection.bru"))
              .filter(p -> !p.getParent().getFileName().toString().equals("environments"))
              .toList();
      for (Path arquivo : requisicoes) {
        Matcher m = METODO_BRU.matcher(Files.readString(arquivo));
        assertThat(m.find()).as("método/url em %s", arquivo).isTrue();
        endpoints.add(m.group(1).toUpperCase() + " " + normalizar(m.group(2)));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return endpoints;
  }

  /**
   * Tira {@code {{baseUrl}}} e a query; troca variáveis de caminho ({id}, {{lancamentoId}}) por
   * {*}.
   */
  private static String normalizar(String url) {
    String caminho = url.replace("{{baseUrl}}", "").replaceAll("\\?.*$", "");
    caminho = caminho.replaceAll("\\{\\{[^}]+}}", "{*}").replaceAll("\\{[^}*]+}", "{*}");
    return caminho.endsWith("/") ? caminho.substring(0, caminho.length() - 1) : caminho;
  }

  /**
   * Só os controllers de produção ({@code <modulo>.endpoints}); os falsos dos testes ficam de fora.
   */
  private static boolean ehControllerDaApi(Class<?> controller) {
    String origem = controller.getProtectionDomain().getCodeSource().getLocation().toString();
    return controller.getPackageName().endsWith(".endpoints") && !origem.contains("test-classes");
  }

  private static String primeiro(String[] caminhos) {
    return caminhos.length == 0 ? "" : caminhos[0];
  }
}
