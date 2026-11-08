package br.com.pastelariass.usuario;

import br.com.pastelariass.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API de usuários, contra o PostgreSQL do Testcontainers.
 *
 * .with(user("chefe").roles("ADMIN")) simula alguém já logado com esse papel,
 * sem precisar passar pelo login. O login de verdade é testado no último teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UsuarioApiTest {

    @Autowired MockMvc mvc;

    /** Nome único por teste: todos os testes dividem o mesmo banco. */
    private static String novoUsername() {
        return "equipe-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String cadastroJson(String username, String senha) {
        return """
                {"username":"%s","nome":"Pessoa de Teste","senha":"%s","papel":"EQUIPE"}
                """.formatted(username, senha);
    }

    // ── Quem pode ────────────────────────────────────────────────────────────

    @Test
    void adminCadastraUsuarioE201SemHashDaSenha() throws Exception {
        var username = novoUsername();

        mvc.perform(post("/api/usuarios").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson(username, "senha-da-equipe-1")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/usuarios/")))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.papel").value("EQUIPE"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void equipeNaoPodeCadastrarRecebe403EmJson() throws Exception {
        mvc.perform(post("/api/usuarios").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson(novoUsername(), "senha-da-equipe-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("Acesso negado."));
    }

    @Test
    void equipeNaoPodeListar() throws Exception {
        mvc.perform(get("/api/usuarios").with(user("ana").roles("EQUIPE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminListaUsuariosIncluindoOAdministradorInicial() throws Exception {
        mvc.perform(get("/api/usuarios").with(user("chefe").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("admin")))
                .andExpect(jsonPath("$[*].senhaHash").isEmpty());
    }

    // ── Regras do cadastro, pela API ─────────────────────────────────────────

    @Test
    void usernameRepetidoMesmoComOutrasMaiusculasRecebe422() throws Exception {
        var username = novoUsername();
        mvc.perform(post("/api/usuarios").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson(username, "senha-da-equipe-1")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/usuarios").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson(username.toUpperCase(), "senha-da-equipe-1")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.mensagem").value("Já existe um usuário com esse nome de usuário."));
    }

    @Test
    void senhaCurtaRecebe422ComExplicacao() throws Exception {
        mvc.perform(post("/api/usuarios").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson(novoUsername(), "curta")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.mensagem").value(startsWith("A senha deve ter")));
    }

    @Test
    void camposFaltandoRecebem400() throws Exception {
        mvc.perform(post("/api/usuarios").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sem-resto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dados inválidos."));
    }

    @Test
    void cadastroSemCsrfEhRecusado() throws Exception {
        mvc.perform(post("/api/usuarios").with(user("chefe").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson(novoUsername(), "senha-da-equipe-1")))
                .andExpect(status().isForbidden());
    }

    // ── De ponta a ponta ─────────────────────────────────────────────────────

    @Test
    void usuarioCadastradoConsegueFazerLoginComOPapelCerto() throws Exception {
        var username = novoUsername();
        mvc.perform(post("/api/usuarios").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson(username, "senha-da-equipe-1")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","senha":"senha-da-equipe-1"}""".formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("EQUIPE"));
    }
}
