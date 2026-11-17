-- ============================================================
-- V8 — Venda por peso (peixaria)
--
-- A quantidade deixa de ser um inteiro: a pastelaria continua vendendo
-- unidades inteiras, e a peixaria vende em kg com até 3 casas (1,350 kg).
--
-- O total do item é arredondado para centavos ("metade para cima"), e não
-- mais um produto exato. Por isso a regra antiga (total = preço × quantidade)
-- é trocada pela versão com ROUND.
--
-- Como na V7, as regras antigas são localizadas no catálogo do banco.
-- ============================================================

DO $$
DECLARE
    regra record;
BEGIN
    FOR regra IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'venda_item'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%quantidade%'
    LOOP
        EXECUTE format('ALTER TABLE venda_item DROP CONSTRAINT %I', regra.conname);
    END LOOP;
END $$;

ALTER TABLE venda_item ALTER COLUMN quantidade TYPE NUMERIC(10,3);

ALTER TABLE venda_item ADD CONSTRAINT venda_item_quantidade_check
    CHECK (quantidade > 0 AND quantidade <= 9999.999);

-- ROUND(x, 2) arredonda para centavos com "metade para cima" nos valores positivos.
-- Nome diferente de venda_item_total_check, que o PostgreSQL já deu à regra "total > 0" da V6.
ALTER TABLE venda_item ADD CONSTRAINT venda_item_total_arredondado_check
    CHECK (total = ROUND(preco_unitario * quantidade, 2));
