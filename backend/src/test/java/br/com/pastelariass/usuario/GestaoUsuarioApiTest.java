package br.com.pastelariass.usuario;

import br.com.pastelariass.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parte B pela API, contra PostgreSQL real. Aqui os testes usam logins de
 * verdade (com sessão), porque o assunto é justamente a sessão caindo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class GestaoUsuarioApiTest {

    private static final String SENHA = "senha-da-equipe-1";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarioRepository;

    private static String novoUsername(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Cadastra pela API, como o ADMIN faria. Devolve o id do novo usuário. */
    private Long cadastrar(String username, String papel) throws Exception {
        mvc.perform(post("/api/usuarios").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","nome":"Pessoa","senha":"%s","papel":"%s"}"""
                                .formatted(username, SENHA, papel)))
                .andExpect(status().isCreated());
        return usuarioRepository.findByUsernameIgnoreCase(username).orElseThrow().getId();
    }

    /** Faz login de verdade e devolve a sessão, como o navegador ficaria. */
    private MockHttpSession login(String username, String senha) throws Exception {
        var resultado = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","senha":"%s"}""".formatted(username, senha)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession();
    }

    // ── Desativar e reativar ─────────────────────────────────────────────────

    @Test
    void equipeNaoDesativaNinguem() throws Exception {
        mvc.perform(post("/api/usuarios/1/desativar").with(user("ana").roles("EQUIPE")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDesativaUmUsuarioEAListaComoInativo() throws Exception {
        var username = novoUsername("equipe");
        var id = cadastrar(username, "EQUIPE");

        mvc.perform(post("/api/usuarios/" + id + "/desativar").with(user("chefe").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mvc.perform(post("/api/usuarios/" + id + "/reativar").with(user("chefe").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void adminNaoDesativaASiMesmo() throws Exception {
        var username = novoUsername("chefe");
        var id = cadastrar(username, "ADMIN");

        mvc.perform(post("/api/usuarios/" + id + "/desativar").with(user(username).roles("ADMIN")).with(csrf()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.mensagem").value("Você não pode desativar a própria conta."));
    }

    @Test
    void usuarioInexistenteRecebe404() throws Exception {
        mvc.perform(post("/api/usuarios/999999999/desativar").with(user("chefe").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    /**
     * O teste mais importante da Parte B: alguém logado é desativado, e a
     * sessão que ela já tinha aberta deixa de funcionar NA HORA.
     */
    @Test
    void sessaoDeUmUsuarioDesativadoCaiNaProximaRequisicao() throws Exception {
        var username = novoUsername("demitida");
        var id = cadastrar(username, "EQUIPE");
        var sessao = login(username, SENHA);

        mvc.perform(get("/api/auth/me").session(sessao))
                .andExpect(status().isOk());

        mvc.perform(post("/api/usuarios/" + id + "/desativar").with(user("chefe").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").session(sessao))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioDesativadoNaoConsegueFazerLoginNovamente() throws Exception {
        var username = novoUsername("inativo");
        var id = cadastrar(username, "EQUIPE");
        mvc.perform(post("/api/usuarios/" + id + "/desativar").with(user("chefe").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","senha":"%s"}""".formatted(username, SENHA)))
                .andExpect(status().isUnauthorized());
    }

    // ── Trocar a própria senha ───────────────────────────────────────────────

    @Test
    void trocaDeSenhaFuncionaERecusaASenhaAntiga() throws Exception {
        var username = novoUsername("troca");
        cadastrar(username, "EQUIPE");
        var sessao = login(username, SENHA);

        mvc.perform(put("/api/auth/senha").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"%s","senhaNova":"nova-senha-12345"}""".formatted(SENHA)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","senha":"%s"}""".formatted(username, SENHA)))
                .andExpect(status().isUnauthorized());

        login(username, "nova-senha-12345");
    }

    @Test
    void trocaComSenhaAtualErradaRecebe422() throws Exception {
        var username = novoUsername("troca");
        cadastrar(username, "EQUIPE");
        var sessao = login(username, SENHA);

        mvc.perform(put("/api/auth/senha").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"errada-errada","senhaNova":"nova-senha-12345"}"""))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.mensagem").value("A senha atual está incorreta."));
    }

    /**
     * Mesma pessoa logada em dois aparelhos. Ela troca a senha no celular:
     * o computador (sessão com a senha antiga) cai, e o celular continua logado.
     */
    @Test
    void trocarSenhaDerrubaAsOutrasSessoesEMantemAAtual() throws Exception {
        var username = novoUsername("dois-aparelhos");
        cadastrar(username, "EQUIPE");
        var computador = login(username, SENHA);
        var celular = login(username, SENHA);

        mvc.perform(put("/api/auth/senha").session(celular).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"%s","senhaNova":"nova-senha-12345"}""".formatted(SENHA)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(computador))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(celular))
                .andExpect(status().isOk());
    }

    @Test
    void trocaDeSenhaSemLoginRecebe401() throws Exception {
        mvc.perform(put("/api/auth/senha").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"x","senhaNova":"nova-senha-12345"}"""))
                .andExpect(status().isUnauthorized());
    }
}
