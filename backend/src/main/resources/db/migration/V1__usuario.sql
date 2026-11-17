-- ============================================================
-- V1 — Tabela de usuários da área administrativa
--
-- Regra de ouro do Flyway: depois de aplicada, uma migração NUNCA é editada.
-- Toda mudança futura vira um novo arquivo (V2__..., V3__...).
-- ============================================================

CREATE TABLE usuario (
    id               BIGSERIAL    PRIMARY KEY,
    username         VARCHAR(60)  NOT NULL,
    nome             VARCHAR(120) NOT NULL,
    -- Hash BCrypt tem 60 caracteres. A senha em texto puro nunca é gravada.
    senha_hash       VARCHAR(100) NOT NULL,
    -- ADMIN: tudo. EQUIPE: operação do dia a dia.
    -- O CHECK garante no BANCO que só esses dois valores existem, mesmo que
    -- algum código novo esqueça a validação.
    papel            VARCHAR(20)  NOT NULL CHECK (papel IN ('ADMIN', 'EQUIPE')),
    ativo            BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ultimo_login_em  TIMESTAMPTZ
);

-- Login não diferencia maiúsculas de minúsculas: "Admin" e "admin" são o mesmo usuário.
-- O índice é sobre LOWER(username), e não sobre a coluna, para que a regra valha
-- mesmo quando alguém cadastrar "ADMIN" e outro "admin".
CREATE UNIQUE INDEX uk_usuario_username ON usuario (LOWER(username));
