-- ============================================================
-- V11 — Taxa de entrega editável e repasses ao entregador
--
-- Venda de entrega guarda duas taxas:
--   taxa_padrao  → a da localidade, no dia da venda.
--   taxa_entrega → a efetivamente cobrada do cliente (pode ser diferente).
--
-- Repasse: o valor devido ao entregador por uma entrega. É sempre o MAIOR
-- entre a taxa padrão e a taxa cobrada. Cada venda gera no máximo UM repasse.
-- ============================================================

ALTER TABLE venda ADD COLUMN taxa_padrao NUMERIC(10,2) CHECK (taxa_padrao >= 0);

-- Vendas de entrega já existentes: a taxa cobrada era a padrão.
UPDATE venda SET taxa_padrao = taxa_entrega WHERE tipo = 'ENTREGA';

ALTER TABLE venda ADD CONSTRAINT venda_taxa_padrao_entrega_check
    CHECK (tipo <> 'ENTREGA' OR taxa_padrao IS NOT NULL);

CREATE TABLE repasse_entrega (
    id               BIGSERIAL     PRIMARY KEY,
    venda_id         BIGINT        NOT NULL REFERENCES venda (id),
    valor            NUMERIC(10,2) NOT NULL CHECK (valor >= 0),
    data_entrega     DATE          NOT NULL,
    situacao         VARCHAR(10)   NOT NULL CHECK (situacao IN ('PENDENTE', 'PAGO', 'CANCELADO')),
    forma_pagamento  VARCHAR(10)   CHECK (forma_pagamento IN ('PIX', 'CARTAO', 'DINHEIRO')),
    pago_em          TIMESTAMPTZ,
    pago_por         VARCHAR(60),
    -- Só repasse pago em dinheiro fica vinculado ao caixa (sai da gaveta).
    caixa_id         BIGINT        REFERENCES caixa (id),
    criado_em        TIMESTAMPTZ   NOT NULL,

    -- Uma entrega, um repasse. Mesmo com cliques simultâneos, não há dois.
    CONSTRAINT uk_repasse_venda UNIQUE (venda_id),
    CHECK (
        (situacao = 'PAGO' AND forma_pagamento IS NOT NULL AND pago_em IS NOT NULL AND pago_por IS NOT NULL
            AND ((forma_pagamento = 'DINHEIRO' AND caixa_id IS NOT NULL)
                 OR (forma_pagamento <> 'DINHEIRO' AND caixa_id IS NULL)))
        OR
        (situacao IN ('PENDENTE', 'CANCELADO') AND forma_pagamento IS NULL AND pago_em IS NULL
            AND pago_por IS NULL AND caixa_id IS NULL)
    )
);

CREATE INDEX idx_repasse_data ON repasse_entrega (data_entrega);
CREATE INDEX idx_repasse_caixa ON repasse_entrega (caixa_id);
