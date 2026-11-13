package br.com.poupamais.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.categoria.publico.CategoriaFacade;
import br.com.poupamais.lancamento.publico.LancamentoFacade;
import br.com.poupamais.lancamento.publico.dto.NovoLancamento;
import br.com.poupamais.lancamento.publico.enums.TipoLancamento;
import br.com.poupamais.shared.security.JwtService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MockMvc;

// Ponta a ponta contra PostgreSQL real: só ATIVOS entram, 4 meses atravessando o ano, rateio de
// centavos e ranking. Sem POUPAMAIS_TEST_DB_URL o teste é pulado.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POUPAMAIS_TEST_DB_URL", matches = ".+")
class DashboardIntegracaoTest {

  @DynamicPropertySource
  static void banco(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", () -> System.getenv("POUPAMAIS_TEST_DB_URL"));
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private LancamentoFacade lancamentos;
  @Autowired private CategoriaFacade categorias;
  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;

  private Long usuario;
  private Long outroUsuario;
  private String bearer;

  private Long criarUsuario() {
    return jdbc.queryForObject(
        "insert into poupamais.usuario (nome, email, senha, aceite_termos_em)"
            + " values ('Teste', ?, 'x', now()) returning id",
        Long.class,
        "dashboard-" + UUID.randomUUID() + "@teste.com");
  }

  @BeforeEach
  void dados() {
    usuario = criarUsuario();
    outroUsuario = criarUsuario();
    bearer = "Bearer " + jwtService.gerar(usuario).token();
  }

  @AfterEach
  void limpar() {
    jdbc.update("delete from poupamais.usuario where id in (?, ?)", usuario, outroUsuario);
  }

  private Long criar(
      Long dono,
      String descricao,
      TipoLancamento tipo,
      int mes,
      int ano,
      String valor,
      List<Long> cats) {
    return lancamentos
        .criar(dono, new NovoLancamento(descricao, tipo, mes, ano, new BigDecimal(valor), cats))
        .id();
  }

  @Test
  void deveMontarODashboardSoComLancamentosAtivos() throws Exception {
    // criadas nesta ordem: moradia < mercado < lazer (ids crescentes)
    Long moradia = categorias.buscarOuCriar(usuario, "Moradia").id();
    Long mercado = categorias.buscarOuCriar(usuario, "Mercado").id();
    Long lazer = categorias.buscarOuCriar(usuario, "Lazer").id();
    Long salarioOutro = categorias.buscarOuCriar(outroUsuario, "Salário").id();

    // Janeiro/2026 (selecionado)
    criar(usuario, "Salário", TipoLancamento.RECEITA, 1, 2026, "3000.00", List.of(moradia));
    Long aluguel =
        criar(usuario, "Aluguel", TipoLancamento.DESPESA, 1, 2026, "1500.00", List.of(moradia));
    Long feira =
        criar(
            usuario,
            "Feira e cinema",
            TipoLancamento.DESPESA,
            1,
            2026,
            "100.00",
            List.of(lazer, mercado, moradia));
    Long lanche =
        criar(usuario, "Lanche", TipoLancamento.DESPESA, 1, 2026, "100.00", List.of(lazer));
    Long inativo =
        criar(usuario, "Viagem", TipoLancamento.DESPESA, 1, 2026, "5000.00", List.of(lazer));
    Long apagado =
        criar(usuario, "Bônus", TipoLancamento.RECEITA, 1, 2026, "9000.00", List.of(moradia));
    // Dezembro/2025 e Outubro/2025 (dentro da janela), Setembro/2025 (fora)
    criar(usuario, "13º", TipoLancamento.RECEITA, 12, 2025, "200.00", List.of(moradia));
    criar(usuario, "Presentes", TipoLancamento.DESPESA, 12, 2025, "350.50", List.of(lazer));
    criar(usuario, "Luz", TipoLancamento.DESPESA, 10, 2025, "80.00", List.of(moradia));
    criar(usuario, "Antigo", TipoLancamento.RECEITA, 9, 2025, "999.00", List.of(moradia));
    // outro usuário no mesmo mês não pode aparecer (RN005)
    criar(
        outroUsuario, "Salário", TipoLancamento.RECEITA, 1, 2026, "7777.00", List.of(salarioOutro));

    mvc.perform(
            patch("/api/lancamentos/" + inativo + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INATIVO\"}"))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/lancamentos/" + apagado).header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/dashboard?mes=1&ano=2026").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mes").value(1))
        .andExpect(jsonPath("$.ano").value(2026))
        .andExpect(jsonPath("$.receitas").value(3000.0))
        .andExpect(jsonPath("$.despesas").value(1700.0))
        .andExpect(jsonPath("$.saldo").value(1300.0))
        .andExpect(jsonPath("$.taxaGasto").value(56.67))
        .andExpect(jsonPath("$.classificacaoTaxa").value("DENTRO_DO_LIMITE"))
        .andExpect(jsonPath("$.mensagem").value("Saldo positivo de R$ 1.300,00 em Janeiro"))
        .andExpect(jsonPath("$.ultimosMeses.length()").value(4))
        .andExpect(jsonPath("$.ultimosMeses[0].mes").value(10))
        .andExpect(jsonPath("$.ultimosMeses[0].ano").value(2025))
        .andExpect(jsonPath("$.ultimosMeses[0].despesas").value(80.0))
        .andExpect(jsonPath("$.ultimosMeses[0].saldo").value(-80.0))
        .andExpect(jsonPath("$.ultimosMeses[1].mes").value(11))
        .andExpect(jsonPath("$.ultimosMeses[1].receitas").value(0))
        .andExpect(jsonPath("$.ultimosMeses[1].despesas").value(0))
        .andExpect(jsonPath("$.ultimosMeses[1].saldo").value(0))
        .andExpect(jsonPath("$.ultimosMeses[2].mes").value(12))
        .andExpect(jsonPath("$.ultimosMeses[2].saldo").value(-150.5))
        .andExpect(jsonPath("$.ultimosMeses[3].mes").value(1))
        .andExpect(jsonPath("$.ultimosMeses[3].ano").value(2026))
        .andExpect(jsonPath("$.ultimosMeses[3].saldo").value(1300.0))
        // 100,00 / 3 = 33,34 (Moradia, menor id) + 33,33 + 33,33
        .andExpect(jsonPath("$.despesasPorCategoria.length()").value(3))
        .andExpect(jsonPath("$.despesasPorCategoria[0].categoriaId").value(moradia))
        .andExpect(jsonPath("$.despesasPorCategoria[0].valor").value(1533.34))
        .andExpect(jsonPath("$.despesasPorCategoria[1].categoriaId").value(lazer))
        .andExpect(jsonPath("$.despesasPorCategoria[1].descricao").value("Lazer"))
        .andExpect(jsonPath("$.despesasPorCategoria[1].valor").value(133.33))
        .andExpect(jsonPath("$.despesasPorCategoria[2].categoriaId").value(mercado))
        .andExpect(jsonPath("$.despesasPorCategoria[2].valor").value(33.33))
        .andExpect(jsonPath("$.maioresDespesas.length()").value(3))
        .andExpect(jsonPath("$.maioresDespesas[0].id").value(aluguel))
        .andExpect(jsonPath("$.maioresDespesas[0].categorias[0]").value("Moradia"))
        .andExpect(jsonPath("$.maioresDespesas[1].id").value(feira))
        .andExpect(jsonPath("$.maioresDespesas[1].categorias.length()").value(3))
        .andExpect(jsonPath("$.maioresDespesas[1].categorias[0]").value("Moradia"))
        .andExpect(jsonPath("$.maioresDespesas[2].id").value(lanche));
  }

  @Test
  void deveResponderMesSemLancamentosComZeros() throws Exception {
    mvc.perform(get("/api/dashboard?mes=3&ano=2030").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.receitas").value(0))
        .andExpect(jsonPath("$.taxaGasto").isEmpty())
        .andExpect(jsonPath("$.classificacaoTaxa").isEmpty())
        .andExpect(jsonPath("$.mensagem").value("Nenhum lançamento ativo em Março"))
        .andExpect(jsonPath("$.ultimosMeses.length()").value(4))
        .andExpect(jsonPath("$.despesasPorCategoria").isEmpty())
        .andExpect(jsonPath("$.maioresDespesas").isEmpty());
  }
}
