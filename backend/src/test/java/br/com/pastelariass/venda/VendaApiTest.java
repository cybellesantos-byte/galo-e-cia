package br.com.pastelariass.venda;

import br.com.pastelariass.TestcontainersConfiguration;
import br.com.pastelariass.caixa.CaixaService;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.localidade.LocalidadeRepository;
import br.com.pastelariass.produto.Produto;
import br.com.pastelariass.produto.ProdutoRepository;
import br.com.pastelariass.produto.UnidadeVenda;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vendas pela API, contra PostgreSQL real, para os dois negócios.
 *
 * @Transactional na classe: cada teste é desfeito no fim, então o caixa e as
 * vendas de um teste não aparecem no próximo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class VendaApiTest {

    @Autowired MockMvc mvc;
    @Autowired CaixaService caixaService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired LocalidadeRepository localidadeRepository;
    @Autowired VendaRepository vendaRepository;

    private Long pastelDeCarne() {
        var nome = "Pastel de carne " + UUID.randomUUID().toString().substring(0, 8);
        return produtoRepository.saveAndFlush(new Produto(Negocio.PASTELARIA, nome, "Pastel",
                UnidadeVenda.UNIDADE, new BigDecimal("8.00"), null)).getId();
    }

    private Long tilapia() {
        var nome = "Tilápia " + UUID.randomUUID().toString().substring(0, 8);
        return produtoRepository.saveAndFlush(new Produto(Negocio.PEIXARIA, nome, "Peixe",
                UnidadeVenda.KG, new BigDecimal("28.00"), null)).getId();
    }

    private Long localidade(String nome) {
        return localidadeRepository.findAll().stream()
                .filter(l -> l.getNome().equals(nome))
                .findFirst().orElseThrow().getId();
    }

    private void abrirCaixa(Negocio negocio, String saldoInicial) {
        caixaService.abrir(negocio, new BigDecimal(saldoInicial));
    }

    private Long caixaId(Negocio negocio) {
        return caixaService.caixaAberto(negocio).getId();
    }

    /** Pedido da pastelaria, com quantidade inteira. */
    private static String corpo(UUID chave, String tipo, Long localidadeId, String forma,
                                String recebido, Long produtoId, String quantidade) {
        return corpoDo("PASTELARIA", chave, tipo, localidadeId, forma, recebido, produtoId, quantidade);
    }

    private static String corpoDo(String negocio, UUID chave, String tipo, Long localidadeId, String forma,
                                  String recebido, Long produtoId, String quantidade) {
        return """
                {"chaveDaVenda":"%s","negocio":"%s","origem":"BALCAO","tipo":"%s","localidadeId":%s,
                 "formaPagamento":"%s","valorRecebido":%s,
                 "itens":[{"produtoId":%d,"quantidade":%s}]}"""
                .formatted(chave, negocio, tipo, localidadeId == null ? "null" : localidadeId, forma,
                        recebido == null ? "null" : recebido, produtoId, quantidade);
    }

    private void vender(String json) throws Exception {
        mvc.perform(post("/api/vendas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private BigDecimal esperadoDo(Negocio negocio) {
        return caixaService.esperadoAtual(caixaService.caixaAberto(negocio));
    }

    /**
     * Pronto quando: venda com troco aparece no caixa aberto e o esperado bate.
     * Venda de R$ 32 (4 pastéis), paga com R$ 50: troco de R$ 18. Entram R$ 32 na gaveta.
     */
    @Test
    void prontoQuandoVendaComTrocoEntraNoCaixaComOValorLiquido() throws Exception {
        abrirCaixa(Negocio.PASTELARIA, "100.00");

        mvc.perform(post("/api/vendas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID(), "RETIRADA", null, "DINHEIRO", "50.00", pastelDeCarne(), "4")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(32.00))
                .andExpect(jsonPath("$.troco").value(18.00))
                .andExpect(jsonPath("$.situacaoPagamento").value("PAGO"));

        assertThat(esperadoDo(Negocio.PASTELARIA)).isEqualByComparingTo("132.00");
    }

    /**
     * Pronto quando: 1,350 kg de tilápia a R$ 28,00/kg = R$ 37,80 no caixa da peixaria.
     * A venda não toca o caixa da pastelaria.
     */
    @Test
    void prontoQuandoTilapiaPorPesoEntraSoNoCaixaDaPeixaria() throws Exception {
        abrirCaixa(Negocio.PASTELARIA, "100.00");
        abrirCaixa(Negocio.PEIXARIA, "200.00");

        mvc.perform(post("/api/vendas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDo("PEIXARIA", UUID.randomUUID(), "RETIRADA", null, "DINHEIRO", "50.00",
                                tilapia(), "1.350")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(37.80))
                .andExpect(jsonPath("$.troco").value(12.20));

        assertThat(esperadoDo(Negocio.PEIXARIA)).isEqualByComparingTo("237.80");
        assertThat(esperadoDo(Negocio.PASTELARIA)).isEqualByComparingTo("100.00");
    }

    /** Pastel é vendido por unidade: meio pastel não existe. */
    @Test
    void pastelNaoAceitaQuantidadeFracionada() throws Exception {
        abrirCaixa(Negocio.PASTELARIA, "100.00");

        mvc.perform(post("/api/vendas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID(), "RETIRADA", null, "PIX", null, pastelDeCarne(), "1.5")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(containsString("vendido por unidade")));
    }

    @Test
    void pixNaoEntraNaGavetaEFicaPendenteAteSerConfirmado() throws Exception {
        abrirCaixa(Negocio.PASTELARIA, "100.00");

        mvc.perform(post("/api/vendas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID(), "RETIRADA", null, "PIX", null, pastelDeCarne(), "2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacaoPagamento").value("PENDENTE"));
        assertThat(esperadoDo(Negocio.PASTELARIA)).isEqualByComparingTo("100.00");

        var venda = vendaRepository.findByCaixaIdOrderByCriadoEmAsc(caixaId(Negocio.PASTELARIA)).get(0);
        mvc.perform(post("/api/vendas/" + venda.getId() + "/confirmar-pagamento")
                        .with(user("ana").roles("EQUIPE")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacaoPagamento").value("PAGO"));
        assertThat(esperadoDo(Negocio.PASTELARIA)).isEqualByComparingTo("100.00");
    }

    /** Cartão, como o Pix: fica pendente, não entra na gaveta e é confirmado depois. */
    @Test
    void cartaoNaoEntraNaGavetaEFicaPendenteAteSerConfirmado() throws Exception {
        abrirCaixa(Negocio.PASTELARIA, "100.00");

        mvc.perform(post("/api/vendas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID(), "RETIRADA", null, "CARTAO", null, pastelDeCarne(), "2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formaPagamento").value("CARTAO"))
                .andExpect(jsonPath("$.situacaoPagamento").value("PENDENTE"));
        assertThat(esperadoDo(Negocio.PASTELARIA)).isEqualByComparingTo("100.00");

        var venda = vendaRepository.findByCaixaIdOrderByCriadoEmAsc(caixaId(Negocio.PASTELARIA)).get(0);
        mvc.perform(post("/api/vendas/" + venda.getId() + "/confirmar-pagamento")
                        .with(user("ana").roles("EQUIPE")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacaoPagamento").value("PAGO"));
        assertThat(esperadoDo(Negocio.PASTELARIA)).isEqualByComparingTo("100.00");
    }

    /** A taxa da localidade entra no total, congelada no momento da venda. */
    @Test
    void entregaCobraATaxaDaLocalidadeCongelada() throws Exception {
        abrirCaixa(Negocio.PASTELARIA, "100.00");

        mvc.perform(post("/api/vendas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID(), "ENTREGA", localidade("Saco"), "DINHEIRO", "50.00", pastelDeCarne(), "4")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taxaEntrega").value(6.00))
                .andExpect(jsonPath("$.total").value(38.00))
                .andExpect(jsonPath("$.troco").value(12.00));

        assertThat(esperadoDo(Negocio.PASTELARIA)).isEqualByComparingTo("138.00");
    }

    @Test
    void produtoEsgotadoNaoPodeSerVendido() throws Exception {
        abrirCaixa(Negocio.PASTELARIA, "100.00");
        var produto = produtoRepository.findById(pastelDeCarne()).orElseThrow();
        produto.alterarDisponivel(false);
        produtoRepository.saveAndFlush(produto);

        mvc.perform(post("/api/vendas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID(), "RETIRADA", null, "PIX", null, produto.getId(), "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(containsString("está esgotado")));
    }

    /** Cancelar uma venda em dinheiro devolve o valor para o esperado. */
    @Test
    void cancelarVendaEmDinheiroTiraDoEsperadoComMotivo() throws Exception {
        abrirCaixa(Negocio.PASTELARIA, "100.00");
        vender(corpo(UUID.randomUUID(), "RETIRADA", null, "DINHEIRO", "32.00", pastelDeCarne(), "4"));
        assertThat(esperadoDo(Negocio.PASTELARIA)).isEqualByComparingTo("132.00");

        var venda = vendaRepository.findByCaixaIdOrderByCriadoEmAsc(caixaId(Negocio.PASTELARIA)).get(0);
        mvc.perform(post("/api/vendas/" + venda.getId() + "/cancelar").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"cliente desistiu"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelada").value(true))
                .andExpect(jsonPath("$.motivoCancelamento").value("cliente desistiu"));
        assertThat(esperadoDo(Negocio.PASTELARIA)).isEqualByComparingTo("100.00");
    }

    /** Mesma chave enviada duas vezes (duplo clique): uma venda só. */
    @Test
    void mesmaChaveNaoGeraDuasVendas() throws Exception {
        abrirCaixa(Negocio.PASTELARIA, "100.00");
        var produto = pastelDeCarne();
        var chave = UUID.randomUUID();

        vender(corpo(chave, "RETIRADA", null, "DINHEIRO", "32.00", produto, "4"));
        vender(corpo(chave, "RETIRADA", null, "DINHEIRO", "32.00", produto, "4"));

        assertThat(vendaRepository.findByCaixaIdOrderByCriadoEmAsc(caixaId(Negocio.PASTELARIA))).hasSize(1);
        assertThat(esperadoDo(Negocio.PASTELARIA)).isEqualByComparingTo("132.00");
    }

    @Test
    void semCaixaDaPastelariaAbertoNaoHaVenda() throws Exception {
        mvc.perform(post("/api/vendas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID(), "RETIRADA", null, "PIX", null, pastelDeCarne(), "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Abra o caixa da pastelaria antes de registrar vendas."));
    }

    @Test
    void vendaDeCaixaFechadoNaoPodeSerCancelada() throws Exception {
        abrirCaixa(Negocio.PASTELARIA, "100.00");
        var caixa = caixaId(Negocio.PASTELARIA);
        vender(corpo(UUID.randomUUID(), "RETIRADA", null, "PIX", null, pastelDeCarne(), "1"));
        var venda = vendaRepository.findByCaixaIdOrderByCriadoEmAsc(caixa).get(0);
        caixaService.fechar(caixa, new BigDecimal("100.00"), null);

        mvc.perform(post("/api/vendas/" + venda.getId() + "/cancelar").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"erro"}"""))
                .andExpect(status().isUnprocessableEntity());
    }
}
