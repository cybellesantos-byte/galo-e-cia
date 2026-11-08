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
// Transacional: a alteração da taxa do Saco é desfeita no fim, e não vaza para outros testes.
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
                .andExpect(content().string(containsString("Lagoa de Praia")))
                .andExpect(content().string(containsString("Barra de Mamanguape")))
                .andExpect(content().string(containsString("Praia de Campina")))
                .andExpect(content().string(containsString("Saco")))
                .andExpect(content().string(containsString("Tanques")));
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
                                {"nome":"saco","taxa":6.00}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Já existe uma localidade com esse nome."));
    }

    @Test
    void alteraTaxaDaSacoSemRecriarALocalidade() throws Exception {
        var saco = localidadeRepository.findAll().stream()
                .filter(l -> l.getNome().equals("Saco"))
                .findFirst().orElseThrow();

        mvc.perform(put("/api/localidades/" + saco.getId()).with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Saco","taxa":6.50,"ativo":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saco.getId()))
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
