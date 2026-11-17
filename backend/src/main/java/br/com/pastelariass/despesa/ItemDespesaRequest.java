package br.com.pastelariass.despesa;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Uma linha da compra de ingredientes:
 * {"descricao": "Farinha", "quantidade": 10.000, "unidade": "KG", "precoUnitario": 5.50}
 */
public record ItemDespesaRequest(
        @NotBlank @Size(max = 120) String descricao,
        @NotNull @DecimalMin("0.001") @Digits(integer = 4, fraction = 3) BigDecimal quantidade,
        @NotNull UnidadeMedida unidade,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal precoUnitario) {
}
