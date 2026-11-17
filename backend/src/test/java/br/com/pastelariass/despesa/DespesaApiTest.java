package br.com.pastelariass.despesa;

import br.com.pastelariass.TestcontainersConfiguration;
import br.com.pastelariass.caixa.CaixaService;
import br.com.pastelariass.comum.Negocio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Despesas pela API, contra PostgreSQL real.
 *
 * As datas são de 2020, sempre no passado. @Transactional na classe: cada
 * teste é desfeito no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class DespesaApiTest {

    @Autowired MockMvc mvc;
    @Autowired CaixaService caixaService;
    @Autowired DespesaRepository despesaRepository;

    private String descricaoUnica() {
        return "Despesa " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String corpo(String negocio, String descricao, String categoria, String valor,
                                String forma, String peso) {
        return """
                {"negocio":"%s","descricao":"%s","categoria":"%s","valor":%s,"data":"2020-01-15",
                 "responsavel":"Ana","formaPagamento":%s,"pesoKg":%s}"""
                .formatted(negocio, descricao, categoria, valor,
                        forma == null ? "null" : "\"" + forma + "\"",
                        peso == null ? "null" : peso);
    }

    private void registrar(String json) throws Exception {
        mvc.perform(post("/api/despesas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private Long idDaDespesa(String descricao) {
        return despesaRepository.findAll().stream()
                .filter(d -> d.getDescricao().equals(descricao))
                .findFirst().orElseThrow().getId();
    }

    /**
     * Pronto quando: compra de R$ 500 (30 kg) aparece no gasto mensal da peixaria.
     * O preço médio é R$ 16,67 por kg.
     */
    @Test
    void prontoQuandoCompraDePeixeAparecenoGastoMensalDaPeixaria() throws Exception {
        registrar(corpo("PEIXARIA", descricaoUnica(), "COMPRA_DE_PEIXE", "500.00", "PIX", "30.000"));

        mvc.perform(get("/api/despesas/resumo?mes=2020-01&negocio=PEIXARIA").with(user("ana").roles("EQUIPE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGasto").value(500.00))
                .andExpect(jsonPath("$.totalPago").value(500.00))
                .andExpect(jsonPath("$.kgComprados").value(30.0))
                .andExpect(jsonPath("$.precoMedioPorKg").value(16.67));
    }

    /** Pendente não mexe na gaveta. Pagar em dinheiro tira o valor da gaveta. */
    @Test
    void despesaPendentePagaEmDinheiroTiraDaGaveta() throws Exception {
        caixaService.abrir(Negocio.PEIXARIA, new BigDecimal("100.00"));
        var descricao = descricaoUnica();
        registrar(corpo("PEIXARIA", descricao, "OUTROS", "20.00", null, null));
        assertThat(caixaService.esperadoAtual(caixaService.caixaAberto(Negocio.PEIXARIA)))
                .isEqualByComparingTo("100.00");

        var id = idDaDespesa(descricao);
        mvc.perform(post("/api/despesas/" + id + "/pagar").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"formaPagamento":"DINHEIRO"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PAGO"));
        assertThat(caixaService.esperadoAtual(caixaService.caixaAberto(Negocio.PEIXARIA)))
                .isEqualByComparingTo("80.00");

        mvc.perform(post("/api/despesas/" + id + "/pagar").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"formaPagamento":"DINHEIRO"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Esta despesa já foi paga."));
    }

    @Test
    void despesaPagaPorPixNaoMexeNaGaveta() throws Exception {
        caixaService.abrir(Negocio.PEIXARIA, new BigDecimal("100.00"));

        registrar(corpo("PEIXARIA", descricaoUnica(), "MANUTENCAO", "30.00", "PIX", null));

        assertThat(caixaService.esperadoAtual(caixaService.caixaAberto(Negocio.PEIXARIA)))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void despesaPagaEmDinheiroSemCaixaAbertoEhRecusada() throws Exception {
        mvc.perform(post("/api/despesas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("PASTELARIA", descricaoUnica(), "INGREDIENTES", "30.00", "DINHEIRO", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Abra o caixa da pastelaria antes de pagar em dinheiro."));
    }

    @Test
    void compraDePeixeNaPastelariaEhRecusada() throws Exception {
        mvc.perform(post("/api/despesas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("PASTELARIA", descricaoUnica(), "COMPRA_DE_PEIXE", "100.00", "PIX", "5")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Compra de peixe é uma despesa da peixaria."));
    }

    /** Compra de ingredientes detalhada: o valor sai da soma (15,00 + 55,00) e os itens voltam. */
    @Test
    void compraDeIngredientesGuardaCadaItemEOValorDaSoma() throws Exception {
        mvc.perform(post("/api/despesas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"negocio":"PASTELARIA","descricao":"Compra da semana","categoria":"INGREDIENTES",
                                 "data":"2020-01-15","responsavel":"Ana","formaPagamento":"PIX",
                                 "itens":[{"descricao":"Ovo","quantidade":30,"unidade":"UNIDADE","precoUnitario":0.50},
                                          {"descricao":"Farinha","quantidade":10,"unidade":"KG","precoUnitario":5.50}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor").value(70.00))
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[1].descricao").value("Farinha"))
                .andExpect(jsonPath("$.itens[1].total").value(55.00));
    }

    /** O resumo separa o que já foi pago do que ainda está pendente. */
    @Test
    void resumoSeparaPagoDePendente() throws Exception {
        registrar(corpo("PEIXARIA", descricaoUnica(), "COMPRA_DE_PEIXE", "500.00", "PIX", "30.000"));
        registrar(corpo("PEIXARIA", descricaoUnica(), "OUTROS", "20.00", null, null));

        mvc.perform(get("/api/despesas/resumo?mes=2020-01&negocio=PEIXARIA").with(user("ana").roles("EQUIPE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGasto").value(520.00))
                .andExpect(jsonPath("$.totalPago").value(500.00))
                .andExpect(jsonPath("$.totalPendente").value(20.00));
    }
}
