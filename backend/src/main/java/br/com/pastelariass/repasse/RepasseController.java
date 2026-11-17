package br.com.pastelariass.repasse;

import br.com.pastelariass.venda.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Repasses ao entregador. Qualquer pessoa logada consulta; só o ADMIN paga.
 *
 *   GET  /api/repasses?inicio=2026-10-01&fim=2026-10-31   → lista do período
 *   GET  /api/repasses/resumo?inicio=...&fim=...          → totais pendente e pago
 *   POST /api/repasses/{id}/pagar                         → paga uma entrega (ADMIN)
 *   POST /api/repasses/pagar                              → paga várias, tudo ou nada (ADMIN)
 */
@RestController
@RequestMapping("/api/repasses")
public class RepasseController {

    private final RepasseService repasseService;

    public RepasseController(RepasseService repasseService) {
        this.repasseService = repasseService;
    }

    @GetMapping
    public List<RepasseResponse> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return respostas(repasseService.listar(inicio, fim));
    }

    @GetMapping("/resumo")
    public RepasseService.ResumoRepasses resumo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return repasseService.resumo(inicio, fim);
    }

    /** Só ADMIN: o repasse é dinheiro que sai para o entregador. */
    @PostMapping("/{id}/pagar")
    @PreAuthorize("hasRole('ADMIN')")
    public RepasseResponse pagar(@PathVariable Long id, @Valid @RequestBody PagamentoRepasseRequest dados) {
        return respostas(List.of(repasseService.pagar(id, dados.formaPagamento()))).get(0);
    }

    /** Só ADMIN: o acerto da semana, várias entregas de uma vez, tudo ou nada. */
    @PostMapping("/pagar")
    @PreAuthorize("hasRole('ADMIN')")
    public PagamentoLoteResponse pagarVarios(@Valid @RequestBody PagamentoLoteRequest dados) {
        var pagos = respostas(repasseService.pagarVarios(dados.ids(), dados.formaPagamento()));
        var total = pagos.stream().map(RepasseResponse::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PagamentoLoteResponse(pagos.size(), total, pagos);
    }

    /** Converte para a resposta, já com o nome do local de cada entrega. */
    private List<RepasseResponse> respostas(List<RepasseEntrega> repasses) {
        var locais = repasseService.locaisDasVendas(repasses.stream().map(RepasseEntrega::getVendaId).toList());
        return repasses.stream().map(r -> RepasseResponse.de(r, locais.get(r.getVendaId()))).toList();
    }

    /** O JSON: {"formaPagamento": "DINHEIRO"} */
    public record PagamentoRepasseRequest(@NotNull FormaPagamento formaPagamento) {
    }

    /** O JSON: {"ids": [12, 15, 18], "formaPagamento": "PIX"} */
    public record PagamentoLoteRequest(
            @NotEmpty @Size(max = RepasseService.MAXIMO_POR_PAGAMENTO) List<@NotNull Long> ids,
            @NotNull FormaPagamento formaPagamento) {
    }

    /** Quantas entregas foram pagas, o total e cada uma delas. */
    public record PagamentoLoteResponse(int quantidade, BigDecimal total, List<RepasseResponse> repasses) {
    }

    public record RepasseResponse(Long id, Long vendaId, String localidade, BigDecimal valor, LocalDate dataEntrega,
                                  SituacaoRepasse situacao, FormaPagamento formaPagamento, Instant pagoEm,
                                  String pagoPor, Long caixaId, Instant criadoEm) {

        static RepasseResponse de(RepasseEntrega repasse, String localidade) {
            return new RepasseResponse(repasse.getId(), repasse.getVendaId(), localidade, repasse.getValor(),
                    repasse.getDataEntrega(), repasse.getSituacao(), repasse.getFormaPagamento(),
                    repasse.getPagoEm(), repasse.getPagoPor(), repasse.getCaixaId(), repasse.getCriadoEm());
        }
    }
}
