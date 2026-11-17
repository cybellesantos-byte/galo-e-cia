package br.com.pastelariass.venda;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Vendas da pastelaria. Qualquer pessoa logada (ADMIN ou EQUIPE) registra vendas.
 *
 *   POST /api/vendas                          → registra uma venda
 *   GET  /api/vendas?caixaId=3                → vendas de um caixa
 *   GET  /api/vendas/{id}                     → uma venda, com os itens
 *   POST /api/vendas/{id}/confirmar-pagamento → confirma o Pix recebido
 *   POST /api/vendas/{id}/cancelar            → cancela, com motivo
 */
@RestController
@RequestMapping("/api/vendas")
public class VendaController {

    private final VendaService vendaService;

    public VendaController(VendaService vendaService) {
        this.vendaService = vendaService;
    }

    @PostMapping
    public ResponseEntity<VendaResponse> registrar(@Valid @RequestBody CriacaoVendaRequest dados) {
        var venda = vendaService.registrar(dados);
        return ResponseEntity.created(URI.create("/api/vendas/" + venda.getId()))
                .body(resposta(venda));
    }

    @GetMapping
    public List<VendaResponse> listar(@RequestParam Long caixaId) {
        return vendaService.listarDoCaixa(caixaId).stream().map(this::resposta).toList();
    }

    @GetMapping("/{id}")
    public VendaResponse buscar(@PathVariable Long id) {
        return resposta(vendaService.buscarPorId(id));
    }

    @PostMapping("/{id}/confirmar-pagamento")
    public VendaResponse confirmarPagamento(@PathVariable Long id) {
        return resposta(vendaService.confirmarPagamento(id));
    }

    /** Só ADMIN: cancelar é devolver o dinheiro da venda. */
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('ADMIN')")
    public VendaResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelamentoVendaRequest dados) {
        return resposta(vendaService.cancelar(id, dados.motivo()));
    }

    private VendaResponse resposta(Venda venda) {
        var itens = vendaService.itensDe(venda.getId()).stream().map(ItemVendaResponse::de).toList();
        return new VendaResponse(venda.getId(), venda.getCaixaId(), venda.getOrigem(), venda.getTipo(),
                venda.getLocalidadeId(), venda.getTaxaPadrao(), venda.getTaxaEntrega(), venda.getSubtotal(),
                venda.getTotal(),
                venda.getFormaPagamento(), venda.getSituacaoPagamento(), venda.getValorRecebido(),
                venda.getTroco(), venda.getObservacao(), venda.isCancelada(), venda.getMotivoCancelamento(),
                venda.getCriadoEm(), venda.getCriadoPor(), itens);
    }

    /** O JSON de cancelamento: {"motivo": "cliente desistiu"} */
    public record CancelamentoVendaRequest(@NotBlank @Size(max = 200) String motivo) {
    }

    public record VendaResponse(Long id, Long caixaId, Origem origem, TipoVenda tipo, Long localidadeId,
                                BigDecimal taxaPadrao, BigDecimal taxaEntrega, BigDecimal subtotal, BigDecimal total,
                                FormaPagamento formaPagamento, SituacaoPagamento situacaoPagamento,
                                BigDecimal valorRecebido, BigDecimal troco, String observacao,
                                boolean cancelada, String motivoCancelamento, Instant criadoEm,
                                String criadoPor, List<ItemVendaResponse> itens) {
    }

    public record ItemVendaResponse(Long produtoId, String nome, BigDecimal precoUnitario,
                                    BigDecimal quantidade, BigDecimal total) {

        static ItemVendaResponse de(VendaItem item) {
            return new ItemVendaResponse(item.getProdutoId(), item.getNomeProduto(), item.getPrecoUnitario(),
                    item.getQuantidade(), item.getTotal());
        }
    }
}
