-- ============================================================
-- V2 — Data da última troca de senha
--
-- Usada para derrubar as sessões abertas ANTES da troca: quem entrou com a
-- senha antiga precisa entrar de novo com a nova.
--
-- Coluna nova e nula: nada existente muda. Quem nunca trocou a senha fica NULL.
-- (Migração nova, e não edição da V1: a V1 já foi aplicada no banco real.)
-- ============================================================

ALTER TABLE usuario ADD COLUMN senha_alterada_em TIMESTAMPTZ;
