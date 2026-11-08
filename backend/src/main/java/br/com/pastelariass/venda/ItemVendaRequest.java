package br.com.pastelariass.venda;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Uma linha do pedido: que produto e quanto. Pastel: 4. Tilápia: 1.350 (kg).
 * O preço NÃO vem daqui. As regras por unidade ficam no VendaService.
 */
public record ItemVendaRequest(
        @NotNull Long produtoId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 4, fraction = 3) BigDecimal quantidade) {
}
