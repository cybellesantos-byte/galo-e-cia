package br.com.pastelariass.despesa;

import br.com.pastelariass.comum.Validacoes;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Espelho Java da tabela "despesa_item": uma linha de uma compra de ingredientes.
 * Registra o que foi comprado e por quanto. Não controla estoque.
 */
@Entity
@Table(name = "despesa_item")
public class DespesaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "despesa_id", nullable = false)
    private Long despesaId;

    @Column(nullable = false, length = 120)
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UnidadeMedida unidade;

    @Column(name = "preco_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    /** Exigido pelo JPA. */
    protected DespesaItem() {
    }

    public DespesaItem(Long despesaId, String descricao, BigDecimal quantidade, UnidadeMedida unidade,
                       BigDecimal precoUnitario) {
        this.despesaId = despesaId;
        this.descricao = descricao;
        this.quantidade = quantidade;
        this.unidade = unidade;
        this.precoUnitario = precoUnitario;
        this.total = calcularTotal(quantidade, precoUnitario);
    }

    /** Quantidade × preço, arredondado para centavos. */
    public static BigDecimal calcularTotal(BigDecimal quantidade, BigDecimal precoUnitario) {
        return Validacoes.arredondarCentavos(quantidade.multiply(precoUnitario));
    }

    public Long getId() { return id; }
    public Long getDespesaId() { return despesaId; }
    public String getDescricao() { return descricao; }
    public BigDecimal getQuantidade() { return quantidade; }
    public UnidadeMedida getUnidade() { return unidade; }
    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public BigDecimal getTotal() { return total; }
}
