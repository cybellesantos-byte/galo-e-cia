package br.com.pastelariass.repasse;

/**
 * PENDENTE: o entregador ainda não recebeu. PAGO: já recebeu.
 * CANCELADO: a venda foi cancelada antes do pagamento, então não há o que pagar.
 */
public enum SituacaoRepasse {
    PENDENTE,
    PAGO,
    CANCELADO
}
