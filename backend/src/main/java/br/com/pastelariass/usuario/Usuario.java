package br.com.pastelariass.usuario;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Espelho Java da tabela "usuario" (migração V1).
 *
 * O Hibernate confere, na inicialização, se esta classe bate com a tabela
 * (ddl-auto=validate). Se nome de coluna ou tipo divergirem, o sistema não sobe.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String username;

    @Column(nullable = false, length = 120)
    private String nome;

    // Guarda só o hash BCrypt. Por isso não há setter de senha em texto puro.
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    // STRING grava o nome do enum ("ADMIN"). ORDINAL gravaria 0 e 1, e uma
    // mudança na ordem dos valores corromperia os dados existentes.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Papel papel;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "ultimo_login_em")
    private Instant ultimoLoginEm;

    // Quando a senha foi trocada pela última vez. NULL = nunca trocou.
    @Column(name = "senha_alterada_em")
    private Instant senhaAlteradaEm;

    /** Exigido pelo JPA. Não é para uso no código: o Hibernate o usa ao ler do banco. */
    protected Usuario() {
    }

    public Usuario(String username, String nome, String senhaHash, Papel papel) {
        this.username = username;
        this.nome = nome;
        this.senhaHash = senhaHash;
        this.papel = papel;
    }

    public void registrarLogin(Instant quando) {
        this.ultimoLoginEm = quando;
    }

    /** Desativar não apaga: o registro continua, para o histórico de quem fez o quê. */
    public void alterarAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    /** Recebe o hash já pronto (quem gera é o UsuarioService) e a hora da troca. */
    public void trocarSenhaHash(String novoHash, Instant quando) {
        this.senhaHash = novoHash;
        this.senhaAlteradaEm = quando;
    }

    /** true se a senha foi trocada DEPOIS do momento informado (ex.: a hora de um login). */
    public boolean senhaFoiTrocadaApos(Instant momento) {
        return senhaAlteradaEm != null && senhaAlteradaEm.isAfter(momento);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getNome() { return nome; }
    public String getSenhaHash() { return senhaHash; }
    public Papel getPapel() { return papel; }
    public boolean isAtivo() { return ativo; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getUltimoLoginEm() { return ultimoLoginEm; }
}
