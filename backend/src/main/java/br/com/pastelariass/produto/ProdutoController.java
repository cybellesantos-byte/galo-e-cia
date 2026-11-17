package br.com.pastelariass.produto;

import br.com.pastelariass.comum.Negocio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

/**
 * Cadastro de produtos usado pela equipe ao registrar vendas. Qualquer pessoa
 * logada (ADMIN ou EQUIPE) pode usar: manter a lista de produtos é trabalho do
 * dia a dia. Nada disso é público.
 *
 *   GET  /api/produtos?negocio=PEIXARIA   → lista (o filtro é opcional)
 *   POST /api/produtos                    → cadastra
 *   PUT  /api/produtos/{id}               → altera nome, categoria, preço, custo
 *   PUT  /api/produtos/{id}/disponivel    → esgota ou volta à venda
 *   PUT  /api/produtos/{id}/no-cardapio   → mostra ou esconde do cardápio online
 *   POST /api/produtos/{id}/desativar     → tira da lista de venda
 *   POST /api/produtos/{id}/reativar      → devolve à lista de venda
 */
@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public List<ProdutoResponse> listar(@RequestParam(required = false) Negocio negocio) {
        return produtoService.listar(negocio).stream().map(ProdutoResponse::de).toList();
    }

    @PostMapping
    public ResponseEntity<ProdutoResponse> cadastrar(@Valid @RequestBody CadastroProdutoRequest dados) {
        var produto = produtoService.cadastrar(dados);
        return ResponseEntity.created(URI.create("/api/produtos/" + produto.getId()))
                .body(ProdutoResponse.de(produto));
    }

    @PutMapping("/{id}")
    public ProdutoResponse alterar(@PathVariable Long id, @Valid @RequestBody AlteracaoProdutoRequest dados) {
        return ProdutoResponse.de(produtoService.alterar(id, dados));
    }

    @PutMapping("/{id}/disponivel")
    public ProdutoResponse definirDisponivel(@PathVariable Long id,
                                             @Valid @RequestBody DisponibilidadeRequest dados) {
        return ProdutoResponse.de(produtoService.definirDisponivel(id, dados.disponivel()));
    }

    @PutMapping("/{id}/no-cardapio")
    public ProdutoResponse definirNoCardapio(@PathVariable Long id,
                                             @Valid @RequestBody NoCardapioRequest dados) {
        return ProdutoResponse.de(produtoService.definirNoCardapio(id, dados.noCardapio()));
    }

    @PostMapping("/{id}/desativar")
    public ProdutoResponse desativar(@PathVariable Long id) {
        return ProdutoResponse.de(produtoService.definirAtivo(id, false));
    }

    @PostMapping("/{id}/reativar")
    public ProdutoResponse reativar(@PathVariable Long id) {
        return ProdutoResponse.de(produtoService.definirAtivo(id, true));
    }

    /** O JSON: {"disponivel": false} */
    public record DisponibilidadeRequest(@NotNull Boolean disponivel) {
    }

    /** O JSON: {"noCardapio": false} */
    public record NoCardapioRequest(@NotNull Boolean noCardapio) {
    }

    /** Como um produto aparece para quem usa o sistema. */
    public record ProdutoResponse(Long id, Negocio negocio, String nome, String categoria,
                                  UnidadeVenda unidadeVenda, BigDecimal preco, BigDecimal custoEstimado,
                                  String descricao, boolean ativo, boolean disponivel, boolean noCardapio) {

        static ProdutoResponse de(Produto produto) {
            return new ProdutoResponse(produto.getId(), produto.getNegocio(), produto.getNome(),
                    produto.getCategoria(), produto.getUnidadeVenda(), produto.getPreco(),
                    produto.getCustoEstimado(), produto.getDescricao(), produto.isAtivo(), produto.isDisponivel(),
                    produto.isNoCardapio());
        }
    }
}
