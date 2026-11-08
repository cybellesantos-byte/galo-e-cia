package br.com.pastelariass.relatorio;

import br.com.pastelariass.TestcontainersConfiguration;
import br.com.pastelariass.caixa.CaixaService;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.despesa.CategoriaDespesa;
import br.com.pastelariass.despesa.CriacaoDespesaRequest;
import br.com.pastelariass.despesa.DespesaService;
import br.com.pastelariass.localidade.LocalidadeRepository;
import br.com.pastelariass.produto.Produto;
import br.com.pastelariass.produto.ProdutoRepository;
import br.com.pastelariass.produto.UnidadeVenda;
import br.com.pastelariass.venda.FormaPagamento;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Relatórios pela API, contra PostgreSQL real. @Transactional: cada teste é
 * desfeito no fim. Os números são conferidos à mão nos comentários.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class RelatorioApiTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    @Autowired MockMvc mvc;
    @Autowired CaixaService caixaService;
    @Autowired DespesaService despesaService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired LocalidadeRepository localidadeRepository;

    private static LocalDate hoje() {
        return LocalDate.now(ZONA);
    }

    private static String nome(String base) {
        return base + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private Long produto(Negocio negocio, String nome, UnidadeVenda unidade, String preco, String custo) {
        return produtoRepository.saveAndFlush(new Produto(negocio, nome,
                negocio == Negocio.PEIXARIA ? "Peixe" : "Pastel", unidade, new BigDecimal(preco),
                custo == null ? null : new BigDecimal(custo))).getId();
    }

    private void vender(Negocio negocio, String tipo, Long localidadeId, String origem, FormaPagamento forma,
                        String recebido, Long produtoId, String quantidade) throws Exception {
        mvc.perform(post("/api/vendas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chaveDaVenda":"%s","negocio":"%s","origem":"%s","tipo":"%s","localidadeId":%s,
                                 "formaPagamento":"%s","valorRecebido":%s,
                                 "itens":[{"produtoId":%d,"quantidade":%s}]}"""
                                .formatted(UUID.randomUUID(), negocio, origem, tipo,
                                        localidadeId == null ? "null" : localidadeId, forma,
                                        recebido == null ? "null" : recebido, produtoId, quantidade)))
                .andExpect(status().isCreated());
    }

    private void comprarPeixe(String valor, String peso) {
        despesaService.registrar(new CriacaoDespesaRequest(Negocio.PEIXARIA, nome("Tilápia do fornecedor"),
                CategoriaDespesa.COMPRA_DE_PEIXE, new BigDecimal(valor), hoje(), "Ana", FormaPagamento.PIX,
                new BigDecimal(peso), null, null));
    }

    /**
     * Conta à mão, no dia de hoje:
     *  - venda 1: 1,350 kg × R$ 28,00 = R$ 37,80, paga em dinheiro → recebido;
     *  - venda 2: 1,000 kg × R$ 28,00 = R$ 28,00, no Pix, ainda pendente;
     *  - compra de peixe: R$ 500,00 (30 kg), paga por Pix.
     * Faturamento R$ 65,80. Recebido R$ 37,80. Pendente R$ 28,00.
     * Resultado = 37,80 − 500,00 − 0 = −R$ 462,20. Preço médio: 500 / 30 = R$ 16,67.
     */
    @Test
    void prontoQuandoResultadoDaPeixariaBateComAContaAMao() throws Exception {
        caixaService.abrir(Negocio.PEIXARIA, new BigDecimal("100.00"));
        var tilapia = produto(Negocio.PEIXARIA, nome("Tilápia"), UnidadeVenda.KG, "28.00", null);
        vender(Negocio.PEIXARIA, "RETIRADA", null, "BALCAO", FormaPagamento.DINHEIRO, "40.00", tilapia, "1.350");
        vender(Negocio.PEIXARIA, "RETIRADA", null, "BALCAO", FormaPagamento.PIX, null, tilapia, "1.000");
        comprarPeixe("500.00", "30.000");

        mvc.perform(get("/api/relatorios/peixaria?inicio=" + hoje() + "&fim=" + hoje())
                        .with(user("chefe").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comum.faturamento").value(65.80))
                .andExpect(jsonPath("$.comum.recebido").value(37.80))
                .andExpect(jsonPath("$.comum.pendente").value(28.00))
                .andExpect(jsonPath("$.comum.despesasPagas").value(500.00))
                .andExpect(jsonPath("$.compras.total").value(500.00))
                .andExpect(jsonPath("$.compras.kg").value(30.0))
                .andExpect(jsonPath("$.compras.precoMedioPorKg").value(16.67))
                .andExpect(jsonPath("$.kgPorPeixe[0].kg").value(2.35))
                .andExpect(jsonPath("$.resultado").value(-462.20));
    }

    /**
     * Pastelaria: 4 pastéis no balcão (R$ 32,00, dinheiro) e 2 pastéis por
     * entrega no WhatsApp (R$ 16,00 + taxa de Saco, R$ 6,00 = R$ 22,00, Pix).
     * Faturamento R$ 54,00. Taxas R$ 6,00. Margem com custo de R$ 3,00:
     * receita R$ 48,00 − custo 6 × R$ 3,00 = R$ 30,00.
     */
    @Test
    void pastelariaMostraTaxasOrigensEMargem() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        var localidadeSaco = localidadeRepository.findAll().stream()
                .filter(l -> l.getNome().equals("Saco")).findFirst().orElseThrow().getId();
        var pastel = produto(Negocio.PASTELARIA, nome("Pastel de carne"), UnidadeVenda.UNIDADE, "8.00", "3.00");
        vender(Negocio.PASTELARIA, "RETIRADA", null, "BALCAO", FormaPagamento.DINHEIRO, "32.00", pastel, "4");
        mvc.perform(post("/api/vendas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chaveDaVenda":"%s","negocio":"PASTELARIA","origem":"WHATSAPP","tipo":"ENTREGA",
                                 "localidadeId":%d,"formaPagamento":"PIX",
                                 "itens":[{"produtoId":%d,"quantidade":2}]}"""
                                .formatted(UUID.randomUUID(), localidadeSaco, pastel)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/relatorios/pastelaria?inicio=" + hoje() + "&fim=" + hoje())
                        .with(user("chefe").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comum.faturamento").value(54.00))
                .andExpect(jsonPath("$.taxasEntrega.quantidadeEntregas").value(1))
                .andExpect(jsonPath("$.taxasEntrega.totalTaxas").value(6.00))
                .andExpect(jsonPath("$.origens[0].total").value(32.00))
                .andExpect(jsonPath("$.origens[1].total").value(22.00))
                .andExpect(jsonPath("$.margens[0].receita").value(48.00))
                .andExpect(jsonPath("$.margens[0].margem").value(30.00));
    }

    /** Produto sem custo cadastrado: a margem fica nula (incompleta), e não zero. */
    @Test
    void margemSemCustoFicaIncompleta() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        var sem = produto(Negocio.PASTELARIA, nome("Pastel sem custo"), UnidadeVenda.UNIDADE, "8.00", null);
        vender(Negocio.PASTELARIA, "RETIRADA", null, "BALCAO", FormaPagamento.DINHEIRO, "8.00", sem, "1");

        mvc.perform(get("/api/relatorios/pastelaria?inicio=" + hoje() + "&fim=" + hoje())
                        .with(user("chefe").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.margens[0].receita").value(8.00))
                .andExpect(jsonPath("$.margens[0].margem").doesNotExist());
    }

    /** Geral: um caixa de cada negócio, somados. Dinheiro R$ 32,00 + Pix R$ 28,00. */
    @Test
    void geralSomaOsDoisNegocios() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        caixaService.abrir(Negocio.PEIXARIA, new BigDecimal("100.00"));
        var pastel = produto(Negocio.PASTELARIA, nome("Pastel"), UnidadeVenda.UNIDADE, "8.00", null);
        var tilapia = produto(Negocio.PEIXARIA, nome("Tilápia"), UnidadeVenda.KG, "28.00", null);
        vender(Negocio.PASTELARIA, "RETIRADA", null, "BALCAO", FormaPagamento.DINHEIRO, "32.00", pastel, "4");
        vender(Negocio.PEIXARIA, "RETIRADA", null, "BALCAO", FormaPagamento.PIX, null, tilapia, "1.000");

        mvc.perform(get("/api/relatorios/geral?inicio=" + hoje() + "&fim=" + hoje())
                        .with(user("chefe").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamento").value(60.00))
                .andExpect(jsonPath("$.recebido").value(32.00))
                .andExpect(jsonPath("$.pendente").value(28.00))
                .andExpect(jsonPath("$.pix").value(28.00))
                .andExpect(jsonPath("$.dinheiro").value(32.00));
    }

    @Test
    void compraDePeixeAparecENoMesCertoDoComparativoAnual() throws Exception {
        despesaService.registrar(new CriacaoDespesaRequest(Negocio.PEIXARIA, nome("Tilápia"),
                CategoriaDespesa.COMPRA_DE_PEIXE, new BigDecimal("500.00"), LocalDate.of(2020, 3, 10), "Ana",
                FormaPagamento.PIX, new BigDecimal("30.000"), null, null));

        mvc.perform(get("/api/relatorios/peixaria/compras-mensais?ano=2020").with(user("chefe").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meses[2].total").value(500.00))
                .andExpect(jsonPath("$.meses[2].precoMedioPorKg").value(16.67))
                .andExpect(jsonPath("$.meses[0].total").value(0));
    }

    @Test
    void periodoComFimAntesDoInicioEhRecusado() throws Exception {
        mvc.perform(get("/api/relatorios/geral?inicio=2026-10-10&fim=2026-10-01")
                        .with(user("chefe").roles("ADMIN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("A data final não pode ser antes da inicial."));
    }

    /**
     * O custo vale o do MOMENTO da venda. Vende 2 pastéis com custo R$ 3,00 e,
     * depois, o custo no cadastro sobe para R$ 4,00. A margem dessa venda continua
     * receita R$ 16,00 − 2 × R$ 3,00 = R$ 10,00 (com o custo novo daria R$ 8,00).
     */
    @Test
    void margemUsaOCustoDoMomentoDaVenda() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        var pastelId = produto(Negocio.PASTELARIA, nome("Pastel de queijo"), UnidadeVenda.UNIDADE, "8.00", "3.00");
        vender(Negocio.PASTELARIA, "RETIRADA", null, "BALCAO", FormaPagamento.DINHEIRO, "16.00", pastelId, "2");

        var pastel = produtoRepository.findById(pastelId).orElseThrow();
        pastel.alterarDados(pastel.getNome(), pastel.getCategoria(), pastel.getPreco(), new BigDecimal("4.00"));
        produtoRepository.saveAndFlush(pastel);

        mvc.perform(get("/api/relatorios/pastelaria?inicio=" + hoje() + "&fim=" + hoje())
                        .with(user("chefe").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.margens[0].custoEstimado").value(6.00))
                .andExpect(jsonPath("$.margens[0].margem").value(10.00));
    }

    /** A equipe do balcão não vê relatório nenhum: os quatro endpoints dão 403. */
    @Test
    void equipeNaoVeRelatorios() throws Exception {
        var periodo = "?inicio=" + hoje() + "&fim=" + hoje();
        for (var url : List.of("/api/relatorios/pastelaria" + periodo, "/api/relatorios/peixaria" + periodo,
                "/api/relatorios/geral" + periodo, "/api/relatorios/peixaria/compras-mensais?ano=2026")) {
            mvc.perform(get(url).with(user("ana").roles("EQUIPE")))
                    .andExpect(status().isForbidden());
        }
    }
}
