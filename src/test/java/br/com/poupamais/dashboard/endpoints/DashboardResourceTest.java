package br.com.poupamais.dashboard.endpoints;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse;
import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse.DespesaCategoria;
import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse.MaiorDespesa;
import br.com.poupamais.dashboard.endpoints.dto.DashboardResponse.MesResumo;
import br.com.poupamais.dashboard.service.ClassificacaoTaxa;
import br.com.poupamais.dashboard.service.DashboardService;
import br.com.poupamais.shared.config.WebConfiguration;
import br.com.poupamais.shared.domain.Periodo;
import br.com.poupamais.shared.security.JwtService;
import br.com.poupamais.shared.security.SecurityConfiguration;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardResource.class)
@Import({SecurityConfiguration.class, JwtService.class, WebConfiguration.class})
class DashboardResourceTest {

  private static final Long USUARIO = 7L;

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;
  @MockitoBean private DashboardService service;

  private String bearer;

  @BeforeEach
  void token() {
    bearer = "Bearer " + jwtService.gerar(USUARIO).token();
  }

  private static DashboardResponse resposta(int mes, int ano) {
    BigDecimal zero = new BigDecimal("0.00");
    return new DashboardResponse(
        mes,
        ano,
        new BigDecimal("9700.00"),
        new BigDecimal("4780.00"),
        new BigDecimal("4920.00"),
        new BigDecimal("49.28"),
        ClassificacaoTaxa.DENTRO_DO_LIMITE,
        "Saldo positivo de R$ 4.920,00 em Agosto",
        List.of(new MesResumo(5, 2026, zero, zero, zero)),
        List.of(new DespesaCategoria(2L, "Moradia", new BigDecimal("2100.00"))),
        List.of(new MaiorDespesa(9L, "Aluguel", new BigDecimal("2100.00"), List.of("Moradia"))));
  }

  @Test
  void deveResponderNoFormatoDoContrato() throws Exception {
    when(service.consultar(USUARIO, new Periodo(8, 2026))).thenReturn(resposta(8, 2026));

    mvc.perform(get("/api/dashboard?mes=8&ano=2026").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mes").value(8))
        .andExpect(jsonPath("$.ano").value(2026))
        .andExpect(jsonPath("$.receitas").value(9700.0))
        .andExpect(jsonPath("$.despesas").value(4780.0))
        .andExpect(jsonPath("$.saldo").value(4920.0))
        .andExpect(jsonPath("$.taxaGasto").value(49.28))
        .andExpect(jsonPath("$.classificacaoTaxa").value("DENTRO_DO_LIMITE"))
        .andExpect(jsonPath("$.mensagem").value("Saldo positivo de R$ 4.920,00 em Agosto"))
        .andExpect(jsonPath("$.ultimosMeses[0].mes").value(5))
        .andExpect(jsonPath("$.ultimosMeses[0].saldo").value(0))
        .andExpect(jsonPath("$.despesasPorCategoria[0].categoriaId").value(2))
        .andExpect(jsonPath("$.despesasPorCategoria[0].descricao").value("Moradia"))
        .andExpect(jsonPath("$.despesasPorCategoria[0].valor").value(2100.0))
        .andExpect(jsonPath("$.maioresDespesas[0].id").value(9))
        .andExpect(jsonPath("$.maioresDespesas[0].categorias[0]").value("Moradia"));
  }

  @Test
  void deveUsarMesEAnoAtuaisPorPadrao() throws Exception {
    Periodo atual = Periodo.atual();
    when(service.consultar(USUARIO, atual)).thenReturn(resposta(atual.mes(), atual.ano()));

    mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mes").value(atual.mes()));

    verify(service).consultar(USUARIO, atual);
  }

  @Test
  void deveSerializarTaxaEClassificacaoNulas() throws Exception {
    DashboardResponse base = resposta(1, 2026);
    DashboardResponse semTaxa =
        new DashboardResponse(
            1,
            2026,
            base.receitas(),
            base.despesas(),
            base.saldo(),
            null,
            null,
            base.mensagem(),
            List.of(),
            List.of(),
            List.of());
    when(service.consultar(any(), any())).thenReturn(semTaxa);

    mvc.perform(get("/api/dashboard?mes=1&ano=2026").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taxaGasto").isEmpty())
        .andExpect(jsonPath("$.classificacaoTaxa").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "mes=0&ano=2026",
        "mes=13&ano=2026",
        "mes=8&ano=1999",
        "mes=8&ano=2101",
        "mes=abc"
      })
  void deveDevolver400ParaMesOuAnoInvalido(String query) throws Exception {
    mvc.perform(get("/api/dashboard?" + query).header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
        .andExpect(jsonPath("$.erros[0].campo").exists());

    verifyNoInteractions(service);
  }

  @Test
  void deveDevolver401SemToken() throws Exception {
    mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());

    verifyNoInteractions(service);
  }
}
