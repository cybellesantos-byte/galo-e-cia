package br.com.pastelariass.repasse;

import br.com.pastelariass.venda.FormaPagamento;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Espelho Java da tabela "repasse_entrega" (migração V11): o que o entregador
 * tem a receber por uma entrega.
 *
 * Um repasse por venda. Ele não se apaga: se a venda for cancelada antes do
 * pagamento, vira CANCELADO.
 */
@Entity
@Table(name = "repasse_entrega")
public class RepasseEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venda_id", nullable = false)
    private Long vendaId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_entrega", nullable = false)
    private LocalDate dataEntrega;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SituacaoRepasse situacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 10)
    private FormaPagamento formaPagamento;

    @Column(name = "pago_em")
    private Instant pagoEm;

    @Column(name = "pago_por", length = 60)
    private String pagoPor;

    @Column(name = "caixa_id")
    private Long caixaId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    /** Exigido pelo JPA. */
    protected RepasseEntrega() {
    }

    public RepasseEntrega(Long vendaId, BigDecimal valor, LocalDate dataEntrega, Instant criadoEm) {
        this.vendaId = vendaId;
        this.valor = valor;
        this.dataEntrega = dataEntrega;
        this.criadoEm = criadoEm;
        this.situacao = SituacaoRepasse.PENDENTE;
    }

    public void pagar(FormaPagamento formaPagamento, Long caixaId, Instant quando, String quem) {
        this.situacao = SituacaoRepasse.PAGO;
        this.formaPagamento = formaPagamento;
        this.caixaId = caixaId;
        this.pagoEm = quando;
        this.pagoPor = quem;
    }

    public void cancelar() {
        this.situacao = SituacaoRepasse.CANCELADO;
    }

    public boolean isPago() {
        return situacao == SituacaoRepasse.PAGO;
    }

    public boolean isCancelado() {
        return situacao == SituacaoRepasse.CANCELADO;
    }

    public Long getId() { return id; }
    public Long getVendaId() { return vendaId; }
    public BigDecimal getValor() { return valor; }
    public LocalDate getDataEntrega() { return dataEntrega; }
    public SituacaoRepasse getSituacao() { return situacao; }
    public FormaPagamento getFormaPagamento() { return formaPagamento; }
    public Instant getPagoEm() { return pagoEm; }
    public String getPagoPor() { return pagoPor; }
    public Long getCaixaId() { return caixaId; }
    public Instant getCriadoEm() { return criadoEm; }
}
