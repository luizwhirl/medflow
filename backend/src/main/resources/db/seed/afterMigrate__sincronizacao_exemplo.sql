-- Callback do Flyway: roda a cada subida no perfil "dev", depois das migrações.
-- Mantém a "última sincronização" das UBS fictícias relativa a agora (as datas do V1000 envelhecem):
-- verde até 1 h, amarelo até 24 h e só a USF Riacho Doce atrasada (vermelho).
-- Só toca os CNES fictícios do seed; unidades vindas da BNAFAR não são alteradas.

UPDATE ubs u
SET ultima_sincronizacao = NOW() - s.atraso
FROM (VALUES
    ('9000101', INTERVAL '12 minutes'),
    ('9000102', INTERVAL '35 minutes'),
    ('9000103', INTERVAL '3 hours'),
    ('9000104', INTERVAL '20 minutes'),
    ('9000105', INTERVAL '70 minutes'),
    ('9000106', INTERVAL '8 minutes'),
    ('9000107', INTERVAL '2 days')
) AS s (cnes, atraso)
WHERE u.cnes = s.cnes;
