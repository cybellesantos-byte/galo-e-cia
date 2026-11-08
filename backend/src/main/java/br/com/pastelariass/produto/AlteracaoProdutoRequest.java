package br.com.pastelariass.produto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * O JSON para alterar nome, categoria, preço ou custo. Negócio e unidade não
 * aparecem aqui de propósito: não podem mudar depois do cadastro.
 */
public record AlteracaoProdutoRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(max = 60) String categoria,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal preco,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal custoEstimado,
        @Size(max = 200) String descricao) {

    /** Sem descrição: o que já existia continua valendo; a descrição fica em branco. */
    public AlteracaoProdutoRequest(String nome, String categoria, BigDecimal preco, BigDecimal custoEstimado) {
        this(nome, categoria, preco, custoEstimado, null);
    }
}
