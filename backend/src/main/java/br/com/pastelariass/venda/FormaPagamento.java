package br.com.pastelariass.venda;

/**
 * PIX e CARTAO: não passam pela gaveta; ficam pendentes até a equipe confirmar
 * que o valor caiu na conta.
 * DINHEIRO: entra na gaveta pelo total (sem o troco) e já nasce paga.
 */
public enum FormaPagamento {
    PIX,
    CARTAO,
    DINHEIRO
}
