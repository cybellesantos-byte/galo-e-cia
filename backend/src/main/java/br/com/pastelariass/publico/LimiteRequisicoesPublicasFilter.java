package br.com.pastelariass.publico;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * O "fiscal da fila": roda em toda requisição, mas só age nas que vão para a
 * porta pública (/api/publico/...). As demais passam direto, sem contar nada.
 *
 * Fica ANTES da conferência de login, para que um robô seja barrado sem gastar
 * nem a consulta da sessão.
 */
public class LimiteRequisicoesPublicasFilter extends OncePerRequestFilter {

    private static final String PREFIXO_PUBLICO = "/api/publico/";

    private final LimiteRequisicoesPublicas limite;

    public LimiteRequisicoesPublicasFilter(LimiteRequisicoesPublicas limite) {
        this.limite = limite;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIXO_PUBLICO);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var segundosDeEspera = limite.registrar(request.getRemoteAddr());
        if (segundosDeEspera > 0) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(segundosDeEspera));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            // Mensagem fixa, sem dados de quem chamou: seguro montar o JSON à mão (igual ao SecurityConfig).
            response.getWriter().write("{\"mensagem\":\"Muitos acessos em pouco tempo. Tente de novo em instantes.\",\"detalhes\":[]}");
            return;
        }
        chain.doFilter(request, response);
    }
}
