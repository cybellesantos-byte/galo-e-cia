-- ============================================================
-- V7 — Cartão como forma de pagamento
--
-- Cartão segue a mesma regra do Pix: não passa pela gaveta e nasce pendente,
-- até a equipe confirmar que o valor caiu na conta.
--
-- Esta migração NÃO edita a V6 (que pode já estar aplicada). Ela troca as
-- regras antigas por novas. Os nomes das regras antigas são descobertos no
-- catálogo do banco, porque o PostgreSQL os gera automaticamente.
-- ============================================================

DO $$
DECLARE
    regra record;
BEGIN
    FOR regra IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'venda'::regclass
          AND contype = 'c'
          AND (pg_get_constraintdef(oid) LIKE '%forma_pagamento%'
               OR pg_get_constraintdef(oid) LIKE '%valor_recebido%')
    LOOP
        EXECUTE format('ALTER TABLE venda DROP CONSTRAINT %I', regra.conname);
    END LOOP;
END $$;

ALTER TABLE venda ADD CONSTRAINT venda_forma_pagamento_check
    CHECK (forma_pagamento IN ('PIX', 'CARTAO', 'DINHEIRO'));

-- Dinheiro: traz valor recebido, troco e já nasce paga.
-- Pix e cartão: não trazem valor recebido nem troco.
ALTER TABLE venda ADD CONSTRAINT venda_pagamento_coerente_check
    CHECK (
        (forma_pagamento = 'DINHEIRO' AND valor_recebido IS NOT NULL AND troco = valor_recebido - total
            AND troco >= 0 AND situacao_pagamento = 'PAGO')
        OR
        (forma_pagamento IN ('PIX', 'CARTAO') AND valor_recebido IS NULL AND troco IS NULL)
    );
