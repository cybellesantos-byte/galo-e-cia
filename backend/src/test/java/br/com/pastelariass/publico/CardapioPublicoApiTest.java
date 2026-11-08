package br.com.pastelariass.publico;

import br.com.pastelariass.TestcontainersConfiguration;
import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.loja.LojaService;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O cardápio público contra PostgreSQL real, SEM LOGIN em nenhum pedido (de propósito):
 * é assim que o cliente chega. O mais importante aqui é o que NÃO pode sair.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional   // cada teste é desfeito no fim: produtos e loja criados aqui não vazam para os outros
class CardapioPublicoApiTest {

    @Autowired MockMvc mvc;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired LojaService lojaService;

    private static String nome(String base) {
        return base + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private Produto pastel(String nome, String categoria, String preco, String custo, String descricao) {
        var produto = new Produto(Negocio.PASTELARIA, nome, categoria, UnidadeVenda.UNIDADE,
                new BigDecimal(preco), new BigDecimal(custo));
        produto.alterarDescricao(descricao);
        return produtoRepository.saveAndFlush(produto);
    }

    @Test
    void qualquerPessoaVeOCardapioSemLogin() throws Exception {
        var nome = nome("Pastel de carne");
        pastel(nome, "Simples", "8.00", "3.37", "carne moída temperada");

        mvc.perform(get("/api/publico/cardapio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.nome == '%s')].preco".formatted(nome)).value(8.00))
                .andExpect(jsonPath("$.itens[?(@.nome == '%s')].descricao".formatted(nome)).value("carne moída temperada"))
                .andExpect(jsonPath("$.itens[?(@.nome == '%s')].categoria".formatted(nome)).value("Simples"))
                .andExpect(jsonPath("$.itens[?(@.nome == '%s')].esgotado".formatted(nome)).value(false));
    }

    /** O teste mais importante da Parte B: dados internos NUNCA saem pela porta pública. */
    @Test
    void nuncaMostraCustoMargemIdsNemOutrosDadosInternos() throws Exception {
        pastel(nome("Pastel secreto"), "Simples", "8.00", "3.37", "descrição pública");

        var resposta = mvc.perform(get("/api/publico/cardapio"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // O valor do custo (3.37) não aparece em lugar nenhum, nem como número solto.
        assertThat(resposta).doesNotContain("3.37");
        // E nenhum campo interno aparece pelo nome.
        for (var proibido : new String[]{"custo", "margem", "\"id\"", "ativo", "disponivel", "negocio",
                "alteradoPor", "alteradoEm", "usuario", "senha"}) {
            assertThat(resposta).as("campo proibido: " + proibido).doesNotContain(proibido);
        }
    }

    @Test
    void produtoForaDaListaNaoApareceMasEsgotadoApareceMarcado() throws Exception {
        var esgotado = pastel(nome("Pastel esgotado"), "Simples", "8.00", "3.00", null);
        esgotado.alterarDisponivel(false);
        var foraDaLista = pastel(nome("Pastel fora da lista"), "Simples", "8.00", "3.00", null);
        foraDaLista.alterarAtivo(false);
        produtoRepository.saveAllAndFlush(java.util.List.of(esgotado, foraDaLista));

        mvc.perform(get("/api/publico/cardapio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.nome == '%s')].esgotado".formatted(esgotado.getNome())).value(true))
                .andExpect(jsonPath("$.itens[*].nome", not(hasItem(foraDaLista.getNome()))));
    }

    @Test
    void produtosDaPeixariaNuncaAparecem() throws Exception {
        var tilapia = nome("Tilápia");
        produtoRepository.saveAndFlush(new Produto(Negocio.PEIXARIA, tilapia, "Peixe", UnidadeVenda.KG,
                new BigDecimal("28.00"), null));

        mvc.perform(get("/api/publico/cardapio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].nome", not(hasItem(tilapia))));
    }

    @Test
    void mostraSeALojaEstaAbertaEOWhatsappParaOBotao() throws Exception {
        mvc.perform(get("/api/publico/cardapio"))
                .andExpect(jsonPath("$.aberta").value(false))   // a loja nasce fechada
                .andExpect(jsonPath("$.whatsapp").doesNotExist());

        lojaService.definirAberta(Negocio.PASTELARIA, true);
        lojaService.definirWhatsapp(Negocio.PASTELARIA, "(83) 99999-8888");

        mvc.perform(get("/api/publico/cardapio"))
                .andExpect(jsonPath("$.aberta").value(true))
                .andExpect(jsonPath("$.whatsapp").value("5583999998888"));
    }

    @Test
    void abrirAPeixariaNaoAbreOCardapioDaPastelaria() throws Exception {
        lojaService.definirAberta(Negocio.PEIXARIA, true);

        mvc.perform(get("/api/publico/cardapio"))
                .andExpect(jsonPath("$.aberta").value(false));
    }

    @Test
    void trazOsLocaisDeEntregaAtivosComATaxa() throws Exception {
        mvc.perform(get("/api/publico/cardapio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locais[?(@.nome == 'Saco')].taxa").value(6.00))
                .andExpect(jsonPath("$.locais[0].id").doesNotExist());
    }

    @Test
    void naoGuardaNadaNoNavegadorEnaoCriaSessao() throws Exception {
        var resposta = mvc.perform(get("/api/publico/cardapio"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse();

        // Quem só olha o cardápio não ganha cookie de sessão.
        assertThat(resposta.getHeaders("Set-Cookie")).noneMatch(c -> c.contains("JSESSIONID"));
    }

    /** A porta pública é SÓ de leitura: nenhum jeito de alterar nada sem login. */
    @Test
    void semLoginNaoExisteJeitoDeAlterarNada() throws Exception {
        mvc.perform(post("/api/publico/cardapio").with(csrf())).andExpect(status().is4xxClientError());
        mvc.perform(put("/api/publico/cardapio").with(csrf())).andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/publico/cardapio").with(csrf())).andExpect(status().is4xxClientError());
    }

    /** Abrir UMA porta não pode abrir as outras: o resto do sistema continua trancado. */
    @Test
    void oRestoDoSistemaContinuaExigindoLogin() throws Exception {
        for (var rota : new String[]{"/api/produtos?negocio=PASTELARIA", "/api/localidades", "/api/vendas",
                "/api/caixas", "/api/usuarios", "/api/relatorios/geral?inicio=2026-10-01&fim=2026-10-30",
                "/api/publico/outra-coisa", "/api/publico/"}) {
            mvc.perform(get(rota))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }
    }
}
