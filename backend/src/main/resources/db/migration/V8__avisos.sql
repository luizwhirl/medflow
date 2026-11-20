-- PDS-35: avisos do cidadão.
-- Os avisos pertencem ao usuário autenticado e são removidos
-- automaticamente quando a conta é excluída.

CREATE TABLE aviso (
    id BIGSERIAL PRIMARY KEY,

    usuario_id BIGINT NOT NULL,

    tipo VARCHAR(40) NOT NULL
        CHECK (tipo IN (
            'MEDICAMENTO_DISPONIVEL',
            'UBS_DADOS_ATRASADOS'
        )),

    titulo VARCHAR(160) NOT NULL,
    texto TEXT NOT NULL,

    lido BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Identifica o evento que originou o aviso.
    -- Evita gerar o mesmo aviso mais de uma vez para o usuário.
    chave_evento VARCHAR(200) NOT NULL,

    CONSTRAINT fk_aviso_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario (id)
        ON DELETE CASCADE,

    CONSTRAINT uk_aviso_usuario_evento
        UNIQUE (usuario_id, chave_evento)
);

-- Otimiza a listagem dos avisos mais recentes de um cidadão.
CREATE INDEX idx_aviso_usuario_criado_em
    ON aviso (usuario_id, criado_em DESC, id DESC);

-- Otimiza consultas e atualizações de avisos não lidos.
CREATE INDEX idx_aviso_usuario_nao_lido
    ON aviso (usuario_id)
    WHERE lido = FALSE;
