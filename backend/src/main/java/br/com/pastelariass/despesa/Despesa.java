package br.com.pastelariass.despesa;

import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.venda.FormaPagamento;
import br.com.pastelariass.venda.SituacaoPagamento;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Espelho Java da tabela "despesa" (migração V9).
 *
 * Paga = tem forma de pagamento. Pendente = ainda não tem.
 * Despesa não se apaga: se estiver errada, a correção vai numa nova etapa.
 */
@Entity
@Table(name = "despesa")
public class Despesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Negocio negocio;

    @Column(nullable = false, length = 120)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategoriaDespesa categoria;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_despesa", nullable = false)
    private LocalDate dataDespesa;

    @Column(nullable = false, length = 60)
    private String responsavel;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 10)
    private FormaPagamento formaPagamento;

    @Column(name = "pago_em")
    private Instant pagoEm;

    @Column(name = "caixa_id")
    private Long caixaId;

    @Column(name = "peso_kg", precision = 10, scale = 3)
    private BigDecimal pesoKg;

    @Column(length = 500)
    private String observacao;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "criado_por", nullable = false, length = 60)
    private String criadoPor;

    /** Exigido pelo JPA. */
    protected Despesa() {
    }

    /** Com forma de pagamento, nasce paga. Sem, nasce pendente. */
    public Despesa(Negocio negocio, String descricao, CategoriaDespesa categoria, BigDecimal valor,
                   LocalDate dataDespesa, String responsavel, FormaPagamento formaPagamento, Long caixaId,
                   BigDecimal pesoKg, String observacao, Instant criadoEm, String criadoPor) {
        this.negocio = negocio;
        this.descricao = descricao;
        this.categoria = categoria;
        this.valor = valor;
        this.dataDespesa = dataDespesa;
        this.responsavel = responsavel;
        this.formaPagamento = formaPagamento;
        this.caixaId = caixaId;
        this.pesoKg = pesoKg;
        this.observacao = observacao;
        this.criadoEm = criadoEm;
        this.criadoPor = criadoPor;
        this.pagoEm = formaPagamento == null ? null : criadoEm;
    }

    /** Paga uma despesa que estava pendente. */
    public void pagar(FormaPagamento formaPagamento, Long caixaId, Instant quando) {
        this.formaPagamento = formaPagamento;
        this.caixaId = caixaId;
        this.pagoEm = quando;
    }

    public boolean estaPaga() {
        return formaPagamento != null;
    }

    /** Derivada, não guardada: paga quando tem forma de pagamento. */
    public SituacaoPagamento getSituacao() {
        return estaPaga() ? SituacaoPagamento.PAGO : SituacaoPagamento.PENDENTE;
    }

    public Long getId() { return id; }
    public Negocio getNegocio() { return negocio; }
    public String getDescricao() { return descricao; }
    public CategoriaDespesa getCategoria() { return categoria; }
    public BigDecimal getValor() { return valor; }
    public LocalDate getDataDespesa() { return dataDespesa; }
    public String getResponsavel() { return responsavel; }
    public FormaPagamento getFormaPagamento() { return formaPagamento; }
    public Instant getPagoEm() { return pagoEm; }
    public Long getCaixaId() { return caixaId; }
    public BigDecimal getPesoKg() { return pesoKg; }
    public String getObservacao() { return observacao; }
    public Instant getCriadoEm() { return criadoEm; }
    public String getCriadoPor() { return criadoPor; }
}
