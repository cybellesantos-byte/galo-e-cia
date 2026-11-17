package br.com.pastelariass.venda;

import br.com.pastelariass.comum.Validacoes;
import br.com.pastelariass.produto.Produto;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Espelho Java da tabela "venda_item". Nome e preço são copiados do produto no
 * momento da venda: mudar o produto depois não altera esta linha.
 *
 * A quantidade é decimal: 4 pastéis, ou 1,350 kg de tilápia.
 */
@Entity
@Table(name = "venda_item")
public class VendaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venda_id", nullable = false)
    private Long vendaId;

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(name = "nome_produto", nullable = false, length = 120)
    private String nomeProduto;

    @Column(name = "preco_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantidade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    /**
     * O custo estimado do produto NO MOMENTO da venda (migração V12), como o nome e
     * o preço. Mudar o custo no cadastro depois não muda a margem desta venda.
     * Nulo quando o produto não tinha custo cadastrado, ou em vendas anteriores à V12.
     */
    @Column(name = "custo_unitario_estimado", precision = 10, scale = 2)
    private BigDecimal custoUnitarioEstimado;

    /** Exigido pelo JPA. */
    protected VendaItem() {
    }

    public VendaItem(Long vendaId, Produto produto, BigDecimal quantidade) {
        this.vendaId = vendaId;
        this.produtoId = produto.getId();
        this.nomeProduto = produto.getNome();
        this.precoUnitario = produto.getPreco();
        this.custoUnitarioEstimado = produto.getCustoEstimado();
        this.quantidade = quantidade;
        this.total = calcularTotal(produto.getPreco(), quantidade);
    }

    public BigDecimal getCustoUnitarioEstimado() { return custoUnitarioEstimado; }

    /** Preço × quantidade, arredondado para centavos. Usado também na conferência do pedido. */
    public static BigDecimal calcularTotal(BigDecimal preco, BigDecimal quantidade) {
        return Validacoes.arredondarCentavos(preco.multiply(quantidade));
    }

    public Long getId() { return id; }
    public Long getVendaId() { return vendaId; }
    public Long getProdutoId() { return produtoId; }
    public String getNomeProduto() { return nomeProduto; }
    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public BigDecimal getQuantidade() { return quantidade; }
    public BigDecimal getTotal() { return total; }
}
