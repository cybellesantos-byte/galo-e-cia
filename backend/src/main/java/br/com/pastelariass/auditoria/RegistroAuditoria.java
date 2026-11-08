package br.com.pastelariass.auditoria;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Uma linha do diário da casa: quem, o quê, quando e sobre o quê.
 *
 * Não existe setter nem método de alteração. Um registro, depois de escrito,
 * não muda: é isso que dá valor a uma auditoria.
 */
@Entity
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant quando;

    /** Quem fez. Para ações sem pessoa (como o início do sistema), vale "sistema". */
    @Column(nullable = false, length = 60)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AcaoAuditoria acao;

    /** Sobre o que foi feito, por exemplo o nome do usuário alterado. */
    @Column(length = 120)
    private String alvo;

    @Column(length = 500)
    private String detalhes;

    /** Exigido pelo JPA. */
    protected RegistroAuditoria() {
    }

    public RegistroAuditoria(Instant quando, String usuario, AcaoAuditoria acao, String alvo, String detalhes) {
        this.quando = quando;
        this.usuario = usuario;
        this.acao = acao;
        this.alvo = alvo;
        this.detalhes = detalhes;
    }

    public Long getId() { return id; }
    public Instant getQuando() { return quando; }
    public String getUsuario() { return usuario; }
    public AcaoAuditoria getAcao() { return acao; }
    public String getAlvo() { return alvo; }
    public String getDetalhes() { return detalhes; }
}
