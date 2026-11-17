-- Contas de acesso. A senha é guardada só como hash BCrypt. O e-mail fica sempre em minúsculas.
-- Não guardamos o CNS (dado sensível): a lista de desejos passa a ser do usuário autenticado.

CREATE TABLE usuario (
    id         BIGSERIAL PRIMARY KEY,
    nome       VARCHAR(120) NOT NULL,
    email      VARCHAR(160) NOT NULL UNIQUE CHECK (email = LOWER(email)),
    senha_hash VARCHAR(100) NOT NULL,
    papel      VARCHAR(10)  NOT NULL CHECK (papel IN ('CIDADAO', 'GESTOR')),
    criado_em  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
