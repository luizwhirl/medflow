-- PDS-49: quantas vezes cada medicamento foi procurado no app, por dia, para o "mais procurados" do painel.
-- Só a contagem agregada (LGPD): sem o texto digitado ou falado, sem aparelho e sem horário.
-- As linhas com mais de 12 meses são apagadas todo dia (app.buscas.retencao-meses).

CREATE TABLE contagem_busca (
    id BIGSERIAL PRIMARY KEY,
    dia DATE NOT NULL,
    medicamento_id BIGINT NOT NULL REFERENCES medicamento (id) ON DELETE CASCADE,
    -- UBS de referência de quem procurou; NULL quando a pessoa ainda não escolheu uma.
    ubs_id BIGINT REFERENCES ubs (id) ON DELETE CASCADE,
    total INTEGER NOT NULL CHECK (total > 0)
);

-- Uma linha por dia, medicamento e UBS (a sem UBS também é uma só); o registro soma 1 com ON CONFLICT.
CREATE UNIQUE INDEX uk_contagem_busca
    ON contagem_busca (dia, medicamento_id, COALESCE(ubs_id, 0));

-- "Mais procurados" dos últimos dias, na UBS ou na rede, e a limpeza das linhas antigas.
CREATE INDEX idx_contagem_busca_ubs_dia ON contagem_busca (ubs_id, dia);
CREATE INDEX idx_contagem_busca_dia ON contagem_busca (dia);
