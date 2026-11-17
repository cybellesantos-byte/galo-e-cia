package br.com.pastelariass.seguranca;

import br.com.pastelariass.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
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

    /** O link curto /pedir abre a mesma página do cardápio, sem login e sem trocar o endereço. */
    @Test
    void linkCurtoPedirAbreOCardapioSemLogin() throws Exception {
        mvc.perform(get("/pedir"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("/cardapio.html"));
    }

    /**
     * A prévia do link no WhatsApp e no Instagram: título, descrição e uma imagem. A imagem precisa de
     * endereço COMPLETO (https://...), e o arquivo dela tem de abrir sem login, porque quem monta a
     * prévia não está logado.
     */
    @Test
    void paginaDoCardapioTemAPreviaParaOWhatsApp() throws Exception {
        var html = mvc.perform(get("/cardapio.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("property=\"og:title\"", "property=\"og:description\"", "property=\"og:image\"");

        var imagem = Pattern.compile("property=\"og:image\" content=\"([^\"]+)\"").matcher(html);
        assertThat(imagem.find()).as("etiqueta og:image com o endereço da imagem").isTrue();
        assertThat(imagem.group(1)).startsWith("https://");

        // O endereço aponta para um arquivo que existe de verdade (se o logo for renomeado, este teste avisa).
        mvc.perform(get(URI.create(imagem.group(1)).getPath())).andExpect(status().isOk());
    }

    /**
     * O CSP (a lista de fornecedores permitidos do navegador) sai em TODA resposta, até nos erros,
     * e é para valer (Content-Security-Policy, e não só "Report-Only", que apenas avisa).
     */
    @Test
    void todaRespostaTrazOCspParaValer() throws Exception {
        for (var caminho : new String[]{"/", "/cardapio", "/api/publico/cardapio", "/api/produtos" /* 401 */}) {
            mvc.perform(get(caminho))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("Content-Security-Policy", org.hamcrest.Matchers.allOf(
                                    org.hamcrest.Matchers.containsString("default-src 'self'"),
                                    org.hamcrest.Matchers.containsString("script-src 'self'"),
                                    org.hamcrest.Matchers.containsString("style-src 'self'"),
                                    org.hamcrest.Matchers.containsString("object-src 'none'"),
                                    org.hamcrest.Matchers.containsString("frame-ancestors 'none'"))))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .doesNotExist("Content-Security-Policy-Report-Only"));
        }
    }

    /** A lista não abre exceção perigosa: sem script/estilo solto, sem eval e sem endereço de fora. */
    @Test
    void oCspNaoAbrePortaParaScriptNemEnderecoDeFora() throws Exception {
        var politica = mvc.perform(get("/")).andReturn().getResponse().getHeader("Content-Security-Policy");

        assertThat(politica).isNotBlank();
        assertThat(politica).doesNotContain("unsafe-inline", "unsafe-eval", "http:", "https:", "*");
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
