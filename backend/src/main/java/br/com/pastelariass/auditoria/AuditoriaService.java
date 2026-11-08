package br.com.pastelariass.auditoria;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Escreve no diário da casa. Quem chama informa O QUE aconteceu; a hora e,
 * quando for o caso, quem estava logado são preenchidos aqui.
 */
@Service
public class AuditoriaService {

    /** Quem aparece quando não há ninguém logado (por exemplo, o início do sistema). */
    static final String SISTEMA = "sistema";

    private final RegistroAuditoriaRepository repositorio;
    private final Clock relogio;

    public AuditoriaService(RegistroAuditoriaRepository repositorio, Clock relogio) {
        this.repositorio = repositorio;
        this.relogio = relogio;
    }

    @Transactional
    public void registrar(String usuario, AcaoAuditoria acao, String alvo, String detalhes) {
        var agora = Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);
        repositorio.save(new RegistroAuditoria(agora, usuario, acao, alvo, detalhes));
    }

    /** O nome de quem está logado nesta requisição, ou "sistema" se ninguém está. */
    public String quemEstaLogado() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        boolean logado = autenticacao != null
                && autenticacao.isAuthenticated()
                && !(autenticacao instanceof AnonymousAuthenticationToken);
        return logado ? autenticacao.getName() : SISTEMA;
    }

    @Transactional(readOnly = true)
    public List<RegistroAuditoria> ultimos() {
        return repositorio.findTop100ByOrderByQuandoDesc();
    }
}
