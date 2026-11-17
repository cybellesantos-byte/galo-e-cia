package br.com.pastelariass.despesa;

import br.com.pastelariass.comum.Negocio;
import br.com.pastelariass.venda.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * O JSON para registrar uma despesa:
 * {"negocio": "PEIXARIA", "descricao": "Tilápia do fornecedor", "categoria": "COMPRA_DE_PEIXE",
 *  "valor": 500.00, "data": "2026-10-20", "responsavel": "Ana",
 *  "formaPagamento": "PIX", "pesoKg": 30.000}
 *
 * Sem "formaPagamento", a despesa fica pendente.
 *
 * Compra de ingredientes pode vir com "itens" detalhados. Nesse caso o valor
 * é a soma dos itens, e o campo "valor" é opcional (se vier, tem que bater).
 */
public record CriacaoDespesaRequest(
        @NotNull Negocio negocio,
        @NotBlank @Size(max = 120) String descricao,
        @NotNull CategoriaDespesa categoria,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal valor,
        @NotNull LocalDate data,
        @NotBlank @Size(max = 60) String responsavel,
        FormaPagamento formaPagamento,
        @DecimalMin("0.001") @Digits(integer = 4, fraction = 3) BigDecimal pesoKg,
        @Size(max = 500) String observacao,
        @Size(max = 100) List<@Valid ItemDespesaRequest> itens) {
}
