-- ============================================================
-- V9 — Despesas e compras de peixe
--
-- Despesa: dinheiro que sai de um negócio. Pode ser paga na hora ou ficar
-- pendente e ser paga depois.
--
-- Despesa paga em DINHEIRO é vinculada ao caixa aberto e reduz a gaveta.
-- Paga por PIX ou CARTÃO não mexe na gaveta.
--
-- Compra de peixe é uma categoria da peixaria, com peso em kg opcional
-- (para calcular o preço médio por kg).
-- ============================================================

CREATE TABLE despesa (
    id               BIGSERIAL     PRIMARY KEY,
    negocio          VARCHAR(20)   NOT NULL CHECK (negocio IN ('PASTELARIA', 'PEIXARIA')),
    descricao        VARCHAR(120)  NOT NULL,
    categoria        VARCHAR(30)   NOT NULL CHECK (categoria IN (
                         'INGREDIENTES', 'EMBALAGENS', 'GAS_E_ENERGIA', 'COMBUSTIVEL',
                         'MANUTENCAO', 'COMPRA_DE_PEIXE', 'OUTROS')),
    valor            NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    data_despesa     DATE          NOT NULL,
    responsavel      VARCHAR(60)   NOT NULL,
    -- Sem forma de pagamento = pendente. Com forma = paga.
    forma_pagamento  VARCHAR(10)   CHECK (forma_pagamento IN ('PIX', 'CARTAO', 'DINHEIRO')),
    pago_em          TIMESTAMPTZ,
    -- Só despesa paga em dinheiro fica vinculada ao caixa (a que sai da gaveta).
    caixa_id         BIGINT        REFERENCES caixa (id),
    peso_kg          NUMERIC(10,3) CHECK (peso_kg > 0),
    observacao       VARCHAR(500),
    criado_em        TIMESTAMPTZ   NOT NULL,
    criado_por       VARCHAR(60)   NOT NULL,

    -- Compra de peixe só existe na peixaria.
    CHECK (categoria <> 'COMPRA_DE_PEIXE' OR negocio = 'PEIXARIA'),
    -- Peso só se informa em compra de peixe.
    CHECK (peso_kg IS NULL OR categoria = 'COMPRA_DE_PEIXE'),
    CHECK (
        (forma_pagamento IS NULL AND pago_em IS NULL AND caixa_id IS NULL)
        OR
        (forma_pagamento = 'DINHEIRO' AND pago_em IS NOT NULL AND caixa_id IS NOT NULL)
        OR
        (forma_pagamento IN ('PIX', 'CARTAO') AND pago_em IS NOT NULL AND caixa_id IS NULL)
    )
);

CREATE INDEX idx_despesa_negocio_data ON despesa (negocio, data_despesa);
CREATE INDEX idx_despesa_caixa ON despesa (caixa_id);
