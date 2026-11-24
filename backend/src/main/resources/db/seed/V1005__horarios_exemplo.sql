-- Dados FICTÍCIOS para desenvolvimento local (perfil "dev").
-- Horário estruturado das UBS do V1000, igual ao texto da coluna ubs.horario.

INSERT INTO horario_funcionamento (ubs_id, dia_semana, abre, fecha)
SELECT u.id, d.dia, h.abre, h.fecha
FROM (VALUES
    -- (cnes, primeiro dia, último dia, abre, fecha); 1 = segunda ... 7 = domingo
    ('9000101', 1, 5, TIME '07:00', TIME '17:00'), -- Seg a Sex · 7h às 17h
    ('9000102', 1, 5, TIME '07:00', TIME '16:00'), -- Seg a Sex · 7h às 16h
    ('9000103', 1, 5, TIME '07:00', TIME '17:00'), -- Seg a Sex · 7h às 17h
    ('9000104', 1, 5, TIME '07:00', TIME '19:00'), -- Seg a Sex · 7h às 19h
    ('9000105', 1, 5, TIME '07:00', TIME '17:00'), -- Seg a Sex · 7h às 17h
    ('9000106', 1, 5, TIME '07:00', TIME '19:00'), -- Seg a Sex · 7h às 19h
    ('9000107', 1, 4, TIME '08:00', TIME '14:00')  -- Seg a Qui · 8h às 14h
) AS h (cnes, primeiro_dia, ultimo_dia, abre, fecha)
JOIN ubs u ON u.cnes = h.cnes
CROSS JOIN LATERAL generate_series(h.primeiro_dia, h.ultimo_dia) AS d (dia);
