package br.com.pastelariass.usuario;

import br.com.pastelariass.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa a entidade e as regras que moram NO BANCO (CHECK, índice único).
 *
 * replace = NONE: usa o PostgreSQL do Testcontainers, e não um H2 de mentira.
 * É aqui que um H2 esconderia erros reais, como o índice LOWER(username).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UsuarioRepositoryTest {

    @Autowired
    UsuarioRepository repositorio;

    @Autowired
    JdbcClient jdbc;

    @Test
    void salvaUsuarioComValoresPadrao() {
        var salvo = repositorio.saveAndFlush(novo("admin", Papel.ADMIN));

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.isAtivo()).isTrue();
        assertThat(salvo.getCriadoEm()).isNotNull();
        assertThat(salvo.getUltimoLoginEm()).isNull();
    }

    @Test
    void buscaPeloUsernameIgnorandoMaiusculas() {
        repositorio.saveAndFlush(novo("admin", Papel.ADMIN));

        assertThat(repositorio.findByUsernameIgnoreCase("ADMIN")).isPresent();
        assertThat(repositorio.findByUsernameIgnoreCase("Admin")).isPresent();
        assertThat(repositorio.findByUsernameIgnoreCase("outro")).isEmpty();
    }

    @Test
    void informaSeUsernameJaExisteIgnorandoMaiusculas() {
        repositorio.saveAndFlush(novo("admin", Papel.ADMIN));

        assertThat(repositorio.existsByUsernameIgnoreCase("admin")).isTrue();
        assertThat(repositorio.existsByUsernameIgnoreCase("ADMIN")).isTrue();
        assertThat(repositorio.existsByUsernameIgnoreCase("outro")).isFalse();
    }

    @Test
    void registraUltimoLogin() {
        var salvo = repositorio.saveAndFlush(novo("ana", Papel.EQUIPE));
        var quando = Instant.parse("2026-10-23T12:00:00Z");

        salvo.registrarLogin(quando);
        repositorio.saveAndFlush(salvo);

        var relido = repositorio.findById(salvo.getId()).orElseThrow();
        assertThat(relido.getUltimoLoginEm()).isEqualTo(quando);
    }

    @Test
    void bancoNaoAceitaDoisUsuariosComMesmoNomeDiferindoSoNasMaiusculas() {
        repositorio.saveAndFlush(novo("admin", Papel.ADMIN));

        // Não é o Java que barra: é o índice único LOWER(username) no PostgreSQL.
        assertThatThrownBy(() -> repositorio.saveAndFlush(novo("ADMIN", Papel.EQUIPE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoRecusaPapelQueNaoExiste() {
        // SQL direto de propósito: testa o CHECK do banco, ignorando o enum Java.
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO usuario (username, nome, senha_hash, papel)
                        VALUES ('gerente', 'Gerente', 'hash', 'GERENTE')
                        """).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoRecusaUsuarioSemSenhaHash() {
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO usuario (username, nome, papel)
                        VALUES ('sem-senha', 'Sem Senha', 'EQUIPE')
                        """).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Usuario novo(String username, Papel papel) {
        return new Usuario(username, "Nome de " + username, "$2a$10$hashfalso", papel);
    }
}
