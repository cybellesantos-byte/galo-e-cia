package br.com.pastelariass.usuario;

import br.com.pastelariass.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de INTEGRAÇÃO: sobe o sistema inteiro contra um PostgreSQL real e
 * confere o resultado do runner no banco, com o BCrypt de verdade.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminInicialIntegrationTest {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void administradorFoiCriadoNaInicializacaoComSenhaEmBCrypt() {
        var admin = usuarioRepository.findByUsernameIgnoreCase("admin").orElseThrow();

        assertThat(admin.getPapel()).isEqualTo(Papel.ADMIN);
        assertThat(admin.isAtivo()).isTrue();
        // O banco guarda o hash, nunca a senha pura...
        assertThat(admin.getSenhaHash()).isNotEqualTo("senha-de-teste-123").startsWith("$2");
        // ...e o hash confere com a senha certa e recusa a errada.
        assertThat(passwordEncoder.matches("senha-de-teste-123", admin.getSenhaHash())).isTrue();
        assertThat(passwordEncoder.matches("senha-errada", admin.getSenhaHash())).isFalse();
    }

    @Test
    void existeApenasUmAdministrador() {
        // Conta só ADMINs: outros testes que dividem este banco criam usuários da EQUIPE.
        var admins = usuarioRepository.findAll().stream()
                .filter(u -> u.getPapel() == Papel.ADMIN)
                .count();
        assertThat(admins).isEqualTo(1);
    }
}
