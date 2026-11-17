-- ============================================================
-- V6 — Vendas da pastelaria
--
-- Uma venda pertence ao caixa aberto da pastelaria no momento em que foi feita.
-- Preço, nome do produto, taxa de entrega e total são CONGELADOS aqui: mudar o
-- cardápio depois não altera vendas antigas.
--
-- Nada se apaga: venda errada é cancelada, com motivo e responsável.
-- ============================================================

CREATE TABLE venda (
    id                  BIGSERIAL     PRIMARY KEY,
    -- Chave gerada pelo app para cada venda. Se a mesma chave chegar de novo
    -- (duplo clique, rede instável), o sistema devolve a venda já registrada.
    chave_da_venda      UUID          NOT NULL,
    caixa_id            BIGINT        NOT NULL REFERENCES caixa (id),
    origem              VARCHAR(20)   NOT NULL CHECK (origem IN ('BALCAO', 'WHATSAPP')),
    tipo                VARCHAR(20)   NOT NULL CHECK (tipo IN ('RETIRADA', 'ENTREGA')),
    localidade_id       BIGINT        REFERENCES localidade (id),
    taxa_entrega        NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (taxa_entrega >= 0),
    subtotal            NUMERIC(10,2) NOT NULL CHECK (subtotal > 0),
    total               NUMERIC(10,2) NOT NULL CHECK (total > 0),
    forma_pagamento     VARCHAR(10)   NOT NULL CHECK (forma_pagamento IN ('PIX', 'DINHEIRO')),
    situacao_pagamento  VARCHAR(10)   NOT NULL CHECK (situacao_pagamento IN ('PENDENTE', 'PAGO')),
    -- Só no dinheiro: quanto o cliente entregou e quanto voltou de troco.
    valor_recebido      NUMERIC(10,2),
    troco               NUMERIC(10,2),
    observacao          VARCHAR(500),
    cancelada           BOOLEAN       NOT NULL DEFAULT FALSE,
    motivo_cancelamento VARCHAR(200),
    cancelado_por       VARCHAR(60),
    cancelado_em        TIMESTAMPTZ,
    criado_em           TIMESTAMPTZ   NOT NULL,
    criado_por          VARCHAR(60)   NOT NULL,

    CHECK (tipo <> 'ENTREGA' OR localidade_id IS NOT NULL),
    CHECK (tipo <> 'RETIRADA' OR (localidade_id IS NULL AND taxa_entrega = 0)),
    CHECK (total = subtotal + taxa_entrega),
    CHECK (
        (forma_pagamento = 'DINHEIRO' AND valor_recebido IS NOT NULL AND troco = valor_recebido - total
            AND troco >= 0 AND situacao_pagamento = 'PAGO')
        OR
        (forma_pagamento = 'PIX' AND valor_recebido IS NULL AND troco IS NULL)
    ),
    CHECK (
        NOT cancelada
        OR (motivo_cancelamento IS NOT NULL AND cancelado_por IS NOT NULL AND cancelado_em IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_venda_chave ON venda (chave_da_venda);
CREATE INDEX idx_venda_caixa ON venda (caixa_id, criado_em);

CREATE TABLE venda_item (
    id              BIGSERIAL     PRIMARY KEY,
    venda_id        BIGINT        NOT NULL REFERENCES venda (id),
    produto_id      BIGINT        NOT NULL REFERENCES produto (id),
    -- Nome e preço copiados do produto no momento da venda.
    nome_produto    VARCHAR(120)  NOT NULL,
    preco_unitario  NUMERIC(10,2) NOT NULL CHECK (preco_unitario > 0),
    quantidade      INTEGER       NOT NULL CHECK (quantidade BETWEEN 1 AND 999),
    total           NUMERIC(10,2) NOT NULL CHECK (total > 0),
    CHECK (total = preco_unitario * quantidade),
    -- Um produto aparece uma vez por venda. Para mais unidades, soma a quantidade.
    UNIQUE (venda_id, produto_id)
);
