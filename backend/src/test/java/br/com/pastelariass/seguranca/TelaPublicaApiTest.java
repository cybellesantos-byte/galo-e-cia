package br.com.pastelariass.seguranca;

import br.com.pastelariass.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A tela (arquivos estáticos) abre sem login; a API continua exigindo login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TelaPublicaApiTest {

    @Autowired MockMvc mvc;

    @Test
    void paginaInicialAbreSemLogin() throws Exception {
        // "/" é encaminhado para index.html; o conteúdo é conferido direto no arquivo.
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Galo &amp; Cia")));
    }

    @Test
    void arquivosDaTelaAbremSemLogin() throws Exception {
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/vendor/bootstrap/bootstrap.min.css")).andExpect(status().isOk());
        mvc.perform(get("/vendor/fonts/baloo2-latin.woff2")).andExpect(status().isOk());
    }

    /** A página do cliente: o link que vai para o Instagram e para o WhatsApp. */
    @Test
    void paginaDoCardapioAbreSemLoginNosDoisEnderecos() throws Exception {
        // "/cardapio" é encaminhado para o arquivo; o conteúdo é conferido direto nele.
        mvc.perform(get("/cardapio"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("/cardapio.html"));
        mvc.perform(get("/cardapio.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Pastelaria do Galo")))
                // Não é o sistema interno: nada de menu nem de tela de login nesta página.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("menu-celular"))));
        mvc.perform(get("/js/publico/cardapio.js")).andExpect(status().isOk());
        mvc.perform(get("/js/publico/mensagem.js")).andExpect(status().isOk());
    }

    /** As logos aparecem na tela de login, antes de qualquer sessão: precisam abrir sem login. */
    @Test
    void logosAbremSemLogin() throws Exception {
        mvc.perform(get("/img/logo-galo.png")).andExpect(status().isOk());
        mvc.perform(get("/img/logo-peixaria.svg")).andExpect(status().isOk());
    }

    @Test
    void apiContinuaExigindoLogin() throws Exception {
        mvc.perform(get("/api/vendas?caixaId=1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/produtos")).andExpect(status().isUnauthorized());
    }
}
