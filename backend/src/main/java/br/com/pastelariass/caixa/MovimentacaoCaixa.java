package br.com.pastelariass.caixa;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Espelho Java da tabela "movimentacao_caixa": uma sangria ou um reforço.
 *
 * Só recebe inserções. Se a conta estiver errada, a correção é outra movimentação,
 * nunca uma edição desta.
 */
@Entity
@Table(name = "movimentacao_caixa")
public class MovimentacaoCaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Guardamos só o número do caixa, sem o relacionamento JPA: simples e suficiente aqui. */
    @Column(name = "caixa_id", nullable = false)
    private Long caixaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimentacao tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private Instant quando;

    @Column(nullable = false, length = 60)
    private String usuario;

    @Column(length = 500)
    private String observacao;

    /** Exigido pelo JPA. */
    protected MovimentacaoCaixa() {
    }

    public MovimentacaoCaixa(Long caixaId, TipoMovimentacao tipo, BigDecimal valor,
                             Instant quando, String usuario, String observacao) {
        this.caixaId = caixaId;
        this.tipo = tipo;
        this.valor = valor;
        this.quando = quando;
        this.usuario = usuario;
        this.observacao = observacao;
    }

    public Long getId() { return id; }
    public Long getCaixaId() { return caixaId; }
    public TipoMovimentacao getTipo() { return tipo; }
    public BigDecimal getValor() { return valor; }
    public Instant getQuando() { return quando; }
    public String getUsuario() { return usuario; }
    public String getObservacao() { return observacao; }
}
