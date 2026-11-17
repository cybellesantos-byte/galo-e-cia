package br.com.pastelariass.caixa;

import br.com.pastelariass.comum.Negocio;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Espelho Java da tabela "caixa" (migração V5): a gaveta de um negócio em um período.
 *
 * Aberto: fechadoEm é null. Fechado: o fechamento grava o esperado, o valor
 * contado e a diferença, e nada disso muda depois.
 */
@Entity
@Table(name = "caixa")
public class Caixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Negocio negocio;

    @Column(name = "saldo_inicial", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoInicial;

    @Column(name = "aberto_em", nullable = false)
    private Instant abertoEm;

    @Column(name = "aberto_por", nullable = false, length = 60)
    private String abertoPor;

    @Column(name = "fechado_em")
    private Instant fechadoEm;

    @Column(name = "fechado_por", length = 60)
    private String fechadoPor;

    @Column(name = "valor_contado", precision = 10, scale = 2)
    private BigDecimal valorContado;

    /** Quanto deveria haver na gaveta, calculado e gravado no fechamento. */
    @Column(precision = 10, scale = 2)
    private BigDecimal esperado;

    /** Contado menos esperado: negativo significa falta; positivo, sobra. */
    @Column(precision = 10, scale = 2)
    private BigDecimal diferenca;

    @Column(name = "observacao_fechamento", length = 500)
    private String observacaoFechamento;

    /** Exigido pelo JPA. */
    protected Caixa() {
    }

    public Caixa(Negocio negocio, BigDecimal saldoInicial, Instant abertoEm, String abertoPor) {
        this.negocio = negocio;
        this.saldoInicial = saldoInicial;
        this.abertoEm = abertoEm;
        this.abertoPor = abertoPor;
    }

    public void fechar(String fechadoPor, Instant fechadoEm, BigDecimal esperado,
                       BigDecimal valorContado, String observacao) {
        this.fechadoPor = fechadoPor;
        this.fechadoEm = fechadoEm;
        this.esperado = esperado;
        this.valorContado = valorContado;
        this.diferenca = valorContado.subtract(esperado);
        this.observacaoFechamento = observacao;
    }

    public boolean estaAberto() {
        return fechadoEm == null;
    }

    public Long getId() { return id; }
    public Negocio getNegocio() { return negocio; }
    public BigDecimal getSaldoInicial() { return saldoInicial; }
    public Instant getAbertoEm() { return abertoEm; }
    public String getAbertoPor() { return abertoPor; }
    public Instant getFechadoEm() { return fechadoEm; }
    public String getFechadoPor() { return fechadoPor; }
    public BigDecimal getValorContado() { return valorContado; }
    public BigDecimal getEsperado() { return esperado; }
    public BigDecimal getDiferenca() { return diferenca; }
    public String getObservacaoFechamento() { return observacaoFechamento; }
}
