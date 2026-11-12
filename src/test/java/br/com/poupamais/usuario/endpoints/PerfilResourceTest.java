package br.com.poupamais.usuario.endpoints;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.poupamais.shared.config.WebConfiguration;
import br.com.poupamais.shared.exception.RecursoNaoEncontradoException;
import br.com.poupamais.shared.exception.RegraNegocioException;
import br.com.poupamais.shared.security.JwtService;
import br.com.poupamais.shared.security.JwtService.TokenGerado;
import br.com.poupamais.shared.security.SecurityConfiguration;
import br.com.poupamais.usuario.endpoints.mappers.UsuarioMapper;
import br.com.poupamais.usuario.model.entidades.FotoPerfil;
import br.com.poupamais.usuario.model.entidades.Usuario;
import br.com.poupamais.usuario.service.PerfilService;
import br.com.poupamais.usuario.service.PerfilService.Perfil;
import br.com.poupamais.usuario.service.PrivacidadeService;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {PerfilResource.class, PrivacidadeResource.class})
@Import({
  SecurityConfiguration.class,
  JwtService.class,
  WebConfiguration.class,
  UsuarioMapper.class
})
class PerfilResourceTest {

  private static final Long USUARIO = 7L;
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1};

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;
  @MockitoBean private PerfilService service;
  @MockitoBean private PrivacidadeService privacidadeService;

  private String bearer;

  @BeforeEach
  void token() {
    bearer = "Bearer " + jwtService.gerar(USUARIO).token();
  }

  private static Perfil perfil(String nome, boolean possuiFoto) {
    return new Perfil(
        Usuario.builder()
            .id(USUARIO)
            .nome(nome)
            .email("ana@email.com")
            .senha("hash")
            .dataCadastro(LocalDate.of(2026, 11, 1))
            .build(),
        possuiFoto);
  }

  @Test
  void todasAsRotasDoPerfilExigemToken() throws Exception {
    mvc.perform(get("/api/usuarios/me")).andExpect(status().isUnauthorized());
    mvc.perform(put("/api/usuarios/me/senha")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/usuarios/me/foto")).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/usuarios/me/foto")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/usuarios/me/excluir")).andExpect(status().isUnauthorized());
  }

  @Test
  void deveBuscarPerfilDoUsuarioDoToken() throws Exception {
    when(service.buscar(USUARIO)).thenReturn(perfil("Ana", true));

    mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(7))
        .andExpect(jsonPath("$.nome").value("Ana"))
        .andExpect(jsonPath("$.email").value("ana@email.com"))
        .andExpect(jsonPath("$.possuiFoto").value(true))
        .andExpect(jsonPath("$.dataCadastro").value("2026-11-01"))
        .andExpect(jsonPath("$.senha").doesNotExist());
  }

  @Test
  void deveAtualizarNome() throws Exception {
    when(service.atualizarNome(USUARIO, "Ana Maria")).thenReturn(perfil("Ana Maria", false));

    mvc.perform(
            put("/api/usuarios/me")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana Maria\",\"email\":\"outro@email.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nome").value("Ana Maria"))
        .andExpect(jsonPath("$.email").value("ana@email.com"));
  }

  @Test
  void deveDevolver400ComNomeVazio() throws Exception {
    mvc.perform(
            put("/api/usuarios/me")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("nome"));
  }

  @Test
  void deveTrocarSenhaEDevolverTokenNovo() throws Exception {
    when(service.trocarSenha(USUARIO, "atual1", "nova12"))
        .thenReturn(new TokenGerado("novo", Instant.parse("2026-11-12T18:00:00Z")));

    mvc.perform(
            put("/api/usuarios/me/senha")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"atual1\",\"novaSenha\":\"nova12\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("novo"))
        .andExpect(jsonPath("$.expiraEm").value("2026-11-12T18:00:00Z"));
  }

  @Test
  void senhaAtualErradaDeveDevolver422ENao401() throws Exception {
    when(service.trocarSenha(any(), any(), any()))
        .thenThrow(new RegraNegocioException("A senha atual está incorreta"));

    mvc.perform(
            put("/api/usuarios/me/senha")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"errada\",\"novaSenha\":\"nova12\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.mensagem").value("A senha atual está incorreta"));
  }

  @Test
  void deveDevolver400NaTrocaComCamposInvalidos() throws Exception {
    mvc.perform(
            put("/api/usuarios/me/senha")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"\",\"novaSenha\":\"123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[*].campo").value(hasItem("senhaAtual")))
        .andExpect(jsonPath("$.erros[*].campo").value(hasItem("novaSenha")));
  }

  @Test
  void deveDevolverFotoComTipoESemCache() throws Exception {
    when(service.buscarFoto(USUARIO))
        .thenReturn(
            FotoPerfil.builder()
                .idUsuario(USUARIO)
                .conteudo(PNG)
                .tipoConteudo("image/png")
                .build());

    mvc.perform(get("/api/usuarios/me/foto").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
        .andExpect(content().bytes(PNG));
  }

  @Test
  void semFotoDeveDevolver404() throws Exception {
    when(service.buscarFoto(USUARIO))
        .thenThrow(new RecursoNaoEncontradoException("Foto de perfil não encontrada"));

    mvc.perform(get("/api/usuarios/me/foto").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isNotFound());
  }

  @Test
  void deveEnviarFotoMultipartCom204() throws Exception {
    mvc.perform(
            multipart(HttpMethod.PUT, "/api/usuarios/me/foto")
                .file(new MockMultipartFile("foto", "eu.png", "image/png", PNG))
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isNoContent());

    verify(service).salvarFoto(USUARIO, PNG);
  }

  @Test
  void fotoInvalidaDeveDevolver422() throws Exception {
    doThrow(new RegraNegocioException("A foto deve ser uma imagem JPG, PNG ou WEBP"))
        .when(service)
        .salvarFoto(eq(USUARIO), any());

    mvc.perform(
            multipart(HttpMethod.PUT, "/api/usuarios/me/foto")
                .file(new MockMultipartFile("foto", "eu.png", "image/png", "texto".getBytes()))
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void semCampoFotoDeveChegarNuloAoService() throws Exception {
    doThrow(new RegraNegocioException("A foto deve ser uma imagem JPG, PNG ou WEBP"))
        .when(service)
        .salvarFoto(eq(USUARIO), isNull());

    mvc.perform(
            multipart(HttpMethod.PUT, "/api/usuarios/me/foto")
                .file(new MockMultipartFile("outro", "x.png", "image/png", PNG))
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void deveRemoverFotoCom204() throws Exception {
    mvc.perform(delete("/api/usuarios/me/foto").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isNoContent());

    verify(service).removerFoto(USUARIO);
  }

  @Test
  void deveExcluirContaCom204() throws Exception {
    mvc.perform(
            post("/api/usuarios/me/excluir")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"123456\"}"))
        .andExpect(status().isNoContent());

    verify(privacidadeService).excluirConta(USUARIO, "123456");
  }

  @Test
  void excluirContaComSenhaErradaDeveDevolver422() throws Exception {
    doThrow(new RegraNegocioException("Senha incorreta"))
        .when(privacidadeService)
        .excluirConta(any(), any());

    mvc.perform(
            post("/api/usuarios/me/excluir")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"errada\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.mensagem").value("Senha incorreta"));
  }

  @Test
  void excluirContaSemSenhaDeveDevolver400() throws Exception {
    mvc.perform(
            post("/api/usuarios/me/excluir")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0].campo").value("senha"));
  }
}
