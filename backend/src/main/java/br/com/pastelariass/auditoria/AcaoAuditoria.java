package br.com.pastelariass.auditoria;

/**
 * As ações que deixam rastro. Cada uma é um fato que alguém pode precisar
 * investigar. Novas ações entram aqui conforme as etapas avançam.
 */
public enum AcaoAuditoria {
    LOGIN_OK,
    LOGIN_FALHA,
    LOGIN_BLOQUEADO,
    USUARIO_CADASTRADO,
    USUARIO_DESATIVADO,
    USUARIO_REATIVADO,
    SENHA_TROCADA,
    CAIXA_ABERTO,
    CAIXA_SANGRIA,
    CAIXA_REFORCO,
    CAIXA_FECHADO,
    VENDA_REGISTRADA,
    VENDA_CANCELADA,
    PAGAMENTO_CONFIRMADO,
    DESPESA_REGISTRADA,
    DESPESA_PAGA,
    REPASSE_PAGO,
    PRODUTO_ALTERADO,
    LOCALIDADE_ALTERADA,
    LOJA_ALTERADA
}
