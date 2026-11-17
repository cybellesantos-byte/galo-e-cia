package br.com.pastelariass.localidade;

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

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Localidades de entrega pela API, contra PostgreSQL real. As cinco localidades
 * iniciais vêm da migração V4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
// Transacional: a alteração da taxa do Recanto é desfeita no fim, e não vaza para outros testes.
@Transactional
class LocalidadeApiTest {

    @Autowired MockMvc mvc;
    @Autowired LocalidadeRepository localidadeRepository;

    private static String nomeUnico(String base) {
        return base + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void listaTrazAsCincoLocalidadesDaPastelaria() throws Exception {
        mvc.perform(get("/api/localidades").with(user("ana").roles("EQUIPE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vila do Sol")))
                .andExpect(content().string(containsString("Barra do Norte")))
                .andExpect(content().string(containsString("Praia Bonita")))
                .andExpect(content().string(containsString("Recanto")))
                .andExpect(content().string(containsString("Morro Alto")));
    }

    @Test
    void cadastraNovaLocalidadeComTaxa() throws Exception {
        mvc.perform(post("/api/localidades").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","taxa":4.50}""".formatted(nomeUnico("Acaú"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taxa").value(4.50))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void nomeDeLocalidadeRepetidoIgnoraMaiusculas() throws Exception {
        mvc.perform(post("/api/localidades").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"recanto","taxa":6.00}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Já existe uma localidade com esse nome."));
    }

    @Test
    void alteraTaxaDaSacoSemRecriarALocalidade() throws Exception {
        var recanto = localidadeRepository.findAll().stream()
                .filter(l -> l.getNome().equals("Recanto"))
                .findFirst().orElseThrow();

        mvc.perform(put("/api/localidades/" + recanto.getId()).with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Recanto","taxa":6.50,"ativo":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recanto.getId()))
                .andExpect(jsonPath("$.taxa").value(6.50));
    }

    @Test
    void taxaNegativaEhRecusadaComoDadoInvalido() throws Exception {
        mvc.perform(post("/api/localidades").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","taxa":-1}""".formatted(nomeUnico("Acaú"))))
                .andExpect(status().isBadRequest());
    }
}
