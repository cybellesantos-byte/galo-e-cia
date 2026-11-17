package br.com.pastelariass.seguranca;

import br.com.pastelariass.usuario.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * "Tradutor" entre a nossa tabela usuario e o Spring Security.
 *
 * O Spring Security não conhece a classe Usuario; ele entende UserDetails.
 * Aqui buscamos o usuário no banco e o entregamos no formato que ele espera:
 * nome, hash da senha, papel (vira ROLE_ADMIN ou ROLE_EQUIPE) e se está ativo.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var usuario = usuarioRepository.findByUsernameIgnoreCase(username)
                // Mensagem genérica: nunca revelar se o usuário existe ou não.
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));

        return User.builder()
                .username(usuario.getUsername())
                .password(usuario.getSenhaHash())
                .roles(usuario.getPapel().name())
                // Usuário desativado não consegue entrar, mesmo com a senha certa.
                .disabled(!usuario.isAtivo())
                .build();
    }
}
