package br.com.pastelariass.auditoria;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Consulta do diário da casa. SÓ ADMIN: a EQUIPE não lê o histórico de quem fez o quê.
 * Não há rotas para alterar ou apagar registros.
 */
@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasRole('ADMIN')")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<RegistroResponse> listar() {
        return auditoriaService.ultimos().stream().map(RegistroResponse::de).toList();
    }

    public record RegistroResponse(Long id, Instant quando, String usuario,
                                   AcaoAuditoria acao, String alvo, String detalhes) {

        static RegistroResponse de(RegistroAuditoria r) {
            return new RegistroResponse(r.getId(), r.getQuando(), r.getUsuario(),
                    r.getAcao(), r.getAlvo(), r.getDetalhes());
        }
    }
}
