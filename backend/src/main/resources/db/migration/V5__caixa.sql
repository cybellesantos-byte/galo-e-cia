-- ============================================================
-- V5 — Caixa (um por negócio, por vez)
--
-- Caixa = a gaveta de um negócio em um período. Abre com um saldo inicial,
-- recebe reforços (troco) e sangrias (retiradas), e fecha com a contagem
-- física do dinheiro, comparada com o que deveria haver.
--
-- Caixa fechado não se altera: o fechamento grava o esperado e a diferença,
-- e nenhuma linha dele muda depois.
-- ============================================================

CREATE TABLE caixa (
    id                     BIGSERIAL     PRIMARY KEY,
    negocio                VARCHAR(20)   NOT NULL CHECK (negocio IN ('PASTELARIA', 'PEIXARIA')),
    saldo_inicial          NUMERIC(10,2) NOT NULL CHECK (saldo_inicial >= 0),
    aberto_em              TIMESTAMPTZ   NOT NULL,
    aberto_por             VARCHAR(60)   NOT NULL,
    fechado_em             TIMESTAMPTZ,
    fechado_por            VARCHAR(60),
    valor_contado          NUMERIC(10,2) CHECK (valor_contado >= 0),
    esperado               NUMERIC(10,2),
    diferenca              NUMERIC(10,2),
    observacao_fechamento  VARCHAR(500),
    -- Ou está aberto (nenhum dado de fechamento), ou fechado (todos preenchidos).
    -- Nunca no meio do caminho.
    CHECK (
        (fechado_em IS NULL AND fechado_por IS NULL AND valor_contado IS NULL
            AND esperado IS NULL AND diferenca IS NULL)
        OR
        (fechado_em IS NOT NULL AND fechado_por IS NOT NULL AND valor_contado IS NOT NULL
            AND esperado IS NOT NULL AND diferenca IS NOT NULL)
    )
);

-- No máximo UM caixa aberto por negócio. O banco garante, mesmo que duas
-- pessoas abram ao mesmo tempo. A pastelaria e a peixaria podem ter o seu cada uma.
CREATE UNIQUE INDEX uk_caixa_aberto_por_negocio ON caixa (negocio) WHERE fechado_em IS NULL;

CREATE TABLE movimentacao_caixa (
    id          BIGSERIAL     PRIMARY KEY,
    caixa_id    BIGINT        NOT NULL REFERENCES caixa (id),
    -- SANGRIA: retirada de dinheiro da gaveta. REFORCO: colocar troco na gaveta.
    tipo        VARCHAR(10)   NOT NULL CHECK (tipo IN ('SANGRIA', 'REFORCO')),
    valor       NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    quando      TIMESTAMPTZ   NOT NULL,
    usuario     VARCHAR(60)   NOT NULL,
    observacao  VARCHAR(500)
);

CREATE INDEX idx_movimentacao_caixa ON movimentacao_caixa (caixa_id, quando);
