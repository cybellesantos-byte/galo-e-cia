-- ============================================================
-- V3 — Registro de auditoria
--
-- Guarda QUEM fez QUAL ação, QUANDO e em QUAL registro. Serve para responder
-- perguntas como "quem desativou esta conta?" daqui a um ano.
--
-- Esta tabela só recebe linhas: não há endpoint para editar ou apagar.
-- ============================================================

CREATE TABLE registro_auditoria (
    id        BIGSERIAL    PRIMARY KEY,
    quando    TIMESTAMPTZ  NOT NULL,
    usuario   VARCHAR(60)  NOT NULL,
    acao      VARCHAR(40)  NOT NULL,
    alvo      VARCHAR(120),
    detalhes  VARCHAR(500)
);

-- A consulta mais comum é "as ações mais recentes".
CREATE INDEX idx_auditoria_quando ON registro_auditoria (quando);
