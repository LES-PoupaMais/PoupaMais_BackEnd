package br.com.poupamais.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.notificacao.EmailFacade;
import br.com.poupamais.notificacao.EnvioEmailException;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Contra PostgreSQL real, ponta a ponta pela API: UNIQUE do e-mail, categorias padrão na mesma
// transação, token de recuperação, senha_alterada_em no filtro e exclusão em cascata.
// O e-mail é simulado (o link é capturado). Sem POUPAMAIS_TEST_DB_URL o teste é pulado.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class UsuarioIntegracaoTest {

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

  private String novoEmail() {
    String email = "usuario-" + UUID.randomUUID() + "@teste.com";
    emails.add(email);
    return email;
  }

  private ResultActions postar(String rota, String json, String token) throws Exception {
    var req = post(rota).contentType(MediaType.APPLICATION_JSON).content(json);
    if (token != null) {
      req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    return mvc.perform(req);
  }

  private ResultActions cadastrar(String email) throws Exception {
    return postar(
        "/api/auth/cadastro",
        "{\"nome\":\"Teste\",\"email\":\"%s\",\"senha\":\"%s\",\"aceiteTermos\":true}"
            .formatted(email, SENHA),
        null);
  }

  private String login(String email, String senha) throws Exception {
    String corpo =
        postar(
                "/api/auth/login",
                "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha),
                null)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(corpo, "$.token");
  }

  private ResultActions perfil(String token) throws Exception {
    return mvc.perform(
        get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  private Long idDo(String email) {
    return jdbc.queryForObject(
        "select id from poupamais.usuario where email = ?", Long.class, email);
  }

  private String solicitarRecuperacaoECapturarToken(String email) throws Exception {
    postar("/api/auth/recuperar-senha", "{\"email\":\"%s\"}".formatted(email), null)
        .andExpect(status().isAccepted());
    ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
    verify(emailFacade, atLeastOnce())
        .enviarRecuperacaoSenha(eq(email), anyString(), link.capture());
    String ultimo = link.getValue();
    return ultimo.substring(ultimo.indexOf("token=") + "token=".length());
  }

  private static String sha256(String texto) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
  }

  // ---------- cadastro e login ----------

  @Test
  void cadastroDeveGravarEmailMinusculoComHashECriar11Categorias() throws Exception {
    String email = novoEmail();

    cadastrar(email.toUpperCase())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email));

    Long id = idDo(email);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from poupamais.categoria where id_usuario = ?", Integer.class, id))
        .isEqualTo(11);
    assertThat(
            jdbc.queryForObject(
                "select senha from poupamais.usuario where id = ?", String.class, id))
        .startsWith("$2")
        .isNotEqualTo(SENHA);
    assertThat(
            jdbc.queryForObject(
                "select aceite_termos_em is not null from poupamais.usuario where id = ?",
                Boolean.class,
                id))
        .isTrue();
  }

  @Test
  void emailDeveSerUnicoSemDiferenciarMaiusculas() throws Exception {
    String email = novoEmail();
    cadastrar(email).andExpect(status().isCreated());

    cadastrar(email.toUpperCase()).andExpect(status().isConflict());

    assertThat(
            jdbc.queryForObject(
                "select count(*) from poupamais.usuario where lower(email) = ?",
                Integer.class,
                email))
        .isEqualTo(1);
  }

  @Test
  void loginDeveAceitarEmailEmQualquerCaixaERecusarSenhaErrada() throws Exception {
    String email = novoEmail();
    cadastrar(email);

    String token = login(email.toUpperCase(), SENHA);

    perfil(token).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    postar("/api/auth/login", "{\"email\":\"%s\",\"senha\":\"errada\"}".formatted(email), null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos"));
  }

  // ---------- recuperação de senha ----------

  @Test
  void tokenDeRecuperacaoDeveSerGuardadoComoHashComValidadeDe30MinEUsoUnico() throws Exception {
    String email = novoEmail();
    cadastrar(email);
    String tokenAntigoJwt = login(email, SENHA);
    Instant antes = Instant.now();

    String token = solicitarRecuperacaoECapturarToken(email);

    Long id = idDo(email);
    var linha =
        jdbc.queryForMap(
            "select token_hash, expira_em from poupamais.token_recuperacao_senha"
                + " where id_usuario = ? and usado_em is null",
            id);
    assertThat(linha.get("token_hash")).isEqualTo(sha256(token)).isNotEqualTo(token);
    Instant expira = ((Timestamp) linha.get("expira_em")).toInstant();
    assertThat(Duration.between(antes, expira))
        .isBetween(Duration.ofMinutes(29), Duration.ofMinutes(31));

    Thread.sleep(1100); // senha_alterada_em tem precisão de segundos
    postar(
            "/api/auth/redefinir-senha",
            "{\"token\":\"%s\",\"novaSenha\":\"novaSenha1\"}".formatted(token),
            null)
        .andExpect(status().isNoContent());

    postar(
            "/api/auth/redefinir-senha",
            "{\"token\":\"%s\",\"novaSenha\":\"outraSenha\"}".formatted(token),
            null)
        .andExpect(status().isUnprocessableEntity());
    perfil(tokenAntigoJwt).andExpect(status().isUnauthorized());
    perfil(login(email, "novaSenha1")).andExpect(status().isOk());
  }

  @Test
  void novoPedidoDeveInvalidarOTokenAnterior() throws Exception {
    String email = novoEmail();
    cadastrar(email);
    String primeiro = solicitarRecuperacaoECapturarToken(email);

    String segundo = solicitarRecuperacaoECapturarToken(email);

    assertThat(segundo).isNotEqualTo(primeiro);
    postar(
            "/api/auth/redefinir-senha",
            "{\"token\":\"%s\",\"novaSenha\":\"novaSenha1\"}".formatted(primeiro),
            null)
        .andExpect(status().isUnprocessableEntity());
    postar(
            "/api/auth/redefinir-senha",
            "{\"token\":\"%s\",\"novaSenha\":\"novaSenha1\"}".formatted(segundo),
            null)
        .andExpect(status().isNoContent());
  }

  @Test
  void tokenExpiradoDeveSerRecusado() throws Exception {
    String email = novoEmail();
    cadastrar(email);
    String token = solicitarRecuperacaoECapturarToken(email);
    jdbc.update(
        "update poupamais.token_recuperacao_senha set expira_em = now() - interval '1 second'"
            + " where id_usuario = ?",
        idDo(email));

    postar(
            "/api/auth/redefinir-senha",
            "{\"token\":\"%s\",\"novaSenha\":\"novaSenha1\"}".formatted(token),
            null)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.mensagem")
                .value("Link de redefinição inválido ou expirado. Solicite um novo."));
  }

  @Test
  void falhaNoEmailOuEmailInexistenteDevemTerAMesmaResposta() throws Exception {
    String email = novoEmail();
    cadastrar(email);
    doThrow(new EnvioEmailException("Falha ao enviar o e-mail", null))
        .when(emailFacade)
        .enviarRecuperacaoSenha(anyString(), anyString(), anyString());

    String comFalha =
        postar("/api/auth/recuperar-senha", "{\"email\":\"%s\"}".formatted(email), null)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String inexistente =
        postar(
                "/api/auth/recuperar-senha",
                "{\"email\":\"ninguem-" + UUID.randomUUID() + "@x.com\"}",
                null)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(comFalha).isEqualTo(inexistente).isEmpty();
  }

  // ---------- troca de senha ----------

  @Test
  void trocaDeSenhaDeveDerrubarTokenAntigoEManterONovo() throws Exception {
    String email = novoEmail();
    cadastrar(email);
    String antigo = login(email, SENHA);
    Thread.sleep(1100); // iat e senha_alterada_em têm precisão de segundos

    String corpo =
        mvc.perform(
                put("/api/usuarios/me/senha")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + antigo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"senhaAtual\":\"%s\",\"novaSenha\":\"novaSenha1\"}".formatted(SENHA)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String novo = JsonPath.read(corpo, "$.token");

    perfil(antigo).andExpect(status().isUnauthorized());
    perfil(novo).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select senha_alterada_em is not null from poupamais.usuario where email = ?",
                Boolean.class,
                email))
        .isTrue();
  }

  // ---------- foto ----------

  @Test
  void fotoDeAte2MBDeveSerSalvaELidaEAcimaDisso422() throws Exception {
    String email = novoEmail();
    cadastrar(email);
    String token = login(email, SENHA);
    byte[] png = Arrays.copyOf(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, 2 * 1024 * 1024);

    mvc.perform(
            multipart(HttpMethod.PUT, "/api/usuarios/me/foto")
                .file(new MockMultipartFile("foto", "eu.png", "image/png", png))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/usuarios/me/foto").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(content().bytes(png));
    perfil(token).andExpect(jsonPath("$.possuiFoto").value(true));

    mvc.perform(
            multipart(HttpMethod.PUT, "/api/usuarios/me/foto")
                .file(
                    new MockMultipartFile(
                        "foto", "eu.png", "image/png", Arrays.copyOf(png, png.length + 1)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            delete("/api/usuarios/me/foto").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNoContent());
    perfil(token).andExpect(jsonPath("$.possuiFoto").value(false));
  }

  // ---------- exclusão de conta ----------

  @Test
  void exclusaoDeContaDeveApagarTudoEmCascataEInvalidarOToken() throws Exception {
    String email = novoEmail();
    cadastrar(email);
    Long id = idDo(email);
    String token = login(email, SENHA);
    solicitarRecuperacaoECapturarToken(email);
    jdbc.update(
        "insert into poupamais.foto_perfil (id_usuario, conteudo, tipo_conteudo)"
            + " values (?, ?, 'image/png')",
        id,
        new byte[] {(byte) 0x89, 'P', 'N', 'G'});
    Long categoria =
        jdbc.queryForObject(
            "select min(id) from poupamais.categoria where id_usuario = ?", Long.class, id);
    Long lancamento =
        jdbc.queryForObject(
            "insert into poupamais.lancamento (id_usuario, descricao, mes, ano, valor, tipo)"
                + " values (?, 'Mercado', 8, 2026, 10.00, 'DESPESA') returning id",
            Long.class,
            id);
    jdbc.update(
        "insert into poupamais.categorias_lancamentos (id_categoria, id_lancamento, id_usuario)"
            + " values (?, ?, ?)",
        categoria,
        lancamento,
        id);

    postar("/api/usuarios/me/excluir", "{\"senha\":\"errada\"}", token)
        .andExpect(status().isUnprocessableEntity());
    postar("/api/usuarios/me/excluir", "{\"senha\":\"%s\"}".formatted(SENHA), token)
        .andExpect(status().isNoContent());

    for (String tabela :
        List.of(
            "usuario where id = ?",
            "categoria where id_usuario = ?",
            "lancamento where id_usuario = ?",
            "categorias_lancamentos where id_usuario = ?",
            "foto_perfil where id_usuario = ?",
            "token_recuperacao_senha where id_usuario = ?")) {
      assertThat(jdbc.queryForObject("select count(*) from poupamais." + tabela, Integer.class, id))
          .as(tabela)
          .isZero();
    }
    perfil(token).andExpect(status().isUnauthorized());
  }

  // ---------- Swagger ----------

  @Test
  void swaggerFicaPublicoNoPadraoDeDesenvolvimento() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }
}
