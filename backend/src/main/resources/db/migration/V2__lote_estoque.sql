-- Lotes de cada posição de estoque (validade por lote, usada no painel da gestão).
-- item_estoque.quantidade continua sendo o total e deve ser a soma dos lotes.

CREATE TABLE lote_estoque (
    id              BIGSERIAL PRIMARY KEY,
    item_estoque_id BIGINT      NOT NULL REFERENCES item_estoque (id) ON DELETE CASCADE,
    numero_lote     VARCHAR(40) NOT NULL,
    validade        DATE        NOT NULL,
    quantidade      INTEGER     NOT NULL CHECK (quantidade >= 0),
    CONSTRAINT uk_lote_estoque UNIQUE (item_estoque_id, numero_lote)
);

CREATE INDEX idx_lote_estoque_validade ON lote_estoque (validade);
