package br.com.pastelariass.produto;

import br.com.pastelariass.TestcontainersConfiguration;
import br.com.pastelariass.auditoria.AcaoAuditoria;
import br.com.pastelariass.comum.Negocio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Lista de produtos pela API, contra PostgreSQL real.
 * Os nomes ganham um sufixo aleatório: o banco de testes é compartilhado entre
 * os testes da mesma execução, e nomes fixos poderiam colidir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProdutoApiTest {

    @Autowired MockMvc mvc;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired br.com.pastelariass.auditoria.RegistroAuditoriaRepository registroAuditoria;

    private static String nomeUnico(String base) {
        return base + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Cadastra pela API e devolve o id do produto criado. */
    private Long cadastrar(String negocio, String unidade, String nome, String preco) throws Exception {
        mvc.perform(post("/api/produtos").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"negocio":"%s","nome":"%s","categoria":"Teste","unidadeVenda":"%s","preco":%s}"""
                                .formatted(negocio, nome, unidade, preco)))
                .andExpect(status().isCreated());
        return produtoRepository.findAll().stream()
                .filter(p -> p.getNome().equals(nome))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void equipeCadastraTilapiaPorKgComPrecoDe28() throws Exception {
        var nome = nomeUnico("Tilápia");

        mvc.perform(post("/api/produtos").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"negocio":"PEIXARIA","nome":"%s","categoria":"Peixe","unidadeVenda":"KG","preco":28.00}"""
                                .formatted(nome)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.negocio").value("PEIXARIA"))
                .andExpect(jsonPath("$.unidadeVenda").value("KG"))
                .andExpect(jsonPath("$.preco").value(28.00))
                .andExpect(jsonPath("$.disponivel").value(true))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    /** A descrição é opcional, aparece na resposta, e dá para trocar ou apagar. */
    @Test
    void descricaoEhOpcionalPodeSerAlteradaEApagada() throws Exception {
        var nome = nomeUnico("Pastel de frango");

        mvc.perform(post("/api/produtos").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"negocio":"PASTELARIA","nome":"%s","categoria":"Simples","unidadeVenda":"UNIDADE",
                                 "preco":8.00,"descricao":"  frango desfiado  "}""".formatted(nome)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descricao").value("frango desfiado"));   // espaços das pontas saem
        var id = produtoRepository.findAll().stream()
                .filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow().getId();

        mvc.perform(put("/api/produtos/" + id).with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","categoria":"Simples","preco":8.00,"descricao":"frango com Catupiry"}"""
                                .formatted(nome)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("frango com Catupiry"));

        mvc.perform(put("/api/produtos/" + id).with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","categoria":"Simples","preco":8.00,"descricao":"   "}""".formatted(nome)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").doesNotExist());   // só espaços = sem descrição
    }

    @Test
    void descricaoMuitoLongaEhRecusadaEmVezDeCortada() throws Exception {
        mvc.perform(post("/api/produtos").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"negocio":"PASTELARIA","nome":"%s","categoria":"Simples","unidadeVenda":"UNIDADE",
                                 "preco":8.00,"descricao":"%s"}""".formatted(nomeUnico("Pastel longo"), "a".repeat(201))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void peixariaNaoVendePorUnidadeNaAPI() throws Exception {
        mvc.perform(post("/api/produtos").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"negocio":"PEIXARIA","nome":"%s","categoria":"Peixe","unidadeVenda":"UNIDADE","preco":28.00}"""
                                .formatted(nomeUnico("Tilápia"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("A peixaria vende por kg."));
    }

    /**
     * Pronto quando: esgotar "Pastel de carne" sem apagá-lo. O preço de 8,00 é
     * só um valor de teste, não o preço real da pastelaria.
     */
    @Test
    void esgotarPastelDeCarneSemApagarEle() throws Exception {
        var nome = nomeUnico("Pastel de carne");
        var id = cadastrar("PASTELARIA", "UNIDADE", nome, "8.00");

        mvc.perform(put("/api/produtos/" + id + "/disponivel").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disponivel\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(false))
                .andExpect(jsonPath("$.ativo").value(true));

        // Continua na lista, só que indisponível.
        mvc.perform(get("/api/produtos?negocio=PASTELARIA").with(user("ana").roles("EQUIPE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(nome)));
        assertThat(produtoRepository.findById(id)).isPresent()
                .get().extracting(Produto::isDisponivel).isEqualTo(false);
    }

    @Test
    void desativarTiraDaListaSemApagar() throws Exception {
        var id = cadastrar("PASTELARIA", "UNIDADE", nomeUnico("Pastel de queijo"), "8.00");

        mvc.perform(post("/api/produtos/" + id + "/desativar").with(user("ana").roles("EQUIPE")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mvc.perform(post("/api/produtos/" + id + "/reativar").with(user("ana").roles("EQUIPE")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void nomeRepetidoNoMesmoNegocioEhRecusado() throws Exception {
        var nome = nomeUnico("Pastel de frango");
        cadastrar("PASTELARIA", "UNIDADE", nome, "8.00");

        mvc.perform(post("/api/produtos").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"negocio":"PASTELARIA","nome":"%s","categoria":"Teste","unidadeVenda":"UNIDADE","preco":9.00}"""
                                .formatted(nome.toUpperCase())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Já existe um produto com esse nome neste negócio."));
    }

    @Test
    void precoZeroEhRecusadoComoDadoInvalido() throws Exception {
        mvc.perform(post("/api/produtos").with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"negocio":"PEIXARIA","nome":"%s","categoria":"Peixe","unidadeVenda":"KG","preco":0}"""
                                .formatted(nomeUnico("Tilápia"))))
                .andExpect(status().isBadRequest());
    }

    /** Mudar o preço deixa registro na auditoria: preço é dinheiro. */
    @Test
    void alterarPrecoDeixaRegistroNaAuditoria() throws Exception {
        var nome = nomeUnico("Pastel auditado");
        var id = cadastrar("PASTELARIA", "UNIDADE", nome, "8.00");

        mvc.perform(put("/api/produtos/" + id).with(user("ana").roles("EQUIPE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","categoria":"Teste","preco":9.00}""".formatted(nome)))
                .andExpect(status().isOk());

        assertThat(registroAuditoria.findTop100ByOrderByQuandoDesc()).anyMatch(registro ->
                registro.getAcao() == AcaoAuditoria.PRODUTO_ALTERADO
                        && registro.getAlvo().equals(nome)
                        && registro.getDetalhes().contains("preco=8.00->9.00"));
    }

    /** Um valor que não existe no parâmetro vira 400 com mensagem, e não 500. */
    @Test
    void negocioInvalidoNaListagemDaResposta400() throws Exception {
        mvc.perform(get("/api/produtos?negocio=XPTO").with(user("ana").roles("EQUIPE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Valor inválido para negocio."));
    }

    @Test
    void semLoginNaoLista() throws Exception {
        mvc.perform(get("/api/produtos")).andExpect(status().isUnauthorized());
    }
}
