package br.com.pastelariass.venda;

import br.com.pastelariass.comum.Negocio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * O JSON para registrar uma venda:
 * {"chaveDaVenda": "...uuid...", "negocio": "PASTELARIA", "origem": "BALCAO",
 *  "tipo": "RETIRADA", "formaPagamento": "DINHEIRO", "valorRecebido": 50.00,
 *  "itens": [{"produtoId": 1, "quantidade": 4}]}
 *
 * O total NÃO vem no pedido: o sistema calcula.
 *
 * Em entrega, "taxaEntrega" é opcional: sem ele, cobra-se a taxa da localidade.
 * Com ele, cobra-se o valor informado, e o repasse ao entregador é o maior dos dois.
 */
public record CriacaoVendaRequest(
        @NotNull UUID chaveDaVenda,
        @NotNull Negocio negocio,
        @NotNull Origem origem,
        @NotNull TipoVenda tipo,
        Long localidadeId,
        @NotNull FormaPagamento formaPagamento,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal valorRecebido,
        @Size(max = 500) String observacao,
        @NotEmpty @Size(max = 50) List<@Valid ItemVendaRequest> itens,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal taxaEntrega) {

    /** Pedido sem taxa informada: cobra a taxa padrão da localidade. */
    public CriacaoVendaRequest(UUID chaveDaVenda, Negocio negocio, Origem origem, TipoVenda tipo, Long localidadeId,
                               FormaPagamento formaPagamento, BigDecimal valorRecebido, String observacao,
                               List<ItemVendaRequest> itens) {
        this(chaveDaVenda, negocio, origem, tipo, localidadeId, formaPagamento, valorRecebido, observacao, itens, null);
    }
}
