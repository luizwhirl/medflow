-- Remessas da CAF para as UBS e o rastreamento por etapa.
-- A BNAFAR pública não informa remessas em trânsito: os dados são cadastrados (em dev, vêm do seed).

CREATE TABLE remessa (
    id               BIGSERIAL PRIMARY KEY,
    ubs_id           BIGINT      NOT NULL REFERENCES ubs (id),
    medicamento_id   BIGINT      NOT NULL REFERENCES medicamento (id),
    quantidade       INTEGER     NOT NULL CHECK (quantidade > 0),
    previsao_chegada DATE        NOT NULL,
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_remessa_ubs ON remessa (ubs_id, previsao_chegada);
CREATE INDEX idx_remessa_medicamento ON remessa (medicamento_id);

-- Cada etapa acontece uma vez por remessa; a etapa atual é a mais avançada registrada.
CREATE TABLE remessa_evento (
    id          BIGSERIAL PRIMARY KEY,
    remessa_id  BIGINT      NOT NULL REFERENCES remessa (id) ON DELETE CASCADE,
    etapa       VARCHAR(15) NOT NULL CHECK (etapa IN ('PEDIDO', 'SEPARADO_CAF', 'EM_TRANSPORTE', 'RECEBIDO', 'DISPONIVEL')),
    ocorrido_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_remessa_evento UNIQUE (remessa_id, etapa)
);
