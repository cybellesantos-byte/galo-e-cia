package br.com.pastelariass.localidade;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Espelho Java da tabela "localidade" (migração V4): um bairro ou praia para
 * onde a pastelaria entrega, com a taxa cobrada.
 */
@Entity
@Table(name = "localidade")
public class Localidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal taxa;

    @Column(nullable = false)
    private boolean ativo = true;

    /** Exigido pelo JPA. */
    protected Localidade() {
    }

    public Localidade(String nome, BigDecimal taxa) {
        this.nome = nome;
        this.taxa = taxa;
    }

    public void alterarDados(String nome, BigDecimal taxa, boolean ativo) {
        this.nome = nome;
        this.taxa = taxa;
        this.ativo = ativo;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public BigDecimal getTaxa() { return taxa; }
    public boolean isAtivo() { return ativo; }
}
