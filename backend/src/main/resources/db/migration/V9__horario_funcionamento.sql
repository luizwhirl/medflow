-- Horário de funcionamento estruturado por dia da semana (PDS-45).
-- A coluna ubs.horario (texto livre) continua existindo como descrição; a regra de "aberta agora" usa esta tabela.
CREATE TABLE horario_funcionamento (
    id         BIGSERIAL PRIMARY KEY,
    ubs_id     BIGINT   NOT NULL REFERENCES ubs (id) ON DELETE CASCADE,
    -- ISO-8601: 1 = segunda ... 7 = domingo
    dia_semana SMALLINT NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    abre       TIME     NOT NULL,
    -- Se fecha for menor que abre, o turno termina no dia seguinte (ex.: 19:00 às 07:00).
    -- Se fecha for igual a abre, o turno dura 24 h (ex.: 00:00 às 00:00, para unidades que não fecham).
    fecha      TIME     NOT NULL,
    -- O índice desta constraint começa por ubs_id e também atende a busca dos turnos por UBS.
    CONSTRAINT uk_horario_funcionamento UNIQUE (ubs_id, dia_semana, abre)
);
