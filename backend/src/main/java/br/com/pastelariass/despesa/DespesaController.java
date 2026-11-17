package br.com.pastelariass.despesa;

import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.venda.FormaPagamento;
import br.com.pastelariass.venda.SituacaoPagamento;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Despesas e compras de peixe. Qualquer pessoa logada registra e consulta.
 *
 *   POST /api/despesas                            → registra (paga ou pendente)
 *   GET  /api/despesas?mes=2026-10&negocio=PEIXARIA → lista do mês (negócio opcional)
 *   POST /api/despesas/{id}/pagar                 → paga uma despesa pendente
 *   GET  /api/despesas/resumo?mes=2026-10&negocio=PEIXARIA → totais do mês
 */
@RestController
@RequestMapping("/api/despesas")
public class DespesaController {

    private final DespesaService despesaService;

    public DespesaController(DespesaService despesaService) {
        this.despesaService = despesaService;
    }

    @PostMapping
    public ResponseEntity<DespesaResponse> registrar(@Valid @RequestBody CriacaoDespesaRequest dados) {
        var despesa = despesaService.registrar(dados);
        return ResponseEntity.created(URI.create("/api/despesas/" + despesa.getId()))
                .body(resposta(despesa));
    }

    @GetMapping
    public List<DespesaResponse> listar(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @RequestParam(required = false) Negocio negocio) {
        return despesaService.listar(mes, negocio).stream().map(this::resposta).toList();
    }

    @PostMapping("/{id}/pagar")
    public DespesaResponse pagar(@PathVariable Long id, @Valid @RequestBody PagamentoDespesaRequest dados) {
        return resposta(despesaService.pagar(id, dados));
    }

    @GetMapping("/resumo")
    public DespesaService.ResumoDespesas resumo(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @RequestParam Negocio negocio) {
        return despesaService.resumo(negocio, mes);
    }

    private DespesaResponse resposta(Despesa despesa) {
        var itens = despesaService.itensDe(despesa.getId()).stream()
                .map(ItemDespesaResponse::de)
                .toList();
        return new DespesaResponse(despesa.getId(), despesa.getNegocio(), despesa.getDescricao(),
                despesa.getCategoria(), despesa.getValor(), despesa.getDataDespesa(), despesa.getResponsavel(),
                despesa.getSituacao(), despesa.getFormaPagamento(), despesa.getPagoEm(), despesa.getCaixaId(),
                despesa.getPesoKg(), despesa.getObservacao(), despesa.getCriadoEm(), despesa.getCriadoPor(),
                itens);
    }

    public record DespesaResponse(Long id, Negocio negocio, String descricao, CategoriaDespesa categoria,
                                  BigDecimal valor, LocalDate data, String responsavel,
                                  SituacaoPagamento situacao, FormaPagamento formaPagamento, Instant pagoEm,
                                  Long caixaId, BigDecimal pesoKg, String observacao, Instant criadoEm,
                                  String criadoPor, List<ItemDespesaResponse> itens) {
    }

    public record ItemDespesaResponse(Long id, String descricao, BigDecimal quantidade, UnidadeMedida unidade,
                                      BigDecimal precoUnitario, BigDecimal total) {

        static ItemDespesaResponse de(DespesaItem item) {
            return new ItemDespesaResponse(item.getId(), item.getDescricao(), item.getQuantidade(),
                    item.getUnidade(), item.getPrecoUnitario(), item.getTotal());
        }
    }
}
