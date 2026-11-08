package br.com.pastelariass.venda;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Espelho Java da tabela "venda" (migração V6).
 *
 * O total é calculado aqui, a partir do subtotal e da taxa: quem chama não
 * informa o total. Venda cancelada continua no banco, marcada, nunca apagada.
 */
@Entity
@Table(name = "venda")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chave_da_venda", nullable = false)
    private UUID chaveDaVenda;

    @Column(name = "caixa_id", nullable = false)
    private Long caixaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Origem origem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoVenda tipo;

    @Column(name = "localidade_id")
    private Long localidadeId;

    /** Taxa da localidade no dia da venda. Nula em retirada. A cobrada pode ser outra. */
    @Column(name = "taxa_padrao", precision = 10, scale = 2)
    private BigDecimal taxaPadrao;

    @Column(name = "taxa_entrega", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxaEntrega;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 10)
    private FormaPagamento formaPagamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao_pagamento", nullable = false, length = 10)
    private SituacaoPagamento situacaoPagamento;

    @Column(name = "valor_recebido", precision = 10, scale = 2)
    private BigDecimal valorRecebido;

    @Column(precision = 10, scale = 2)
    private BigDecimal troco;

    @Column(length = 500)
    private String observacao;

    @Column(nullable = false)
    private boolean cancelada = false;

    @Column(name = "motivo_cancelamento", length = 200)
    private String motivoCancelamento;

    @Column(name = "cancelado_por", length = 60)
    private String canceladoPor;

    @Column(name = "cancelado_em")
    private Instant canceladoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "criado_por", nullable = false, length = 60)
    private String criadoPor;

    /** Exigido pelo JPA. */
    protected Venda() {
    }

    /**
     * Dinheiro já nasce paga; Pix e cartão nascem pendentes. O total é a soma do subtotal
     * com a taxa de entrega (zero para retirada).
     */
    public Venda(UUID chaveDaVenda, Long caixaId, Origem origem, TipoVenda tipo, Long localidadeId,
                 BigDecimal taxaPadrao, BigDecimal taxaEntrega, BigDecimal subtotal, FormaPagamento formaPagamento,
                 BigDecimal valorRecebido, BigDecimal troco, String observacao,
                 Instant criadoEm, String criadoPor) {
        this.chaveDaVenda = chaveDaVenda;
        this.caixaId = caixaId;
        this.origem = origem;
        this.tipo = tipo;
        this.localidadeId = localidadeId;
        this.taxaPadrao = taxaPadrao;
        this.taxaEntrega = taxaEntrega;
        this.subtotal = subtotal;
        this.total = subtotal.add(taxaEntrega);
        this.formaPagamento = formaPagamento;
        this.situacaoPagamento = formaPagamento == FormaPagamento.DINHEIRO
                ? SituacaoPagamento.PAGO
                : SituacaoPagamento.PENDENTE;
        this.valorRecebido = valorRecebido;
        this.troco = troco;
        this.observacao = observacao;
        this.criadoEm = criadoEm;
        this.criadoPor = criadoPor;
    }

    public void confirmarPagamento() {
        this.situacaoPagamento = SituacaoPagamento.PAGO;
    }

    public void cancelar(String motivo, String quem, Instant quando) {
        this.cancelada = true;
        this.motivoCancelamento = motivo;
        this.canceladoPor = quem;
        this.canceladoEm = quando;
    }

    public Long getId() { return id; }
    public UUID getChaveDaVenda() { return chaveDaVenda; }
    public Long getCaixaId() { return caixaId; }
    public Origem getOrigem() { return origem; }
    public TipoVenda getTipo() { return tipo; }
    public Long getLocalidadeId() { return localidadeId; }
    public BigDecimal getTaxaPadrao() { return taxaPadrao; }
    public BigDecimal getTaxaEntrega() { return taxaEntrega; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTotal() { return total; }
    public FormaPagamento getFormaPagamento() { return formaPagamento; }
    public SituacaoPagamento getSituacaoPagamento() { return situacaoPagamento; }
    public BigDecimal getValorRecebido() { return valorRecebido; }
    public BigDecimal getTroco() { return troco; }
    public String getObservacao() { return observacao; }
    public boolean isCancelada() { return cancelada; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public Instant getCriadoEm() { return criadoEm; }
    public String getCriadoPor() { return criadoPor; }
}
