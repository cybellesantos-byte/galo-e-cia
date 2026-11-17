package br.com.pastelariass.caixa;

import br.com.pastelariass.comum.Negocio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Caixa de cada negócio. Qualquer pessoa logada (ADMIN ou EQUIPE) opera o caixa.
 *
 *   GET  /api/caixas?negocio=PASTELARIA        → histórico (o filtro é opcional)
 *   GET  /api/caixas/aberto?negocio=PEIXARIA   → o caixa aberto, se houver
 *   GET  /api/caixas/{id}                      → um caixa, com suas movimentações
 *   POST /api/caixas                           → abre um caixa
 *   POST /api/caixas/{id}/reforcos             → põe troco na gaveta
 *   POST /api/caixas/{id}/sangrias             → tira dinheiro da gaveta
 *   POST /api/caixas/{id}/fechar               → fecha, com a contagem física
 */
@RestController
@RequestMapping("/api/caixas")
public class CaixaController {

    private final CaixaService caixaService;

    public CaixaController(CaixaService caixaService) {
        this.caixaService = caixaService;
    }

    @GetMapping
    public List<CaixaResponse> listar(@RequestParam(required = false) Negocio negocio) {
        return caixaService.listar(negocio).stream().map(this::resposta).toList();
    }

    @GetMapping("/aberto")
    public CaixaResponse aberto(@RequestParam Negocio negocio) {
        return resposta(caixaService.caixaAberto(negocio));
    }

    @GetMapping("/{id}")
    public CaixaResponse buscar(@PathVariable Long id) {
        return resposta(caixaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<CaixaResponse> abrir(@Valid @RequestBody AberturaCaixaRequest dados) {
        var caixa = caixaService.abrir(dados.negocio(), dados.saldoInicial());
        return ResponseEntity.created(URI.create("/api/caixas/" + caixa.getId()))
                .body(resposta(caixa));
    }

    @PostMapping("/{id}/reforcos")
    public CaixaResponse reforco(@PathVariable Long id, @Valid @RequestBody MovimentoCaixaRequest dados) {
        return resposta(caixaService.reforco(id, dados.valor(), dados.observacao()));
    }

    @PostMapping("/{id}/sangrias")
    public CaixaResponse sangria(@PathVariable Long id, @Valid @RequestBody MovimentoCaixaRequest dados) {
        return resposta(caixaService.sangria(id, dados.valor(), dados.observacao()));
    }

    @PostMapping("/{id}/fechar")
    public CaixaResponse fechar(@PathVariable Long id, @Valid @RequestBody FechamentoCaixaRequest dados) {
        return resposta(caixaService.fechar(id, dados.valorContado(), dados.observacao()));
    }

    private CaixaResponse resposta(Caixa caixa) {
        var movimentos = caixaService.movimentacoes(caixa.getId()).stream()
                .map(MovimentacaoResponse::de)
                .toList();
        // Aberto: o esperado é o total da composição (calculada uma vez só).
        // Fechado: não há composição, e vale o esperado gravado no fechamento.
        var composicao = caixaService.composicao(caixa);
        var esperado = composicao != null ? composicao.esperado() : caixa.getEsperado();
        return new CaixaResponse(caixa.getId(), caixa.getNegocio(), caixa.estaAberto(),
                caixa.getSaldoInicial(), caixa.getAbertoEm(), caixa.getAbertoPor(),
                caixa.getFechadoEm(), caixa.getFechadoPor(), esperado, composicao,
                caixa.getValorContado(), caixa.getDiferenca(), caixa.getObservacaoFechamento(),
                movimentos);
    }

    /** O JSON: {"negocio": "PASTELARIA", "saldoInicial": 100.00} */
    public record AberturaCaixaRequest(
            @NotNull Negocio negocio,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal saldoInicial) {
    }

    /** O JSON de sangria e de reforço: {"valor": 30.00, "observacao": "troco do dia"} */
    public record MovimentoCaixaRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal valor,
            @Size(max = 500) String observacao) {
    }

    /** O JSON de fechamento: {"valorContado": 68.00, "observacao": "faltou R$ 2 no troco"} */
    public record FechamentoCaixaRequest(
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal valorContado,
            @Size(max = 500) String observacao) {
    }

    public record CaixaResponse(Long id, Negocio negocio, boolean aberto, BigDecimal saldoInicial,
                                Instant abertoEm, String abertoPor, Instant fechadoEm, String fechadoPor,
                                BigDecimal esperado, ComposicaoEsperado composicao,
                                BigDecimal valorContado, BigDecimal diferenca,
                                String observacaoFechamento, List<MovimentacaoResponse> movimentacoes) {
    }

    public record MovimentacaoResponse(Long id, TipoMovimentacao tipo, BigDecimal valor,
                                       Instant quando, String usuario, String observacao) {

        static MovimentacaoResponse de(MovimentacaoCaixa movimento) {
            return new MovimentacaoResponse(movimento.getId(), movimento.getTipo(), movimento.getValor(),
                    movimento.getQuando(), movimento.getUsuario(), movimento.getObservacao());
        }
    }
}
