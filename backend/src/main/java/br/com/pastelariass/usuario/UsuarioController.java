package br.com.pastelariass.usuario;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Gestão de usuários. TODOS os métodos desta classe são só para ADMIN:
 * o @PreAuthorize em cima da classe vale para cada método dela.
 *
 *   POST /api/usuarios  → cadastra
 *   GET  /api/usuarios  → lista
 */
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> cadastrar(@Valid @RequestBody CadastroUsuarioRequest dados) {
        var usuario = usuarioService.cadastrar(dados);
        // 201 Created + o endereço do recurso novo no header Location (padrão REST).
        return ResponseEntity.created(URI.create("/api/usuarios/" + usuario.getId()))
                .body(UsuarioResponse.de(usuario));
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarioService.listar().stream().map(UsuarioResponse::de).toList();
    }

    /** O solicitante vem da sessão: não dá para desativar "em nome" de outra pessoa. */
    @PostMapping("/{id}/desativar")
    public UsuarioResponse desativar(@PathVariable Long id, Authentication autenticacao) {
        return UsuarioResponse.de(usuarioService.desativar(id, autenticacao.getName()));
    }

    @PostMapping("/{id}/reativar")
    public UsuarioResponse reativar(@PathVariable Long id) {
        return UsuarioResponse.de(usuarioService.reativar(id));
    }
}
