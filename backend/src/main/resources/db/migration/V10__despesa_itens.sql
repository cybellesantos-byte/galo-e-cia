-- ============================================================
-- V10 — Itens detalhados de despesa (compra de ingredientes)
--
-- Uma compra de ingredientes pode ser detalhada em linhas: ingrediente,
-- quantidade, unidade e preço. O valor da despesa é a soma dessas linhas.
--
-- Isto NÃO é controle de estoque: registra o que foi comprado e por quanto.
-- ============================================================

CREATE TABLE despesa_item (
    id              BIGSERIAL     PRIMARY KEY,
    despesa_id      BIGINT        NOT NULL REFERENCES despesa (id),
    descricao       VARCHAR(120)  NOT NULL,
    quantidade      NUMERIC(10,3) NOT NULL CHECK (quantidade > 0 AND quantidade <= 9999.999),
    unidade         VARCHAR(10)   NOT NULL CHECK (unidade IN ('UNIDADE', 'KG', 'G', 'LITRO', 'ML')),
    -- Preço por unidade de medida (por kg, por litro, por unidade...).
    preco_unitario  NUMERIC(10,2) NOT NULL CHECK (preco_unitario >= 0),
    total           NUMERIC(10,2) NOT NULL CHECK (total >= 0),
    -- Arredondado para centavos, "metade para cima", como nas vendas por peso.
    CHECK (total = ROUND(quantidade * preco_unitario, 2))
);

CREATE INDEX idx_despesa_item_despesa ON despesa_item (despesa_id);
