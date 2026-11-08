package br.com.pastelariass.produto;

import br.com.pastelariass.comum.Negocio;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * O JSON para cadastrar um produto:
 * {"negocio": "PEIXARIA", "nome": "Tilápia", "categoria": "Peixe",
 *  "unidadeVenda": "KG", "preco": 28.00, "custoEstimado": 20.00,
 *  "descricao": "filé fresco, limpo"}   ← descricao é opcional
 *
 * As anotações cuidam do formato. A ligação negócio × unidade e as regras de
 * preço ficam no ProdutoService, que vale também para quem não passa pela API.
 */
public record CadastroProdutoRequest(
        @NotNull Negocio negocio,
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(max = 60) String categoria,
        @NotNull UnidadeVenda unidadeVenda,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal preco,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal custoEstimado,
        @Size(max = 200) String descricao) {

    /** Sem descrição: quem cadastra só o essencial não precisa mencionar o campo. */
    public CadastroProdutoRequest(Negocio negocio, String nome, String categoria, UnidadeVenda unidadeVenda,
                                  BigDecimal preco, BigDecimal custoEstimado) {
        this(negocio, nome, categoria, unidadeVenda, preco, custoEstimado, null);
    }
}
