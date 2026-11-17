package br.com.pastelariass.usuario;

import br.com.pastelariass.comum.RecursoNaoEncontradoException;
import br.com.pastelariass.comum.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Testes UNITÁRIOS da Parte B: desativar, reativar, trocar senha e a
 * verificação de desativado. Sem banco; o repositório é um dublê.
 */
@ExtendWith(MockitoExtension.class)
class GestaoUsuarioServiceTest {

    private static final Long ID = 7L;
    private static final Instant MEIO_DIA = Instant.parse("2026-10-27T15:00:00Z");

    @Mock UsuarioRepository usuarioRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock br.com.pastelariass.auditoria.AuditoriaService auditoria;

    private UsuarioService service() {
        return new UsuarioService(usuarioRepository, passwordEncoder, auditoria,
                Clock.fixed(MEIO_DIA, ZoneOffset.UTC));
    }

    // ── Desativar ────────────────────────────────────────────────────────────

    @Test
    void desativaUmUsuarioComum() {
        var ana = new Usuario("ana", "Ana", "HASH", Papel.EQUIPE);
        when(usuarioRepository.findById(ID)).thenReturn(Optional.of(ana));

        var resultado = service().desativar(ID, "chefe");

        assertThat(resultado.isAtivo()).isFalse();
    }

    @Test
    void naoDesativaAPropriaConta() {
        var chefe = new Usuario("chefe", "Chefe", "HASH", Papel.ADMIN);
        when(usuarioRepository.findById(ID)).thenReturn(Optional.of(chefe));

        assertThatThrownBy(() -> service().desativar(ID, "CHEFE"))   // maiúsculas não enganam
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("própria conta");
        assertThat(chefe.isAtivo()).isTrue();
    }

    @Test
    void naoDesativaOUltimoAdministradorAtivo() {
        var outroAdmin = new Usuario("outro", "Outro", "HASH", Papel.ADMIN);
        when(usuarioRepository.findById(ID)).thenReturn(Optional.of(outroAdmin));
        when(usuarioRepository.countByPapelAndAtivoTrue(Papel.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service().desativar(ID, "chefe"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("último administrador");
        assertThat(outroAdmin.isAtivo()).isTrue();
    }

    @Test
    void desativaUmAdminQuandoHaOutroAdminAtivo() {
        var outroAdmin = new Usuario("outro", "Outro", "HASH", Papel.ADMIN);
        when(usuarioRepository.findById(ID)).thenReturn(Optional.of(outroAdmin));
        when(usuarioRepository.countByPapelAndAtivoTrue(Papel.ADMIN)).thenReturn(2L);

        assertThat(service().desativar(ID, "chefe").isAtivo()).isFalse();
    }

    @Test
    void desativarUsuarioInexistenteViraRecursoNaoEncontrado() {
        when(usuarioRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().desativar(ID, "chefe"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void reativaUmUsuarioDesativado() {
        var ana = new Usuario("ana", "Ana", "HASH", Papel.EQUIPE);
        ana.alterarAtivo(false);
        when(usuarioRepository.findById(ID)).thenReturn(Optional.of(ana));

        assertThat(service().reativar(ID).isAtivo()).isTrue();
    }

    // ── Verificação de desativado (usada pelo filtro a cada requisição) ──────

    private static final Instant LOGIN_ANTIGO = Instant.parse("2026-10-27T10:00:00Z");

    @Test
    void sessaoCaiQuandoOUsuarioFoiDesativado() {
        var ana = new Usuario("ana", "Ana", "HASH", Papel.EQUIPE);
        ana.alterarAtivo(false);
        when(usuarioRepository.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(ana));

        assertThat(service().sessaoDeveSerEncerrada("ana", LOGIN_ANTIGO)).isTrue();
    }

    @Test
    void sessaoSemHoraDoLoginAindaCaiSeOUsuarioFoiDesativado() {
        var ana = new Usuario("ana", "Ana", "HASH", Papel.EQUIPE);
        ana.alterarAtivo(false);
        when(usuarioRepository.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(ana));

        assertThat(service().sessaoDeveSerEncerrada("ana", null)).isTrue();
    }

    @Test
    void sessaoCaiQuandoASenhaFoiTrocadaDepoisDoLogin() {
        var ana = new Usuario("ana", "Ana", "HASH", Papel.EQUIPE);
        ana.trocarSenhaHash("NOVO", Instant.parse("2026-10-27T11:00:00Z")); // depois do login
        when(usuarioRepository.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(ana));

        assertThat(service().sessaoDeveSerEncerrada("ana", LOGIN_ANTIGO)).isTrue();
    }

    @Test
    void sessaoContinuaQuandoASenhaFoiTrocadaAntesDoLogin() {
        var ana = new Usuario("ana", "Ana", "HASH", Papel.EQUIPE);
        ana.trocarSenhaHash("NOVO", Instant.parse("2026-10-27T09:00:00Z")); // antes do login
        when(usuarioRepository.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(ana));

        assertThat(service().sessaoDeveSerEncerrada("ana", LOGIN_ANTIGO)).isFalse();
    }

    @Test
    void sessaoContinuaParaUsuarioAtivoComSenhaNuncaTrocada() {
        when(usuarioRepository.findByUsernameIgnoreCase("ativa")).thenReturn(Optional.of(
                new Usuario("ativa", "Ativa", "HASH", Papel.EQUIPE)));

        assertThat(service().sessaoDeveSerEncerrada("ativa", LOGIN_ANTIGO)).isFalse();
        assertThat(service().sessaoDeveSerEncerrada("nao-existe", LOGIN_ANTIGO)).isFalse();
    }

    @Test
    void trocaDeSenhaDevolveAHoraDaTrocaDoRelogio() {
        var ana = new Usuario("ana", "Ana", "HASH-ANTIGO", Papel.EQUIPE);
        when(usuarioRepository.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-antiga-1", "HASH-ANTIGO")).thenReturn(true);
        when(passwordEncoder.encode("senha-nova-12345")).thenReturn("HASH-NOVO");

        var quando = service().trocarSenha("ana", "senha-antiga-1", "senha-nova-12345");

        assertThat(quando).isEqualTo(MEIO_DIA);
        assertThat(ana.senhaFoiTrocadaApos(MEIO_DIA.minusSeconds(1))).isTrue();
    }

    // ── Troca de senha ───────────────────────────────────────────────────────

    @Test
    void trocaSenhaQuandoASenhaAtualConfere() {
        var ana = new Usuario("ana", "Ana", "HASH-ANTIGO", Papel.EQUIPE);
        when(usuarioRepository.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-antiga-1", "HASH-ANTIGO")).thenReturn(true);
        when(passwordEncoder.encode("senha-nova-12345")).thenReturn("HASH-NOVO");

        service().trocarSenha("ana", "senha-antiga-1", "senha-nova-12345");

        assertThat(ana.getSenhaHash()).isEqualTo("HASH-NOVO");
    }

    @Test
    void recusaTrocaQuandoASenhaAtualNaoConfere() {
        var ana = new Usuario("ana", "Ana", "HASH-ANTIGO", Papel.EQUIPE);
        when(usuarioRepository.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("errada", "HASH-ANTIGO")).thenReturn(false);

        assertThatThrownBy(() -> service().trocarSenha("ana", "errada", "senha-nova-12345"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("senha atual");
        assertThat(ana.getSenhaHash()).isEqualTo("HASH-ANTIGO");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void recusaSenhaNovaCurta() {
        var ana = new Usuario("ana", "Ana", "HASH-ANTIGO", Papel.EQUIPE);
        when(usuarioRepository.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-antiga-1", "HASH-ANTIGO")).thenReturn(true);

        assertThatThrownBy(() -> service().trocarSenha("ana", "senha-antiga-1", "curta"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("senha deve ter");
        assertThat(ana.getSenhaHash()).isEqualTo("HASH-ANTIGO");
    }
}
