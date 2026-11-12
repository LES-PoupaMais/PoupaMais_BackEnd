package br.com.poupamais.usuario.endpoints;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.shared.config.WebConfiguration;
import br.com.poupamais.shared.exception.ConflitoException;
import br.com.poupamais.shared.exception.ErroAutenticacaoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.shared.security.JwtService;
import br.com.poupamais.shared.security.JwtService.TokenGerado;
import br.com.poupamais.shared.security.SecurityConfiguration;
import br.com.poupamais.usuario.endpoints.mappers.UsuarioMapper;
import br.com.poupamais.usuario.model.entidades.Usuario;
import br.com.poupamais.usuario.service.AuthService;
import br.com.poupamais.usuario.service.AuthService.Sessao;
import br.com.poupamais.usuario.service.RecuperacaoSenhaService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(controllers = AuthResource.class)
@Import({
  SecurityConfiguration.class,
  JwtService.class,
  WebConfiguration.class,
  UsuarioMapper.class
})
class AuthResourceTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private AuthService authService;
  @MockitoBean private RecuperacaoSenhaService recuperacaoService;

  private ResultActions postar(String rota, String json) throws Exception {
    return mvc.perform(
        post("/api/auth/" + rota).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private static Usuario ana() {
    return Usuario.builder().id(1L).nome("Ana").email("ana@email.com").senha("hash").build();
  }

  @Test
  void deveCadastrarSemTokenEDevolver201SemSenha() throws Exception {
    when(authService.cadastrar("Ana", "ana@email.com", "123456", true)).thenReturn(ana());

    postar(
            "cadastro",
            "{\"nome\":\"Ana\",\"email\":\"ana@email.com\",\"senha\":\"123456\",\"aceiteTermos\":true}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.nome").value("Ana"))
        .andExpect(jsonPath("$.email").value("ana@email.com"))
        .andExpect(jsonPath("$.senha").doesNotExist());
  }

  @Test
  void deveDevolver400QuandoAceiteTermosFalsoOuAusente() throws Exception {
    postar(
            "cadastro",
            "{\"nome\":\"Ana\",\"email\":\"ana@email.com\",\"senha\":\"123456\",\"aceiteTermos\":false}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[*].campo").value(hasItem("aceiteTermos")));
    postar("cadastro", "{\"nome\":\"Ana\",\"email\":\"ana@email.com\",\"senha\":\"123456\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[*].campo").value(hasItem("aceiteTermos")));
    verifyNoInteractions(authService);
  }

  @Test
  void deveDevolver400ComErrosDeFormatoNoCadastro() throws Exception {
    postar(
            "cadastro",
            "{\"nome\":\"\",\"email\":\"invalido\",\"senha\":\"123\",\"aceiteTermos\":true}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
        .andExpect(jsonPath("$.erros[*].campo").value(hasItem("nome")))
        .andExpect(jsonPath("$.erros[*].campo").value(hasItem("email")))
        .andExpect(jsonPath("$.erros[*].campo").value(hasItem("senha")));
  }

  @Test
  void deveDevolver409QuandoEmailDuplicado() throws Exception {
    when(authService.cadastrar(anyString(), anyString(), anyString(), anyBoolean()))
        .thenThrow(new ConflitoException("Este e-mail já está cadastrado"));

    postar(
            "cadastro",
            "{\"nome\":\"Ana\",\"email\":\"ana@email.com\",\"senha\":\"123456\",\"aceiteTermos\":true}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.mensagem").value("Este e-mail já está cadastrado"));
  }

  @Test
  void deveFazerLoginEDevolverTokenExpiracaoEUsuario() throws Exception {
    Instant expira = Instant.parse("2026-11-12T18:00:00Z");
    when(authService.login("ana@email.com", "123456"))
        .thenReturn(new Sessao(new TokenGerado("jwt", expira), ana()));

    postar("login", "{\"email\":\"ana@email.com\",\"senha\":\"123456\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("jwt"))
        .andExpect(jsonPath("$.expiraEm").value("2026-11-12T18:00:00Z"))
        .andExpect(jsonPath("$.usuario.id").value(1))
        .andExpect(jsonPath("$.usuario.email").value("ana@email.com"));
  }

  @Test
  void deveDevolver401NoLoginInvalido() throws Exception {
    when(authService.login(any(), any()))
        .thenThrow(new ErroAutenticacaoException("E-mail ou senha inválidos"));

    postar("login", "{\"email\":\"ana@email.com\",\"senha\":\"errada\"}")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos"));
  }

  @Test
  void deveDevolver400NoLoginSemCampos() throws Exception {
    postar("login", "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[*].campo").value(hasItem("senha")));
  }

  @Test
  void recuperarSenhaDeveResponder202SemCorpo() throws Exception {
    postar("recuperar-senha", "{\"email\":\"qualquer@email.com\"}")
        .andExpect(status().isAccepted())
        .andExpect(content().string(""));

    verify(recuperacaoService).solicitar("qualquer@email.com");
  }

  @Test
  void deveRedefinirSenhaCom204() throws Exception {
    postar("redefinir-senha", "{\"token\":\"abc\",\"novaSenha\":\"novaSenha\"}")
        .andExpect(status().isNoContent());

    verify(recuperacaoService).redefinir("abc", "novaSenha");
  }

  @Test
  void deveDevolver422ComTokenInvalido() throws Exception {
    doThrow(
            new RegraNegocioException(
                "Link de redefinição inválido ou expirado. Solicite um novo."))
        .when(recuperacaoService)
        .redefinir(any(), any());

    postar("redefinir-senha", "{\"token\":\"abc\",\"novaSenha\":\"novaSenha\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422));
  }

  @Test
  void deveDevolver400NaRedefinicaoComSenhaCurta() throws Exception {
    postar("redefinir-senha", "{\"token\":\"abc\",\"novaSenha\":\"123\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("novaSenha"));
  }
}
