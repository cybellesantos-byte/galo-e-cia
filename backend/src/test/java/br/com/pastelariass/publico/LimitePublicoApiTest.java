package br.com.pastelariass.publico;

import br.com.pastelariass.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O limite de acessos funcionando de ponta a ponta, com o sistema inteiro ligado.
 * O limite aqui é 3 por minuto (nos testes normais, 120), para o teste ser rápido.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.publico.limite-por-minuto=3")
class LimitePublicoApiTest {

    @Autowired MockMvc mvc;

    private static RequestPostProcessor vindoDe(String endereco) {
        return requisicao -> {
            requisicao.setRemoteAddr(endereco);
            return requisicao;
        };
    }

    @Test
    void oQuartoAcessoNoMinutoRecebeTenteDeNovoComTempoDeEspera() throws Exception {
        var endereco = vindoDe("203.0.113.10");
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/publico/cardapio").with(endereco)).andExpect(status().isOk());
        }

        mvc.perform(get("/api/publico/cardapio").with(endereco))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.mensagem").value("Muitos acessos em pouco tempo. Tente de novo em instantes."));
    }

    @Test
    void oBloqueioDeUmEnderecoNaoAtrapalhaOutroCliente() throws Exception {
        var robo = vindoDe("203.0.113.20");
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/publico/cardapio").with(robo));
        }
        mvc.perform(get("/api/publico/cardapio").with(robo)).andExpect(status().isTooManyRequests());

        mvc.perform(get("/api/publico/cardapio").with(vindoDe("203.0.113.21"))).andExpect(status().isOk());
    }

    /** O limite é só da porta pública: o login e o resto do sistema não são afetados por ele. */
    @Test
    void ofiscalSoAgeNaPortaPublica() throws Exception {
        var endereco = vindoDe("203.0.113.30");
        for (int i = 0; i < 10; i++) {
            // Um token CSRF (rota liberada, fora de /api/publico) pode ser pedido à vontade.
            mvc.perform(get("/api/auth/csrf").with(endereco)).andExpect(status().is2xxSuccessful());
        }
        // E o cardápio desse mesmo endereço ainda tem a cota inteira.
        mvc.perform(get("/api/publico/cardapio").with(endereco))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()", greaterThan(-1)));
    }
}
