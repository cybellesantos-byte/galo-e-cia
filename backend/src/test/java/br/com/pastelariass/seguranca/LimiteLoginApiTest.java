package br.com.pastelariass.seguranca;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Limite de tentativas pela API, contra PostgreSQL real.
 * Cada teste usa um nome novo, porque o contador é compartilhado na memória.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LimiteLoginApiTest {

    private static final String SENHA = "senha-da-equipe-1";

    @Autowired MockMvc mvc;

    private static String novoUsername() {
        return "limite-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void loginCom(String username, String senha, int statusEsperado) throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","senha":"%s"}""".formatted(username, senha)))
                .andExpect(status().is(statusEsperado));
    }

    @Test
    void cincoErrosBloqueiamAteOCertoEntaoOSistemaRecusa429() throws Exception {
        var username = novoUsername();
        mvc.perform(post("/api/usuarios").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","nome":"Pessoa","senha":"%s","papel":"EQUIPE"}"""
                                .formatted(username, SENHA)))
                .andExpect(status().isCreated());

        for (int i = 0; i < LimiteTentativasLogin.MAXIMO_FALHAS; i++) {
            loginCom(username, "errada-errada-1", 401);
        }

        // Agora bloqueado: nem a senha CERTA entra. O limite não pode ser contornado por sorte.
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","senha":"%s"}""".formatted(username, SENHA)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensagem").value("Muitas tentativas de login. Tente novamente em alguns minutos."));
    }

    @Test
    void bloqueioDeUmNomeNaoAfetaOutroNome() throws Exception {
        var bloqueado = novoUsername();
        for (int i = 0; i < LimiteTentativasLogin.MAXIMO_FALHAS; i++) {
            loginCom(bloqueado, "errada-errada-1", 401);
        }
        loginCom(bloqueado, SENHA, 429);

        // Outro nome qualquer continua recebendo a resposta normal (401 por não existir).
        loginCom(novoUsername(), SENHA, 401);
    }
}
