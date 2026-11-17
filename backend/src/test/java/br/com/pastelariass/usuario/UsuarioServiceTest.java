package br.com.pastelariass.usuario;

import br.com.pastelariass.comum.RecursoNaoEncontradoException;
import br.com.pastelariass.comum.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes UNITÁRIOS do UsuarioService: sem Spring e sem banco.
 * O repositório e o encoder são dublês (mocks); o relógio está parado.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final Instant MEIO_DIA = Instant.parse("2026-10-23T15:00:00Z"); // 12:00 em Brasília
    private static final String SENHA_OK = "senha-boa-123"; // 13 caracteres

    @Mock UsuarioRepository usuarioRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock br.com.pastelariass.auditoria.AuditoriaService auditoria;

    private UsuarioService service() {
        return new UsuarioService(usuarioRepository, passwordEncoder, auditoria, Clock.fixed(MEIO_DIA, ZoneOffset.UTC));
    }

    /** Atalho para os testes ficarem curtos: monta o request e chama o service. */
    private Usuario cadastrar(String username, String nome, String senha, Papel papel) {
        return service().cadastrar(new CadastroUsuarioRequest(username, nome, senha, papel));
    }

    // ── Cadastro: caminho feliz ──────────────────────────────────────────────

    @Test
    void cadastraComSenhaEmHashENomesSemEspacosNasPontas() {
        when(passwordEncoder.encode(SENHA_OK)).thenReturn("HASH");
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var usuario = cadastrar("  ana.souza ", "  Ana Souza  ", SENHA_OK, Papel.EQUIPE);

        assertThat(usuario.getUsername()).isEqualTo("ana.souza");
        assertThat(usuario.getNome()).isEqualTo("Ana Souza");
        assertThat(usuario.getSenhaHash()).isEqualTo("HASH");
        assertThat(usuario.getPapel()).isEqualTo(Papel.EQUIPE);
    }

    // ── Cadastro: regras que recusam ─────────────────────────────────────────

    @ParameterizedTest(name = "recusa username \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "ana souza", "joão", "ana@casa"})
    void recusaUsernameForaDoFormato(String username) {
        assertThatThrownBy(() -> cadastrar(username, "Ana", SENHA_OK, Papel.EQUIPE))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("nome de usuário");
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void recusaUsernameComMaisDe60Caracteres() {
        // Fora do @ValueSource: anotações só aceitam constantes, e repeat() é calculado ao rodar.
        var longo = "x".repeat(61);

        assertThatThrownBy(() -> cadastrar(longo, "Ana", SENHA_OK, Papel.EQUIPE))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void aceitaUsernameNoTamanhoMinimoEComPontoHifenSublinhado() {
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        cadastrar("a.b", "Ana", SENHA_OK, Papel.EQUIPE);
        cadastrar("ana_souza-2", "Ana", SENHA_OK, Papel.EQUIPE);

        verify(usuarioRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void recusaNomeEmBranco() {
        assertThatThrownBy(() -> cadastrar("ana", "   ", SENHA_OK, Papel.EQUIPE))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("nome");
    }

    @Test
    void recusaSenhaUmCaractereAbaixoDoMinimo() {
        var curta = "x".repeat(UsuarioService.TAMANHO_MINIMO_SENHA - 1);

        assertThatThrownBy(() -> cadastrar("ana", "Ana", curta, Papel.EQUIPE))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("senha");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void aceitaSenhaExatamenteNoMinimo() {
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));
        var exata = "x".repeat(UsuarioService.TAMANHO_MINIMO_SENHA);

        cadastrar("ana", "Ana", exata, Papel.EQUIPE);

        verify(usuarioRepository).saveAndFlush(any());
    }

    @Test
    void recusaPapelVazio() {
        assertThatThrownBy(() -> cadastrar("ana", "Ana", SENHA_OK, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("papel");
    }

    @Test
    void recusaUsernameQueJaExiste() {
        when(usuarioRepository.existsByUsernameIgnoreCase("ana")).thenReturn(true);

        assertThatThrownBy(() -> cadastrar("ana", "Ana", SENHA_OK, Papel.EQUIPE))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Já existe");
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    /** Dois cadastros simultâneos: o "exists" deixou passar, mas o índice do banco barrou. */
    @Test
    void cadastroSimultaneoBarradoPeloBancoViraMensagemClara() {
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenThrow(new DataIntegrityViolationException("uk_usuario_username"));

        assertThatThrownBy(() -> cadastrar("ana", "Ana", SENHA_OK, Papel.EQUIPE))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Já existe");
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @Test
    void registrarLoginGravaOHorarioDoRelogio() {
        var usuario = new Usuario("ana", "Ana", "HASH", Papel.EQUIPE);
        when(usuarioRepository.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(usuario));

        var resultado = service().registrarLogin("ana");

        assertThat(resultado.getUltimoLoginEm()).isEqualTo(MEIO_DIA);
    }

    @Test
    void usuarioInexistenteViraRecursoNaoEncontrado() {
        when(usuarioRepository.findByUsernameIgnoreCase("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().buscarPorUsername("fantasma"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
