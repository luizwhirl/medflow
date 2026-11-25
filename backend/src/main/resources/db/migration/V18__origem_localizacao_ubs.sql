-- PDS-21: de onde vem a coordenada da UBS no mapa.
-- A BNAFAR (que repete o CNES) às vezes manda a coordenada fora do município da unidade (ex.: no mar, a 77 km de
-- Maceió). A sincronização confere cada unidade com a malha do IBGE e, se a coordenada cair fora, procura a unidade
-- no OpenStreetMap (LUGAR) ou, sem ela, o centro do bairro (BAIRRO, aproximada). O gestor pode corrigir o pino
-- (MANUAL), e a correção vale acima da fonte nas próximas sincronizações.
-- INVALIDA: nada confiável encontrado; a unidade fica no banco (o painel da gestão conta o estoque), mas não aparece
-- para o cidadão até alguém corrigir o pino.

ALTER TABLE ubs
    ADD COLUMN localizacao_origem VARCHAR(10) NOT NULL DEFAULT 'FONTE'
        CHECK (localizacao_origem IN ('FONTE', 'LUGAR', 'BAIRRO', 'MANUAL', 'INVALIDA'));
