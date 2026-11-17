package br.com.pastelariass.caixa;

import br.com.pastelariass.TestcontainersConfiguration;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caixa pela API, contra PostgreSQL real.
 *
 * @Transactional na classe: cada teste roda numa transação que é desfeita no
 * fim. Assim, um caixa aberto num teste não atrapalha o próximo, já que só
 * pode existir um caixa aberto por negócio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class CaixaApiTest {

    @Autowired MockMvc mvc;
    @Autowired CaixaRepository caixaRepository;

    private Long abrir(String negocio, String saldoInicial) throws Exception {
        mvc.perform(post("/api/caixas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"negocio":"%s","saldoInicial":%s}""".formatted(negocio, saldoInicial)))
                .andExpect(status().isCreated());
        return caixaRepository.findByNegocioAndFechadoEmIsNull(Negocio.valueOf(negocio))
                .orElseThrow().getId();
    }

    private void sangria(Long id, String valor) throws Exception {
        mvc.perform(post("/api/caixas/" + id + "/sangrias").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valor":%s}""".formatted(valor)))
                .andExpect(status().isOk());
    }

    /**
     * Pronto quando: abre com R$ 100, sangria de R$ 30, conta R$ 68.
     * O esperado é R$ 70 e a diferença é −R$ 2.
     */
    @Test
    void prontoQuandoBateComOPlano() throws Exception {
        var id = abrir("PASTELARIA", "100.00");
        sangria(id, "30.00");

        mvc.perform(post("/api/caixas/" + id + "/fechar").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valorContado":68.00,"observacao":"faltou R$ 2"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aberto").value(false))
                .andExpect(jsonPath("$.esperado").value(70.00))
                .andExpect(jsonPath("$.valorContado").value(68.00))
                .andExpect(jsonPath("$.diferenca").value(-2.00))
                .andExpect(jsonPath("$.movimentacoes.length()").value(1));
    }

    @Test
    void reforcoSobeOEsperadoDaGaveta() throws Exception {
        var id = abrir("PASTELARIA", "50.00");

        mvc.perform(post("/api/caixas/" + id + "/reforcos").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valor":20.00}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esperado").value(70.00));
    }

    @Test
    void pastelariaEPeixariaTemCaixasAbertosAoMesmoTempo() throws Exception {
        abrir("PASTELARIA", "100.00");
        abrir("PEIXARIA", "200.00");
    }

    @Test
    void naoAbreSegundoCaixaDoMesmoNegocio() throws Exception {
        abrir("PASTELARIA", "100.00");

        mvc.perform(post("/api/caixas").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"negocio":"PASTELARIA","saldoInicial":50.00}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Já existe um caixa aberto para este negócio."));
    }

    @Test
    void sangriaAcimaDoQueHaNaGavetaEhRecusada() throws Exception {
        var id = abrir("PASTELARIA", "100.00");

        mvc.perform(post("/api/caixas/" + id + "/sangrias").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valor":150.00}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void caixaFechadoNaoAceitaMaisMovimentacoes() throws Exception {
        var id = abrir("PASTELARIA", "100.00");
        mvc.perform(post("/api/caixas/" + id + "/fechar").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valorContado":100.00}"""))
                .andExpect(status().isOk());

        mvc.perform(post("/api/caixas/" + id + "/sangrias").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valor":10.00}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Este caixa já está fechado e não pode mais ser alterado."));
    }

    @Test
    void depoisDeFecharNaoHaCaixaAbertoParaONegocio() throws Exception {
        var id = abrir("PEIXARIA", "100.00");
        mvc.perform(post("/api/caixas/" + id + "/fechar").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valorContado":100.00}"""))
                .andExpect(status().isOk());

        mvc.perform(get("/api/caixas/aberto?negocio=PEIXARIA").with(user("ana").roles("EQUIPE")))
                .andExpect(status().isNotFound());
    }

    /** A tela de fechamento mostra a conta aberta: cada parcela e o total que bate com o esperado. */
    @Test
    void caixaAbertoTrazAComposicaoDoEsperadoEFechadoNao() throws Exception {
        var id = abrir("PASTELARIA", "100.00");
        mvc.perform(post("/api/caixas/" + id + "/reforcos").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valor":50.00}"""))
                .andExpect(status().isOk());
        sangria(id, "30.00");

        mvc.perform(get("/api/caixas/aberto?negocio=PASTELARIA").with(user("ana").roles("EQUIPE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.composicao.saldoInicial").value(100.00))
                .andExpect(jsonPath("$.composicao.reforcos").value(50.00))
                .andExpect(jsonPath("$.composicao.sangrias").value(30.00))
                .andExpect(jsonPath("$.composicao.vendasDinheiro").value(0))
                .andExpect(jsonPath("$.composicao.despesas").value(0))
                .andExpect(jsonPath("$.composicao.repasses").value(0))
                .andExpect(jsonPath("$.esperado").value(120.00));

        mvc.perform(post("/api/caixas/" + id + "/fechar").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valorContado":120.00}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.composicao").doesNotExist())
                .andExpect(jsonPath("$.esperado").value(120.00));
    }
}
