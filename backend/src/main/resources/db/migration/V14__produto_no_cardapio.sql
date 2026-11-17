-- ============================================================
-- V14 — Mostrar no cardápio online (Etapa 12)
--
-- produto.no_cardapio: se o produto aparece no cardápio do cliente.
-- Não tem relação com "ativo" (que também tira o produto do balcão) nem com
-- "disponivel" (que mostra "esgotado hoje"). Desligado = o cliente não vê,
-- mas a equipe continua vendendo no balcão.
-- Começa LIGADO: todo produto que já existe continua aparecendo.
-- ============================================================

ALTER TABLE produto
    ADD COLUMN no_cardapio BOOLEAN NOT NULL DEFAULT TRUE;
