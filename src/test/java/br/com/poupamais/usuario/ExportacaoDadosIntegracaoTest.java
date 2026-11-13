package br.com.poupamais.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.notificacao.publico.EmailFacade;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Contra PostgreSQL real, ponta a ponta pela API: a exportação LGPD (RF016) traz categorias ativas
// e
// inativas e lançamentos nos três status, e nada de outro usuário (RN005). Sem
// POUPAMAIS_TEST_DB_URL
// o teste é pulado.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class ExportacaoDadosIntegracaoTest {

  @DynamicPropertySource
  static void banco(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", () -> System.getenv("POUPAMAIS_TEST_DB_URL"));
  }

  private static final String SENHA = "senha123";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private EmailFacade emailFacade;

  private final List<String> emails = new ArrayList<>();

  @AfterEach
  void limpar() {
    emails.forEach(e -> jdbc.update("delete from poupamais.usuario where email = ?", e));
  }

  // ---------- apoio ----------

  private String json(MockHttpServletRequestBuilder req, String token, String corpo, int esperado)
      throws Exception {
    req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    if (corpo != null) {
      req.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }
    return mvc.perform(req)
        .andExpect(status().is(esperado))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** Cadastra (com as 11 categorias padrão) e devolve o token. */
  private String novoUsuario(String nome) throws Exception {
    String email = "exportacao-" + UUID.randomUUID() + "@teste.com";
    emails.add(email);
    mvc.perform(
            post("/api/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nome\":\"%s\",\"email\":\"%s\",\"senha\":\"%s\",\"aceiteTermos\":true}"
                        .formatted(nome, email, SENHA)))
        .andExpect(status().isCreated());
    String corpo =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(corpo, "$.token");
  }

  private int novaCategoria(String token, String descricao) throws Exception {
    return JsonPath.read(
        json(post("/api/categorias"), token, "{\"descricao\":\"%s\"}".formatted(descricao), 201),
        "$.id");
  }

  private int novoLancamento(String token, String descricao, int... categorias) throws Exception {
    String ids = Arrays.toString(categorias);
    return JsonPath.read(
        json(
            post("/api/lancamentos"),
            token,
            ("{\"descricao\":\"%s\",\"tipo\":\"DESPESA\",\"mes\":8,\"ano\":2026,"
                    + "\"valor\":10.50,\"categoriaIds\":%s}")
                .formatted(descricao, ids),
            201),
        "$.id");
  }

  // ---------- teste ----------

  @Test
  void deveExportarTudoDoTitularENadaDeOutroUsuario() throws Exception {
    String ana = novoUsuario("Ana Exportação");
    String bia = novoUsuario("Bia Outra");
    int viagem = novaCategoria(ana, "Viagem");
    int antiga = novaCategoria(ana, "Antiga");
    int ativo = novoLancamento(ana, "Passagem", viagem, antiga);
    int inativo = novoLancamento(ana, "Hotel", viagem);
    int deletado = novoLancamento(ana, "Seguro", antiga);
    json(patch("/api/lancamentos/" + inativo + "/status"), ana, "{\"status\":\"INATIVO\"}", 200);
    json(delete("/api/lancamentos/" + deletado), ana, null, 204);
    json(patch("/api/categorias/" + antiga + "/status"), ana, "{\"ativa\":false}", 200);
    int categoriaDaBia = novaCategoria(bia, "Segredo da Bia");
    novoLancamento(bia, "Lançamento da Bia", categoriaDaBia);

    String corpo =
        mvc.perform(
                get("/api/usuarios/me/dados").header(HttpHeaders.AUTHORIZATION, "Bearer " + ana))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"meus-dados-poupamais.json\""))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Perfil sem dados sensíveis
    assertThat((String) JsonPath.read(corpo, "$.perfil.nome")).isEqualTo("Ana Exportação");
    assertThat((String) JsonPath.read(corpo, "$.perfil.email")).isEqualTo(emails.get(0));
    assertThat((String) JsonPath.read(corpo, "$.perfil.aceiteTermosEm")).isNotBlank();
    assertThat((Boolean) JsonPath.read(corpo, "$.perfil.possuiFoto")).isFalse();
    assertThat((Map<String, Object>) JsonPath.read(corpo, "$.perfil"))
        .containsOnlyKeys("id", "nome", "email", "dataCadastro", "aceiteTermosEm", "possuiFoto");
    assertThat(corpo).doesNotContain("senha").doesNotContain("$2a$");
    assertThat((String) JsonPath.read(corpo, "$.geradoEm")).endsWith("Z");

    // 11 padrão + 2 criadas, ativas e inativas
    List<String> categorias = JsonPath.read(corpo, "$.categorias[*].descricao");
    assertThat(categorias)
        .hasSize(13)
        .contains("Viagem", "Antiga")
        .doesNotContain("Segredo da Bia");
    List<Boolean> antigaAtiva =
        JsonPath.read(corpo, "$.categorias[?(@.descricao == 'Antiga')].ativa");
    assertThat(antigaAtiva).containsExactly(false);

    // Lançamentos nos três status, com categorias; nada da Bia
    List<Integer> ids = JsonPath.read(corpo, "$.lancamentos[*].id");
    assertThat(ids).containsExactlyInAnyOrder(ativo, inativo, deletado);
    assertThat((List<String>) JsonPath.read(corpo, "$.lancamentos[*].status"))
        .containsExactlyInAnyOrder("ATIVO", "INATIVO", "DELETADO");
    assertThat(
            (List<String>)
                JsonPath.read(
                    corpo, "$.lancamentos[?(@.id == " + ativo + ")].categorias[*].descricao"))
        .containsExactly("Viagem", "Antiga");
    assertThat(corpo).doesNotContain("Lançamento da Bia");
  }
}
