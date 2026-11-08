package br.com.pastelariass.usuario;

import br.com.pastelariass.comum.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Teste UNITÁRIO do runner. As regras do cadastro (senha mínima etc.) são
 * testadas no UsuarioServiceTest; aqui conferimos só o que é do runner:
 * quando cadastrar, quando não, e como reage a uma regra violada.
 */
@ExtendWith(MockitoExtension.class)
class AdminInicialRunnerTest {

    @Mock
    UsuarioService usuarioService;

    private AdminInicialRunner runner(String senha) {
        return new AdminInicialRunner(usuarioService, "admin", senha);
    }

    @Test
    void cadastraOAdministradorPeloServicoQuandoNaoExiste() {
        when(usuarioService.existe("admin")).thenReturn(false);

        runner("senha-forte-123").run(null);

        // Records comparam pelo conteúdo: um request "igual" ao esperado satisfaz o verify.
        verify(usuarioService).cadastrar(
                new CadastroUsuarioRequest("admin", "Administrador", "senha-forte-123", Papel.ADMIN));
    }

    @Test
    void naoMexeNoAdministradorQueJaExiste() {
        when(usuarioService.existe("admin")).thenReturn(true);

        runner("outra-senha-qualquer").run(null);

        verify(usuarioService, never()).cadastrar(any());
    }

    @Test
    void regraVioladaImpedeOSistemaDeSubirComMensagemSobreOEnv() {
        when(usuarioService.existe("admin")).thenReturn(false);
        when(usuarioService.cadastrar(new CadastroUsuarioRequest("admin", "Administrador", "curta", Papel.ADMIN)))
                .thenThrow(new RegraDeNegocioException("A senha deve ter de 12 a 128 caracteres."));

        assertThatThrownBy(() -> runner("curta").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A senha deve ter")
                .hasMessageContaining("ADMIN_PASSWORD");
    }
}
