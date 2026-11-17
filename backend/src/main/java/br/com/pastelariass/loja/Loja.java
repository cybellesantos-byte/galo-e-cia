package br.com.pastelariass.loja;

import br.com.pastelariass.comum.Negocio;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Espelho Java da tabela "loja" (migração V13): o que o cardápio público precisa
 * saber de cada negócio. Existe uma linha para a pastelaria e uma para a peixaria,
 * criadas pela própria migração; o sistema só altera, nunca cria nem apaga.
 *
 * A chave é o próprio negócio, então não há "duas lojas da pastelaria".
 */
@Entity
@Table(name = "loja")
public class Loja {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Negocio negocio;

    /** A equipe liga e desliga. Começa fechada. */
    @Column(nullable = false)
    private boolean aberta;

    /** Só dígitos, com o código do país: 5583999998888. Null enquanto não foi informado. */
    @Column(length = 15)
    private String whatsapp;

    @Column(name = "alterado_em")
    private Instant alteradoEm;

    @Column(name = "alterado_por", length = 60)
    private String alteradoPor;

    /** Exigido pelo JPA. */
    protected Loja() {
    }

    public void definirAberta(boolean aberta, Instant quando, String usuario) {
        this.aberta = aberta;
        registrarAlteracao(quando, usuario);
    }

    public void definirWhatsapp(String whatsapp, Instant quando, String usuario) {
        this.whatsapp = whatsapp;
        registrarAlteracao(quando, usuario);
    }

    private void registrarAlteracao(Instant quando, String usuario) {
        this.alteradoEm = quando;
        this.alteradoPor = usuario;
    }

    public Negocio getNegocio() { return negocio; }
    public boolean isAberta() { return aberta; }
    public String getWhatsapp() { return whatsapp; }
    public Instant getAlteradoEm() { return alteradoEm; }
    public String getAlteradoPor() { return alteradoPor; }
}
