-- ============================================================
-- V13 — Cardápio público (Etapa 12): descrição do produto e dados da loja
--
-- 1) produto.descricao: uma frase curta que o cliente lê no cardápio
--    (ex.: "frango desfiado com Catupiry"). Opcional.
--
-- 2) loja: uma linha por negócio, com o que o cardápio público precisa saber:
--      - aberta: a equipe liga e desliga. Começa FECHADA: um negócio novo
--        nunca aparece "aberto" por acidente;
--      - whatsapp: o número que recebe os pedidos, só dígitos com o código do
--        país (ex.: 5583999998888). O CHECK abaixo recusa qualquer outra coisa,
--        porque esse número vira link (wa.me/...) e não pode levar texto estranho.
-- ============================================================

ALTER TABLE produto
    ADD COLUMN descricao VARCHAR(200);

CREATE TABLE loja (
    negocio       VARCHAR(20)  PRIMARY KEY CHECK (negocio IN ('PASTELARIA', 'PEIXARIA')),
    aberta        BOOLEAN      NOT NULL DEFAULT FALSE,
    whatsapp      VARCHAR(15)  CHECK (whatsapp ~ '^55[0-9]{10,11}$'),
    alterado_em   TIMESTAMPTZ,
    alterado_por  VARCHAR(60)
);

INSERT INTO loja (negocio) VALUES ('PASTELARIA'), ('PEIXARIA');
