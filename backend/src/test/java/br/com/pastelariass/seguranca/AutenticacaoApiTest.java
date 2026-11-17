package br.com.pastelariass.seguranca;

import br.com.pastelariass.TestcontainersConfiguration;
import br.com.pastelariass.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regras de autenticação, uma por teste, via MockMvc (requisições simuladas,
 * sem abrir porta de rede), contra o PostgreSQL do Testcontainers.
 *
 * .with(csrf()) é um atalho de teste que inclui um token CSRF válido. O fluxo
 * com o CSRF 100% real, igual ao navegador, está em FluxoLoginNavegadorTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AutenticacaoApiTest {

    // Senha do admin definida em src/test/resources/application-test.properties
    private static final String LOGIN_ADMIN = """
            {"username":"admin","senha":"senha-de-teste-123"}""";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UsuarioRepository usuarioRepository;

    // ── Acesso sem login ─────────────────────────────────────────────────────

    @Test
    void semLoginTudoEhBloqueadoCom401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Faça login para continuar."));
        mvc.perform(get("/api/qualquer-coisa")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenCsrfPodeSerObtidoSemLogin() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent());
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @Test
    void loginCorretoDevolveDadosDoUsuarioSemSenha() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.nome").value("Administrador"))
                .andExpect(jsonPath("$.papel").value("ADMIN"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void loginRegistraDataDoUltimoAcesso() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_ADMIN))
                .andExpect(status().isOk());

        var admin = usuarioRepository.findByUsernameIgnoreCase("admin").orElseThrow();
        assertThat(admin.getUltimoLoginEm()).isNotNull();
    }

    @Test
    void usernameNaoDiferenciaMaiusculas() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ADMIN","senha":"senha-de-teste-123"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void senhaErradaEUsuarioInexistenteRecebemAMesmaResposta() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","senha":"senha-errada"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Usuário ou senha inválidos."));

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nao-existe","senha":"senha-errada"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Usuário ou senha inválidos."));
    }

    @Test
    void usuarioDesativadoNaoEntraMesmoComSenhaCerta() throws Exception {
        jdbc.sql("""
                        INSERT INTO usuario (username, nome, senha_hash, papel, ativo)
                        VALUES ('desativado', 'Ex-funcionário', :hash, 'EQUIPE', FALSE)
                        """)
                .param("hash", passwordEncoder.encode("senha-certa-123"))
                .update();

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"desativado","senha":"senha-certa-123"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Usuário ou senha inválidos."));
    }

    @Test
    void loginSemTokenCsrfEhRecusado() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_ADMIN))
                .andExpect(status().isForbidden());
    }

    // ── Validação de entrada ─────────────────────────────────────────────────

    @Test
    void camposVaziosDevolvem400ComDetalhes() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","senha":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dados inválidos."))
                .andExpect(jsonPath("$.detalhes.length()").value(2));
    }

    @Test
    void senhaGiganteEhRecusadaAntesDoBCrypt() throws Exception {
        var senhaGigante = "x".repeat(10_000);
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"senha\":\"" + senhaGigante + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jsonMalformadoDevolve400() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{isto não é json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Corpo da requisição inválido."));
    }

    // ── Sessão ───────────────────────────────────────────────────────────────

    @Test
    void sessaoDoLoginVaiParaAsProximasRequisicoesELogoutEncerra() throws Exception {
        var login = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_ADMIN))
                .andExpect(status().isOk())
                .andReturn();
        var sessao = (MockHttpSession) login.getRequest().getSession();

        mvc.perform(get("/api/auth/me").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));

        mvc.perform(post("/api/auth/logout").session(sessao).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(sessao))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotaInexistenteComLoginDevolve404EmPortugues() throws Exception {
        mvc.perform(get("/api/nao-existe").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Recurso não encontrado."));
    }
}
