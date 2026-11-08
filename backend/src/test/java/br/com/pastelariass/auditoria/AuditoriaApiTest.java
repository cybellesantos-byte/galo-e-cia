package br.com.pastelariass.auditoria;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A auditoria registra as ações certas, com o autor certo, e só o ADMIN lê.
 * Contra PostgreSQL real: é a tabela de verdade que guarda os registros.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuditoriaApiTest {

    @Autowired MockMvc mvc;
    @Autowired RegistroAuditoriaRepository registros;

    private static String novoUsername(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Registros de um nome, de uma ação. Os testes dividem o banco, então filtramos pelo nome único. */
    private java.util.List<RegistroAuditoria> de(String usuario, AcaoAuditoria acao) {
        return registros.findAll().stream()
                .filter(r -> r.getUsuario().equals(usuario) && r.getAcao() == acao)
                .toList();
    }

    @Test
    void loginCertoDeixaRastro() throws Exception {
        var username = novoUsername("rastro-ok");
        mvc.perform(post("/api/usuarios").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","nome":"Pessoa","senha":"senha-da-equipe-1","papel":"EQUIPE"}"""
                                .formatted(username)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","senha":"senha-da-equipe-1"}""".formatted(username)))
                .andExpect(status().isOk());

        assertThat(de(username, AcaoAuditoria.LOGIN_OK)).hasSize(1);
    }

    @Test
    void loginErradoDeixaRastroComONomeTentado() throws Exception {
        var username = novoUsername("rastro-falha");

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","senha":"senha-errada-1"}""".formatted(username)))
                .andExpect(status().isUnauthorized());

        assertThat(de(username, AcaoAuditoria.LOGIN_FALHA)).hasSize(1);
    }

    @Test
    void cadastroRegistraQuemCadastrou() throws Exception {
        var autor = novoUsername("chefe");
        var novo = novoUsername("nova-pessoa");

        mvc.perform(post("/api/usuarios").with(user(autor).roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","nome":"Pessoa","senha":"senha-da-equipe-1","papel":"EQUIPE"}"""
                                .formatted(novo)))
                .andExpect(status().isCreated());

        var registro = de(autor, AcaoAuditoria.USUARIO_CADASTRADO);
        assertThat(registro).hasSize(1);
        assertThat(registro.get(0).getAlvo()).isEqualTo(novo);
    }

    @Test
    void consultaDaAuditoriaSoParaAdmin() throws Exception {
        mvc.perform(get("/api/auditoria").with(user("ana").roles("EQUIPE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria").with(user("chefe").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void registrosNaoPodemSerAlteradosPorNenhumaRota() throws Exception {
        // Só leitura: nenhum caminho de alteração ou exclusão existe para a auditoria.
        mvc.perform(post("/api/auditoria").with(user("chefe").roles("ADMIN")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }
}
