package br.com.pastelariass.seguranca;

import br.com.pastelariass.auditoria.AcaoAuditoria;
import br.com.pastelariass.auditoria.AuditoriaService;
import br.com.pastelariass.comum.TratadorDeErros.ErroResponse;
import br.com.pastelariass.usuario.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints de autenticação:
 *   GET  /api/auth/csrf    → entrega o cookie XSRF-TOKEN (antes do primeiro POST)
 *   POST /api/auth/login   → confere usuário e senha e abre a sessão
 *   GET  /api/auth/me      → diz quem está logado
 *   POST /api/auth/logout  → encerra a sessão (feito pelo próprio Spring, ver SecurityConfig)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UsuarioService usuarioService;
    private final LimiteTentativasLogin limiteTentativas;
    private final AuditoriaService auditoria;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          UsuarioService usuarioService,
                          LimiteTentativasLogin limiteTentativas,
                          AuditoriaService auditoria) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.usuarioService = usuarioService;
        this.limiteTentativas = limiteTentativas;
        this.auditoria = auditoria;
    }

    /**
     * O frontend chama ao abrir a página. Pedir o token o obriga a ser gerado,
     * e o Spring o grava no cookie XSRF-TOKEN da resposta.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest dados,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        // Confere o bloqueio ANTES de olhar a senha: com o nome bloqueado, nem a
        // senha certa entra. Assim o limite não pode ser contornado acertando por sorte.
        if (limiteTentativas.bloqueado(dados.username())) {
            auditoria.registrar(dados.username(), AcaoAuditoria.LOGIN_BLOQUEADO, null, null);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ErroResponse.de("Muitas tentativas de login. Tente novamente em alguns minutos."));
        }

        Authentication autenticacao;
        try {
            autenticacao = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(dados.username(), dados.senha()));
        } catch (AuthenticationException e) {
            // A MESMA resposta para usuário inexistente, senha errada ou usuário
            // desativado. Respostas diferentes ensinariam um atacante quais
            // usuários existem.
            limiteTentativas.registrarFalha(dados.username());
            auditoria.registrar(dados.username(), AcaoAuditoria.LOGIN_FALHA, null, null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErroResponse.de("Usuário ou senha inválidos."));
        }
        limiteTentativas.limpar(dados.username());

        // Se já havia uma sessão, troca o ID dela. Isso impede o ataque de
        // "session fixation": alguém plantar um ID de sessão conhecido antes
        // do login e reaproveitá-lo depois.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        // Guarda o usuário autenticado na sessão: as próximas requisições
        // chegam com o cookie JSESSIONID e o Spring sabe quem é.
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);
        securityContextRepository.saveContext(contexto, request, response);

        var usuario = usuarioService.registrarLogin(autenticacao.getName());
        // Guarda na sessão a hora deste login. Se a senha mudar depois, o filtro
        // percebe que esta sessão é antiga e a derruba.
        request.getSession().setAttribute(VerificaUsuarioAtivoFilter.ATRIBUTO_AUTENTICADO_EM,
                usuario.getUltimoLoginEm());
        auditoria.registrar(usuario.getUsername(), AcaoAuditoria.LOGIN_OK, null, null);
        return ResponseEntity.ok(UsuarioLogadoResponse.de(usuario));
    }

    @GetMapping("/me")
    public UsuarioLogadoResponse me(Authentication autenticacao) {
        return UsuarioLogadoResponse.de(usuarioService.buscarPorUsername(autenticacao.getName()));
    }

    /** Qualquer pessoa logada troca a PRÓPRIA senha. O nome vem da sessão, nunca do corpo do pedido. */
    @PutMapping("/senha")
    public ResponseEntity<Void> trocarSenha(@Valid @RequestBody TrocaSenhaRequest dados,
                                            Authentication autenticacao,
                                            HttpServletRequest request) {
        var quando = usuarioService.trocarSenha(autenticacao.getName(), dados.senhaAtual(), dados.senhaNova());
        // Esta sessão continua valendo: quem acabou de trocar a senha prova que a conhece.
        // As OUTRAS sessões, abertas com a senha antiga, caem no próximo acesso.
        var sessao = request.getSession(false);
        if (sessao != null) {
            sessao.setAttribute(VerificaUsuarioAtivoFilter.ATRIBUTO_AUTENTICADO_EM, quando);
        }
        return ResponseEntity.noContent().build();
    }
}
