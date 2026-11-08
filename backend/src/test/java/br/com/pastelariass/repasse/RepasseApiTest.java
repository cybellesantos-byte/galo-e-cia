package br.com.pastelariass.repasse;

import br.com.pastelariass.TestcontainersConfiguration;
import br.com.pastelariass.caixa.CaixaService;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.localidade.LocalidadeRepository;
import br.com.pastelariass.produto.Produto;
import br.com.pastelariass.produto.ProdutoRepository;
import br.com.pastelariass.produto.UnidadeVenda;
import br.com.pastelariass.venda.Venda;
import br.com.pastelariass.venda.VendaRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Repasses e entregas pela API, contra PostgreSQL real. @Transactional: cada
 * teste é desfeito no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class RepasseApiTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    @Autowired MockMvc mvc;
    @Autowired CaixaService caixaService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired LocalidadeRepository localidadeRepository;
    @Autowired VendaRepository vendaRepository;
    @Autowired RepasseRepository repasseRepository;

    private Long pastelDeCarne() {
        var nome = "Pastel de carne " + UUID.randomUUID().toString().substring(0, 8);
        return produtoRepository.saveAndFlush(new Produto(Negocio.PASTELARIA, nome, "Pastel",
                UnidadeVenda.UNIDADE, new BigDecimal("8.00"), null)).getId();
    }

    private Long localidade(String nome) {
        return localidadeRepository.findAll().stream()
                .filter(l -> l.getNome().equals(nome))
                .findFirst().orElseThrow().getId();
    }

    private static String corpoEntrega(UUID chave, Long localidadeId, String forma, String taxaInformada,
                                       Long produtoId) {
        return """
                {"chaveDaVenda":"%s","negocio":"PASTELARIA","origem":"WHATSAPP","tipo":"ENTREGA",
                 "localidadeId":%d,"formaPagamento":"%s","taxaEntrega":%s,
                 "itens":[{"produtoId":%d,"quantidade":2}]}"""
                .formatted(chave, localidadeId, forma, taxaInformada == null ? "null" : taxaInformada, produtoId);
    }

    private void vender(String json) throws Exception {
        mvc.perform(post("/api/vendas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    /** A venda mais recente do caixa aberto: a que o teste acabou de registrar. */
    private Venda ultimaVenda() {
        var caixaId = caixaService.caixaAberto(Negocio.PASTELARIA).getId();
        var vendas = vendaRepository.findByCaixaIdOrderByCriadoEmAsc(caixaId);
        return vendas.get(vendas.size() - 1);
    }

    private Long repasseDaUltimaVenda() {
        return repasseRepository.findByVendaId(ultimaVenda().getId()).orElseThrow().getId();
    }

    private LocalDate hoje() {
        return LocalDate.now(ZONA);
    }

    /**
     * Entrega com taxa cobrada de R$ 8,00, sendo a padrão de R$ 6,00 (Saco):
     * o repasse é de R$ 8,00. O extra fica com o entregador.
     */
    @Test
    void prontoQuandoTaxaCobradaMaiorVaiInteiraParaOEntregador() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        vender(corpoEntrega(UUID.randomUUID(), localidade("Saco"), "PIX", "8.00", pastelDeCarne()));

        mvc.perform(get("/api/repasses?inicio=" + hoje() + "&fim=" + hoje()).with(user("ana").roles("EQUIPE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].valor").value(8.00))
                .andExpect(jsonPath("$[0].situacao").value("PENDENTE"));
    }

    /** Sem taxa informada, o repasse é a taxa padrão da localidade. */
    @Test
    void semTaxaInformadaORepasseEAPadrao() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        vender(corpoEntrega(UUID.randomUUID(), localidade("Saco"), "PIX", null, pastelDeCarne()));

        mvc.perform(get("/api/repasses?inicio=" + hoje() + "&fim=" + hoje()).with(user("ana").roles("EQUIPE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].valor").value(6.00));
    }

    /** Pagar em dinheiro tira o repasse da gaveta, e pagar de novo é recusado. */
    @Test
    void repassePagoEmDinheiroTiraDaGavetaEUmaSoVez() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        vender(corpoEntrega(UUID.randomUUID(), localidade("Saco"), "PIX", null, pastelDeCarne()));
        var id = repasseDaUltimaVenda();

        mvc.perform(post("/api/repasses/" + id + "/pagar").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"formaPagamento":"DINHEIRO"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PAGO"));
        assertThat(caixaService.esperadoAtual(caixaService.caixaAberto(Negocio.PASTELARIA)))
                .isEqualByComparingTo("94.00");

        mvc.perform(post("/api/repasses/" + id + "/pagar").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"formaPagamento":"DINHEIRO"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Este repasse já foi pago."));
    }

    /** A EQUIPE não paga repasse: é dinheiro que sai para o entregador. */
    @Test
    void equipeNaoPagaRepasse() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        vender(corpoEntrega(UUID.randomUUID(), localidade("Saco"), "PIX", null, pastelDeCarne()));
        var id = repasseDaUltimaVenda();

        mvc.perform(post("/api/repasses/" + id + "/pagar").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"formaPagamento":"PIX"}"""))
                .andExpect(status().isForbidden());
    }

    /** Entrega já repassada não pode ser cancelada; entrega ainda pendente cancela o repasse. */
    @Test
    void cancelarEntregaRespeitaORepasse() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        vender(corpoEntrega(UUID.randomUUID(), localidade("Saco"), "PIX", null, pastelDeCarne()));
        var venda = ultimaVenda();
        var repasseId = repasseDaUltimaVenda();

        mvc.perform(post("/api/repasses/" + repasseId + "/pagar").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"formaPagamento":"PIX"}"""))
                .andExpect(status().isOk());

        mvc.perform(post("/api/vendas/" + venda.getId() + "/cancelar").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"motivo":"erro"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(
                        "Esta entrega já foi repassada ao entregador e não pode ser cancelada."));
    }

    @Test
    void cancelarEntregaAindaPendenteCancelaORepasse() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        vender(corpoEntrega(UUID.randomUUID(), localidade("Saco"), "PIX", null, pastelDeCarne()));
        var venda = ultimaVenda();

        mvc.perform(post("/api/vendas/" + venda.getId() + "/cancelar").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"motivo":"cliente desistiu"}"""))
                .andExpect(status().isOk());

        assertThat(repasseRepository.findByVendaId(venda.getId()).orElseThrow().isCancelado()).isTrue();
    }

    // ── Pagar várias de uma vez ──────────────────────────────────────────────

    private static String lote(String forma, Long... ids) {
        var lista = java.util.Arrays.stream(ids).map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        return """
                {"ids":[%s],"formaPagamento":"%s"}""".formatted(lista, forma);
    }

    /**
     * O acerto da semana: duas entregas (Saco R$ 6 + Tanques R$ 7) pagas juntas,
     * em dinheiro. As duas ficam pagas, o total é R$ 13, e sai R$ 13 da gaveta.
     */
    @Test
    void adminPagaVariasDeUmaVezEmDinheiroESaiDaGaveta() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        var produto = pastelDeCarne();
        vender(corpoEntrega(UUID.randomUUID(), localidade("Saco"), "PIX", null, produto));
        var saco = repasseDaUltimaVenda();
        vender(corpoEntrega(UUID.randomUUID(), localidade("Tanques"), "PIX", null, produto));
        var tanques = repasseDaUltimaVenda();

        mvc.perform(post("/api/repasses/pagar").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(lote("DINHEIRO", saco, tanques)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(2))
                .andExpect(jsonPath("$.total").value(13.00))
                .andExpect(jsonPath("$.repasses[0].situacao").value("PAGO"))
                .andExpect(jsonPath("$.repasses[1].situacao").value("PAGO"));

        // As vendas foram no Pix (não entram na gaveta); os repasses saíram dela: 100 − 13.
        assertThat(caixaService.esperadoAtual(caixaService.caixaAberto(Negocio.PASTELARIA)))
                .isEqualByComparingTo("87.00");
    }

    /** Uma já paga no meio do lote: NENHUMA é paga, e a outra continua pendente. */
    @Test
    void loteComUmaJaPagaNaoPagaNenhuma() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        var produto = pastelDeCarne();
        vender(corpoEntrega(UUID.randomUUID(), localidade("Saco"), "PIX", null, produto));
        var primeira = repasseDaUltimaVenda();
        vender(corpoEntrega(UUID.randomUUID(), localidade("Tanques"), "PIX", null, produto));
        var segunda = repasseDaUltimaVenda();
        mvc.perform(post("/api/repasses/" + primeira + "/pagar").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"formaPagamento":"PIX"}"""))
                .andExpect(status().isOk());

        mvc.perform(post("/api/repasses/pagar").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(lote("PIX", primeira, segunda)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.startsWith("Nenhuma entrega foi paga")));

        assertThat(repasseRepository.findById(segunda).orElseThrow().getSituacao()).isEqualTo(SituacaoRepasse.PENDENTE);
    }

    @Test
    void equipeNaoPagaEmLote() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        vender(corpoEntrega(UUID.randomUUID(), localidade("Saco"), "PIX", null, pastelDeCarne()));

        mvc.perform(post("/api/repasses/pagar").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(lote("PIX", repasseDaUltimaVenda())))
                .andExpect(status().isForbidden());
    }

    @Test
    void loteVazioEhRecusadoNaValidacao() throws Exception {
        mvc.perform(post("/api/repasses/pagar").with(user("chefe").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"ids":[],"formaPagamento":"PIX"}"""))
                .andExpect(status().isBadRequest());
    }

    /** A lista mostra o nome do local de cada entrega (para o acerto da semana). */
    @Test
    void listaMostraONomeDoLocalDaEntrega() throws Exception {
        caixaService.abrir(Negocio.PASTELARIA, new BigDecimal("100.00"));
        vender(corpoEntrega(UUID.randomUUID(), localidade("Saco"), "PIX", null, pastelDeCarne()));

        mvc.perform(get("/api/repasses?inicio=" + hoje() + "&fim=" + hoje()).with(user("ana").roles("EQUIPE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].localidade").value("Saco"));
    }
}
