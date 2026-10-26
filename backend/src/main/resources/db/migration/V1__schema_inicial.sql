-- Schema inicial do MedFlow
-- Identificadores oficiais: CNES (unidade de saúde) e CATMAT (medicamento).

CREATE TABLE ubs (
    id                    BIGSERIAL PRIMARY KEY,
    cnes                  VARCHAR(7)       NOT NULL UNIQUE,
    nome                  VARCHAR(160)     NOT NULL,
    tipo                  VARCHAR(10)      NOT NULL CHECK (tipo IN ('UBS', 'USF')),
    bairro                VARCHAR(120)     NOT NULL,
    endereco              VARCHAR(200)     NOT NULL,
    telefone              VARCHAR(20),
    horario               VARCHAR(80),
    codigo_ibge_municipio VARCHAR(7)       NOT NULL,
    latitude              DOUBLE PRECISION NOT NULL,
    longitude             DOUBLE PRECISION NOT NULL,
    ultima_sincronizacao  TIMESTAMPTZ
);

CREATE TABLE medicamento (
    id                 BIGSERIAL PRIMARY KEY,
    catmat             VARCHAR(20)  NOT NULL UNIQUE,
    nome               VARCHAR(160) NOT NULL,
    concentracao       VARCHAR(60)  NOT NULL,
    forma_farmaceutica VARCHAR(60)  NOT NULL,
    categoria          VARCHAR(60)  NOT NULL,
    rename             BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_medicamento_nome ON medicamento (LOWER(nome));

CREATE TABLE item_estoque (
    id             BIGSERIAL PRIMARY KEY,
    ubs_id         BIGINT      NOT NULL REFERENCES ubs (id),
    medicamento_id BIGINT      NOT NULL REFERENCES medicamento (id),
    status         VARCHAR(12) NOT NULL CHECK (status IN ('DISPONIVEL', 'RESERVADO', 'EM_FALTA')),
    quantidade     INTEGER     NOT NULL DEFAULT 0 CHECK (quantidade >= 0),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_item_estoque UNIQUE (ubs_id, medicamento_id)
);

CREATE INDEX idx_item_estoque_medicamento ON item_estoque (medicamento_id);

-- Lista de desejos do cidadão vinculada à UBS de referência (aviso quando o item chegar)
CREATE TABLE item_lista_desejo (
    id             BIGSERIAL PRIMARY KEY,
    cidadao_cns    VARCHAR(15) NOT NULL, -- Cartão Nacional de Saúde
    ubs_id         BIGINT      NOT NULL REFERENCES ubs (id),
    medicamento_id BIGINT      NOT NULL REFERENCES medicamento (id),
    notificar      BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_item_lista_desejo UNIQUE (cidadao_cns, ubs_id, medicamento_id)
);
