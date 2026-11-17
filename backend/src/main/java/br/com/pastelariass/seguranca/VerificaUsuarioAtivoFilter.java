package br.com.pastelariass.seguranca;

import br.com.pastelariass.usuario.UsuarioService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Filtro que roda em TODA requisição e confere se quem está logado ainda pode entrar.
 *
 * Imagine o porteiro da pastelaria: ele não confere só o crachá quando a pessoa
 * entra. Ele confere de novo a cada vez que ela passa pela porta. Se a pessoa foi
 * demitida no meio do turno, o crachá deixa de valer na próxima passagem.
 *
 * Se o usuário está desativado, o filtro:
 *  1. esvazia quem está logado (SecurityContextHolder.clearContext);
 *  2. destrói a sessão no servidor (invalidate).
 * Depois disso, a requisição segue como anônima e o sistema responde 401.
 */
public class VerificaUsuarioAtivoFilter extends OncePerRequestFilter {

    /** Nome do atributo da sessão que guarda a hora do login (preenchido pelo AuthController). */
    public static final String ATRIBUTO_AUTENTICADO_EM = "autenticadoEm";

    private final UsuarioService usuarioService;

    public VerificaUsuarioAtivoFilter(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        boolean logado = autenticacao != null
                && autenticacao.isAuthenticated()
                && !(autenticacao instanceof AnonymousAuthenticationToken);
        var sessao = request.getSession(false);

        if (logado && sessao != null && precisaEncerrar(sessao, autenticacao.getName())) {
            SecurityContextHolder.clearContext();
            sessao.invalidate();
        }
        chain.doFilter(request, response);
    }

    /**
     * Encerra a sessão se o usuário foi desativado, ou trocou a senha depois do
     * login desta sessão.
     *
     * Sessão sem a hora do login (criada fora do nosso fluxo de login) não tem
     * como comparar a senha, mas a desativação continua valendo para ela.
     * Todo login feito pela API grava a hora, então esse caso só acontece fora dela.
     */
    private boolean precisaEncerrar(HttpSession sessao, String username) {
        Instant autenticadoEm = sessao.getAttribute(ATRIBUTO_AUTENTICADO_EM) instanceof Instant hora ? hora : null;
        return usuarioService.sessaoDeveSerEncerrada(username, autenticadoEm);
    }
}
