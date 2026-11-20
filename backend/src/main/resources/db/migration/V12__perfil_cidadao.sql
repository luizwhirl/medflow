-- Perfil do cidadão.
-- Os dados básicos de autenticação continuam na tabela usuario.
-- A UBS de referência é opcional e pertence ao usuário.

ALTER TABLE usuario
    ADD COLUMN ubs_referencia_id BIGINT
        REFERENCES ubs(id) ON DELETE SET NULL,

    ADD COLUMN bairro VARCHAR(120),

    ADD COLUMN latitude_aproximada DOUBLE PRECISION,

    ADD COLUMN longitude_aproximada DOUBLE PRECISION,

    ADD COLUMN letra_maior BOOLEAN NOT NULL DEFAULT FALSE,

    ADD COLUMN salvar_buscas_recentes BOOLEAN NOT NULL DEFAULT FALSE;

-- A localização aproximada deve respeitar os limites geográficos.
-- Latitude e longitude devem ser informadas juntas ou ambas omitidas.

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_latitude_aproximada
        CHECK (
            latitude_aproximada IS NULL
            OR latitude_aproximada BETWEEN -90 AND 90
        ),

    ADD CONSTRAINT ck_usuario_longitude_aproximada
        CHECK (
            longitude_aproximada IS NULL
            OR longitude_aproximada BETWEEN -180 AND 180
        ),

    ADD CONSTRAINT ck_usuario_localizacao_aproximada
        CHECK (
            (
                latitude_aproximada IS NULL
                AND longitude_aproximada IS NULL
            )
            OR
            (
                latitude_aproximada IS NOT NULL
                AND longitude_aproximada IS NOT NULL
            )
        );

-- Histórico de buscas recentes do cidadão.
-- A posição permite manter a ordem dos termos.
-- O limite de 10 também será validado na API.

CREATE TABLE busca_recente_usuario (
    usuario_id BIGINT NOT NULL
        REFERENCES usuario(id) ON DELETE CASCADE,

    ordem SMALLINT NOT NULL
        CHECK (ordem BETWEEN 0 AND 9),

    termo VARCHAR(120) NOT NULL
        CHECK (LENGTH(BTRIM(termo)) > 0),

    PRIMARY KEY (usuario_id, ordem)
);
