package br.com.poupamais.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.categoria.publico.CategoriaFacade;
import br.com.poupamais.lancamento.publico.LancamentoFacade;
import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import br.com.poupamais.shared.security.JwtService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

// Pendência 12: linha que passa na checagem prévia, cria categoria nova e é recusada pelo
// LancamentoFacade.criar. A linha "Recusada" chama o criar real com valor zero, então quem recusa
// é o LancamentoValidator de verdade, dentro da transação da linha. Sem POUPAMAIS_TEST_DB_URL é
// pulado. O spy cria um contexto só desta classe: @DirtiesContext fecha esse contexto (e o pool de
// conexões) no fim, para não somar conexões aos contextos em cache dos outros testes.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
@DirtiesContext
class ImportacaoCategoriaOrfaIntegracaoTest {

  @DynamicPropertySource
  static void banco(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", () -> System.getenv("POUPAMAIS_TEST_DB_URL"));
  }

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CategoriaFacade categorias;
  @MockitoSpyBean private LancamentoFacade lancamentos;

  private Long usuario;
  private String bearer;

  @BeforeEach
  void dados() {
    usuario =
        jdbc.queryForObject(
            "insert into poupamais.usuario (nome, email, senha, aceite_termos_em)"
                + " values ('Teste', ?, 'x', now()) returning id",
            Long.class,
            "arquivo-orfa-" + UUID.randomUUID() + "@teste.com");
    bearer = "Bearer " + jwtService.gerar(usuario).token();
    doAnswer(
            invocacao -> {
              NovoLancamento novo = invocacao.getArgument(1);
              if (novo.descricao().equals("Recusada")) {
                invocacao.getArguments()[1] =
                    new NovoLancamento(
                        novo.descricao(),
                        novo.tipo(),
                        novo.mes(),
                        novo.ano(),
                        BigDecimal.ZERO,
                        novo.categoriaIds());
              }
              return invocacao.callRealMethod();
            })
        .when(lancamentos)
        .criar(anyLong(), any());
  }

  @AfterEach
  void limpar() {
    jdbc.update("delete from poupamais.usuario where id = ?", usuario);
  }

  private int categoriasDoUsuario() {
    return jdbc.queryForObject(
        "select count(*) from poupamais.categoria where id_usuario = ?", Integer.class, usuario);
  }

  @Test
  void naoDeveDeixarCategoriaOrfaQuandoCriarRecusaALinha() throws Exception {
    String csv =
        "descricao;tipo;mes;ano;valor;categorias\n"
            + "Ração;DESPESA;8;2026;80;Pets\n"
            + "Recusada;DESPESA;8;2026;10;Orfa|Pets\n"
            + "Passagem;DESPESA;8;2026;300;Viagem\n"
            + "Recusada;DESPESA;8;2026;10;Viagem|Outra Orfa\n";

    mvc.perform(
            multipart("/api/lancamentos/importar")
                .file(
                    new MockMultipartFile(
                        "arquivo",
                        "lancamentos.csv",
                        "text/csv",
                        csv.getBytes(StandardCharsets.UTF_8)))
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalLinhas").value(4))
        .andExpect(jsonPath("$.importados").value(2))
        .andExpect(jsonPath("$.falhas.length()").value(2))
        .andExpect(jsonPath("$.falhas[0].linha").value(3))
        .andExpect(jsonPath("$.falhas[0].motivo").value("O valor deve ser maior que zero"))
        .andExpect(jsonPath("$.falhas[1].linha").value(5))
        .andExpect(jsonPath("$.categoriasCriadas.length()").value(2))
        .andExpect(jsonPath("$.categoriasCriadas[0]").value("Pets"))
        .andExpect(jsonPath("$.categoriasCriadas[1]").value("Viagem"));

    assertThat(categorias.buscarPorDescricao(usuario, "Orfa")).isEmpty();
    assertThat(categorias.buscarPorDescricao(usuario, "Outra Orfa")).isEmpty();
    assertThat(categoriasDoUsuario()).isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                "select descricao from poupamais.lancamento where id_usuario = ? order by id",
                String.class,
                usuario))
        .containsExactly("Ração", "Passagem");
  }
}
