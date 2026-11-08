package br.com.pastelariass.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Operações de banco de usuários. Não escrevemos SQL aqui: o Spring Data cria
 * a consulta a partir do NOME do método. "IgnoreCase" vira
 * "upper(username) = upper(?)" (conferido no SQL gerado pelo Hibernate).
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    /** Quantos usuários com esse papel estão ativos. Usado para não deixar o sistema sem admin. */
    long countByPapelAndAtivoTrue(Papel papel);
}
