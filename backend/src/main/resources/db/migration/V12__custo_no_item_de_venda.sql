-- ============================================================
-- V12 — Custo estimado guardado em cada item de venda
--
-- Por quê: o relatório de margem usava o custo de HOJE do cadastro. Se o custo
-- do pastel subisse em dezembro, a margem de outubro mudava sozinha. A regra do
-- projeto é que mudar o cadastro nunca altera vendas antigas: o item guarda o
-- custo do momento da venda, como já guardava o nome e o preço.
--
-- Vendas feitas antes desta migração ficam com NULL: não há como saber o custo
-- da época, e o relatório mostra a margem delas como "incompleta" (nunca inventa).
-- ============================================================

ALTER TABLE venda_item
    ADD COLUMN custo_unitario_estimado NUMERIC(10,2) CHECK (custo_unitario_estimado >= 0);
