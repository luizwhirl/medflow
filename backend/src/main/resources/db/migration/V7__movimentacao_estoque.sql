-- Histórico de movimentações de cada posição de estoque.
-- A quantidade tem sinal: ENTRADA soma (> 0), DISPENSACAO subtrai (< 0) e AJUSTE corrige para mais ou menos (<> 0).
-- A UBS e o medicamento vêm do item_estoque, então os índices partem dele.
-- É histórico: a chave estrangeira é RESTRICT, então um item com movimentações não pode ser apagado.

CREATE TABLE movimentacao_estoque (
    id              BIGSERIAL PRIMARY KEY,
    item_estoque_id BIGINT      NOT NULL REFERENCES item_estoque (id) ON DELETE RESTRICT,
    tipo            VARCHAR(12) NOT NULL CHECK (tipo IN ('ENTRADA', 'DISPENSACAO', 'AJUSTE')),
    quantidade      INTEGER     NOT NULL,
    ocorrido_em     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_movimentacao_sinal CHECK (
        (tipo = 'ENTRADA' AND quantidade > 0)
        OR (tipo = 'DISPENSACAO' AND quantidade < 0)
        OR (tipo = 'AJUSTE' AND quantidade <> 0)
    )
);

CREATE INDEX idx_movimentacao_item_data ON movimentacao_estoque (item_estoque_id, ocorrido_em DESC);
CREATE INDEX idx_movimentacao_data ON movimentacao_estoque (ocorrido_em);
