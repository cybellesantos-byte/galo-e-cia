-- ============================================================
-- V4 — Produtos e localidades de entrega
--
-- Produto: o que se vende. Pertence a UM negócio. A pastelaria vende por
-- unidade ("Pastel de carne"); a peixaria vende por kg ("Tilápia").
--
-- Dois sinalizadores, com sentidos diferentes:
--   ativo      → faz parte do cadastro. Desativado, não aparece para venda.
--   disponivel → dá para vender AGORA. Esgotado, continua no cadastro.
--
-- Nada se apaga: produto e localidade são desativados, para que as vendas
-- antigas continuem apontando para algo que existe.
-- ============================================================

CREATE TABLE produto (
    id              BIGSERIAL     PRIMARY KEY,
    negocio         VARCHAR(20)   NOT NULL CHECK (negocio IN ('PASTELARIA', 'PEIXARIA')),
    nome            VARCHAR(120)  NOT NULL,
    categoria       VARCHAR(60)   NOT NULL,
    unidade_venda   VARCHAR(10)   NOT NULL CHECK (unidade_venda IN ('UNIDADE', 'KG')),
    -- Preço por unidade (pastelaria) ou por kg (peixaria). Sempre positivo.
    preco           NUMERIC(10,2) NOT NULL CHECK (preco > 0),
    -- Opcional: sem custo cadastrado, a margem aparece como "incompleta", não como zero.
    custo_estimado  NUMERIC(10,2) CHECK (custo_estimado >= 0),
    ativo           BOOLEAN       NOT NULL DEFAULT TRUE,
    disponivel      BOOLEAN       NOT NULL DEFAULT TRUE
);

-- Dois produtos do MESMO negócio não podem ter o mesmo nome (sem diferença de
-- maiúsculas). O mesmo nome em negócios diferentes é permitido.
CREATE UNIQUE INDEX uk_produto_negocio_nome ON produto (negocio, LOWER(nome));

CREATE TABLE localidade (
    id     BIGSERIAL     PRIMARY KEY,
    nome   VARCHAR(120)  NOT NULL,
    -- Taxa de entrega cobrada do cliente. Zero é permitido (entrega grátis).
    taxa   NUMERIC(10,2) NOT NULL CHECK (taxa >= 0),
    ativo  BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX uk_localidade_nome ON localidade (LOWER(nome));

-- Localidades de entrega da pastelaria. A peixaria não tem entrega nesta versão.
INSERT INTO localidade (nome, taxa) VALUES
    ('Lagoa de Praia',        3.00),
    ('Barra de Mamanguape',   5.00),
    ('Praia de Campina',      5.00),
    ('Saco',                  6.00),
    ('Tanques',               7.00);
