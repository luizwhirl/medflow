-- Callback do Flyway: roda a cada subida no perfil "dev" (sem o "demo"), depois das migrações.
-- Bancos de dev criados antes do PDS-21 ainda têm as UBS fictícias do seed (db/seed, hoje só no perfil demo).
-- Elas não existem de verdade, então saem da lista e do mapa do cidadão. Para limpar de vez: scripts/db-reset.
-- Confere CNES e nome juntos: os CNES 9000xxx também existem de verdade (ex.: 9000755 em Maceió).

UPDATE ubs u
SET localizacao_origem = 'INVALIDA'
FROM (VALUES
    ('9000101', 'UBS Benedito Bentes I'),
    ('9000102', 'USF Village Campestre II'),
    ('9000103', 'UBS Clima Bom'),
    ('9000104', 'UBS Tabuleiro do Martins'),
    ('9000105', 'UBS Santos Dumont'),
    ('9000106', 'UBS Cidade Universitária'),
    ('9000107', 'USF Riacho Doce (zona rural)')
) AS f (cnes, nome)
WHERE u.cnes = f.cnes
  AND u.nome = f.nome
  AND u.localizacao_origem <> 'INVALIDA';
