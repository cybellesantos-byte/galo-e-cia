package br.com.pastelariass.despesa;

import br.com.pastelariass.venda.FormaPagamento;
import jakarta.validation.constraints.NotNull;

/** O JSON para pagar uma despesa pendente: {"formaPagamento": "DINHEIRO"} */
public record PagamentoDespesaRequest(@NotNull FormaPagamento formaPagamento) {
}
