package br.com.pastelariass.loja;

import br.com.pastelariass.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A loja pela API interna, contra PostgreSQL real: quem pode abrir e fechar, quem pode
 * trocar o WhatsApp, e que o cliente enxerga a mudança na porta pública.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional   // cada teste é desfeito no fim: a loja não fica "aberta" para os outros
class LojaApiTest {

    @Autowired MockMvc mvc;

    private static final String CORPO_ABERTA = "{\"aberta\": %s}";
    private static final String CORPO_WHATSAPP = "{\"whatsapp\": \"%s\"}";

    @Test
    void equipeVeComoALojaEstaEAbreEFecha() throws Exception {
        mvc.perform(get("/api/loja/PASTELARIA").with(user("ana").roles("EQUIPE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aberta").value(false))
                .andExpect(jsonPath("$.negocio").value("PASTELARIA"));

        mvc.perform(put("/api/loja/PASTELARIA/aberta").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_ABERTA.formatted("true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aberta").value(true))
                .andExpect(jsonPath("$.alteradoPor").value("ana"));

        mvc.perform(put("/api/loja/PASTELARIA/aberta").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_ABERTA.formatted("false")))
                .andExpect(jsonPath("$.aberta").value(false));
    }

    /** Ponta a ponta: a equipe abre pelo painel e o cliente vê "aberto" na porta pública. */
    @Test
    void abrirNoPainelMudaOQueOClienteVe() throws Exception {
        mvc.perform(get("/api/publico/cardapio")).andExpect(jsonPath("$.aberta").value(false));

        mvc.perform(put("/api/loja/PASTELARIA/aberta").with(user("ana").roles("EQUIPE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CORPO_ABERTA.formatted("true"))).andExpect(status().isOk());

        mvc.perform(get("/api/publico/cardapio")).andExpect(jsonPath("$.aberta").value(true));
    }

    /** O número é para onde vão todos os pedidos: trocar é coisa só do administrador. */
    @Test
    void soOAdministradorTrocaOWhatsapp() throws Exception {
        mvc.perform(put("/api/loja/PASTELARIA/whatsapp").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_WHATSAPP.formatted("(83) 99999-8888")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/loja/PASTELARIA").with(user("ana").roles("EQUIPE")))
                .andExpect(jsonPath("$.whatsapp").doesNotExist());   // não mudou

        mvc.perform(put("/api/loja/PASTELARIA/whatsapp").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_WHATSAPP.formatted("(83) 99999-8888")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsapp").value("5583999998888"));

        mvc.perform(get("/api/publico/cardapio")).andExpect(jsonPath("$.whatsapp").value("5583999998888"));
    }

    @Test
    void whatsappInvalidoEhRecusadoComMensagem() throws Exception {
        mvc.perform(put("/api/loja/PASTELARIA/whatsapp").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_WHATSAPP.formatted("83 abc")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("O WhatsApp deve ter só números, com o DDD. Ex.: (83) 99999-8888."));
    }

    @Test
    void whatsappEmBrancoApagaONumero() throws Exception {
        mvc.perform(put("/api/loja/PASTELARIA/whatsapp").with(user("chefe").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CORPO_WHATSAPP.formatted("(83) 99999-8888"))).andExpect(status().isOk());

        mvc.perform(put("/api/loja/PASTELARIA/whatsapp").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_WHATSAPP.formatted("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsapp").doesNotExist());
    }

    @Test
    void semLoginNaoVeNemAltera() throws Exception {
        mvc.perform(get("/api/loja/PASTELARIA")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/loja/PASTELARIA/aberta").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CORPO_ABERTA.formatted("true"))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/loja/PASTELARIA/whatsapp").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CORPO_WHATSAPP.formatted("83999998888"))).andExpect(status().isUnauthorized());
    }

    @Test
    void semOTokenCsrfNaoAltera() throws Exception {
        mvc.perform(put("/api/loja/PASTELARIA/aberta").with(user("ana").roles("EQUIPE"))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_ABERTA.formatted("true")))
                .andExpect(status().isForbidden());
    }

    @Test
    void negocioInvalidoEhRecusado() throws Exception {
        mvc.perform(get("/api/loja/QUITANDA").with(user("ana").roles("EQUIPE"))).andExpect(status().is4xxClientError());
    }

    @Test
    void faltandoOCampoAbertaEhRecusado() throws Exception {
        mvc.perform(put("/api/loja/PASTELARIA/aberta").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }
}
