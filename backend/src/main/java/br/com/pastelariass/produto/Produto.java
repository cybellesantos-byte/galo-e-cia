package br.com.pastelariass.produto;

import br.com.pastelariass.comum.Negocio;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Espelho Java da tabela "produto" (migração V4).
 *
 * Não há método de apagar: produto sai da lista por "desativar" e some das
 * vendas por "indisponível". A venda antiga continua apontando para ele.
 */
@Entity
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Negocio negocio;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 60)
    private String categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_venda", nullable = false, length = 10)
    private UnidadeVenda unidadeVenda;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(name = "custo_estimado", precision = 10, scale = 2)
    private BigDecimal custoEstimado;

    /** Frase curta para o cardápio do cliente (migração V13). Opcional. */
    @Column(length = 200)
    private String descricao;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private boolean disponivel = true;

    /** Exigido pelo JPA. */
    protected Produto() {
    }

    public Produto(Negocio negocio, String nome, String categoria, UnidadeVenda unidadeVenda,
                   BigDecimal preco, BigDecimal custoEstimado) {
        this.negocio = negocio;
        this.nome = nome;
        this.categoria = categoria;
        this.unidadeVenda = unidadeVenda;
        this.preco = preco;
        this.custoEstimado = custoEstimado;
    }

    /** O negócio e a unidade não mudam depois do cadastro: mudariam o sentido das vendas antigas. */
    public void alterarDados(String nome, String categoria, BigDecimal preco, BigDecimal custoEstimado) {
        this.nome = nome;
        this.categoria = categoria;
        this.preco = preco;
        this.custoEstimado = custoEstimado;
    }

    public void alterarDescricao(String descricao) {
        this.descricao = descricao;
    }

    public void alterarDisponivel(boolean disponivel) {
        this.disponivel = disponivel;
    }

    public void alterarAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Long getId() { return id; }
    public Negocio getNegocio() { return negocio; }
    public String getNome() { return nome; }
    public String getCategoria() { return categoria; }
    public UnidadeVenda getUnidadeVenda() { return unidadeVenda; }
    public BigDecimal getPreco() { return preco; }
    public BigDecimal getCustoEstimado() { return custoEstimado; }
    public String getDescricao() { return descricao; }
    public boolean isAtivo() { return ativo; }
    public boolean isDisponivel() { return disponivel; }
}
