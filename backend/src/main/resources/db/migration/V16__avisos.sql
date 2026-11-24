-- PDS-35: avisos por dispositivo (sem conta de cidadão).
-- O identificador é o UUID salvo no navegador e enviado em X-Dispositivo-Id.

CREATE TABLE aviso (
    id BIGSERIAL PRIMARY KEY,

    dispositivo_id UUID NOT NULL,

    tipo VARCHAR(40) NOT NULL
        CHECK (tipo IN (
            'MEDICAMENTO_DISPONIVEL',
            'UBS_DADOS_ATRASADOS'
        )),

    titulo VARCHAR(160) NOT NULL,
    texto TEXT NOT NULL,

    lido BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Impede repetir o mesmo evento para um dispositivo.
    chave_evento VARCHAR(200) NOT NULL,

    CONSTRAINT uk_aviso_dispositivo_evento
        UNIQUE (dispositivo_id, chave_evento)
);

-- Lista dos 50 avisos mais recentes do dispositivo.
CREATE INDEX idx_aviso_dispositivo_criado_em
    ON aviso (dispositivo_id, criado_em DESC, id DESC);

-- Contagem e atualização dos avisos não lidos.
CREATE INDEX idx_aviso_dispositivo_nao_lido
    ON aviso (dispositivo_id)
    WHERE lido = FALSE;
