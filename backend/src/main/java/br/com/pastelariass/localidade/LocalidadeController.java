package br.com.pastelariass.localidade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

/**
 * Localidades de entrega e suas taxas. Qualquer pessoa logada pode consultar;
 * alterar faz parte da operação do dia a dia, como manter a lista de produtos.
 *
 *   GET /api/localidades       → lista
 *   POST /api/localidades      → cadastra
 *   PUT /api/localidades/{id}  → altera nome, taxa e situação
 */
@RestController
@RequestMapping("/api/localidades")
public class LocalidadeController {

    private final LocalidadeService localidadeService;

    public LocalidadeController(LocalidadeService localidadeService) {
        this.localidadeService = localidadeService;
    }

    @GetMapping
    public List<LocalidadeResponse> listar() {
        return localidadeService.listar().stream().map(LocalidadeResponse::de).toList();
    }

    @PostMapping
    public ResponseEntity<LocalidadeResponse> cadastrar(@Valid @RequestBody CadastroLocalidadeRequest dados) {
        var localidade = localidadeService.cadastrar(dados.nome(), dados.taxa());
        return ResponseEntity.created(URI.create("/api/localidades/" + localidade.getId()))
                .body(LocalidadeResponse.de(localidade));
    }

    @PutMapping("/{id}")
    public LocalidadeResponse alterar(@PathVariable Long id, @Valid @RequestBody AlteracaoLocalidadeRequest dados) {
        return LocalidadeResponse.de(localidadeService.alterar(id, dados.nome(), dados.taxa(), dados.ativo()));
    }

    /** O JSON: {"nome": "Saco", "taxa": 6.00} */
    public record CadastroLocalidadeRequest(
            @NotBlank @Size(max = 120) String nome,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal taxa) {
    }

    /** O JSON: {"nome": "Saco", "taxa": 6.00, "ativo": true} */
    public record AlteracaoLocalidadeRequest(
            @NotBlank @Size(max = 120) String nome,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal taxa,
            @NotNull Boolean ativo) {
    }

    public record LocalidadeResponse(Long id, String nome, BigDecimal taxa, boolean ativo) {

        static LocalidadeResponse de(Localidade localidade) {
            return new LocalidadeResponse(localidade.getId(), localidade.getNome(),
                    localidade.getTaxa(), localidade.isAtivo());
        }
    }
}
