-- Dados FICTÍCIOS para desenvolvimento local (perfil "dev"). Depende de V1000.
-- Mais 7 medicamentos da RENAME (17 no total), presentes nas 7 UBS. CATMAT apenas ilustrativos.
-- As categorias seguem os nomes usados pela sincronização (sincronizacao/CategoriaMedicamento.kt).

INSERT INTO medicamento (catmat, nome, concentracao, forma_farmaceutica, categoria, rename) VALUES
    ('BR0272301', 'Omeprazol',     '20 mg',  'cápsula',    'Estômago',     TRUE),
    ('BR0272302', 'Enalapril',     '10 mg',  'comprimido', 'Hipertensão',  TRUE),
    ('BR0272303', 'Anlodipino',    '5 mg',   'comprimido', 'Hipertensão',  TRUE),
    ('BR0272304', 'Atorvastatina', '20 mg',  'comprimido', 'Colesterol',   TRUE),
    ('BR0272305', 'Azitromicina',  '500 mg', 'comprimido', 'Antibióticos', TRUE),
    ('BR0272306', 'Paracetamol',   '500 mg', 'comprimido', 'Dor e febre',  TRUE),
    ('BR0272307', 'Beclometasona', '50 mcg', 'aerossol',   'Asma',         TRUE);

-- Estoque: quantidade por UBS (9000101 a 9000107). O status sai da regra de app.estoque.limite-reservado (20).
INSERT INTO item_estoque (ubs_id, medicamento_id, status, quantidade)
SELECT u.id, m.id,
       CASE WHEN e.quantidade = 0 THEN 'EM_FALTA' WHEN e.quantidade <= 20 THEN 'RESERVADO' ELSE 'DISPONIVEL' END,
       e.quantidade
FROM (VALUES
    --          101   102   103   104   105   106   107
    ('BR0272301', ARRAY[140,   0, 260,  90,  15, 180,   0]), -- Omeprazol
    ('BR0272302', ARRAY[200,  75,   0, 130,  18, 240,  40]), -- Enalapril
    ('BR0272303', ARRAY[  0, 150, 110,   7, 220,   0,  22]), -- Anlodipino
    ('BR0272304', ARRAY[ 60,   0,  14, 170,  95, 120,   0]), -- Atorvastatina
    ('BR0272305', ARRAY[ 30,  12,  45,   0,   0,  65,   5]), -- Azitromicina
    ('BR0272306', ARRAY[380, 290,   0, 210, 330, 450,  90]), -- Paracetamol
    ('BR0272307', ARRAY[ 10,  24,   0,  16,  28,   0,   0])  -- Beclometasona
) AS q (catmat, por_ubs)
CROSS JOIN LATERAL unnest(q.por_ubs) WITH ORDINALITY AS e (quantidade, ordem)
JOIN medicamento m ON m.catmat = q.catmat
JOIN ubs u ON u.cnes = '900010' || e.ordem;

-- Lotes vencendo em até 60 dias (a soma bate com a quantidade do item)
INSERT INTO lote_estoque (item_estoque_id, numero_lote, validade, quantidade)
SELECT i.id, l.numero_lote, CURRENT_DATE + l.dias, l.quantidade
FROM (VALUES
    ('9000101', 'BR0272301', 'OME-2601',  18,  50), ('9000101', 'BR0272301', 'OME-2608', 270,  90),
    ('9000102', 'BR0272303', 'ANL-2603',   9, 150),
    ('9000104', 'BR0272304', 'ATO-2601',  55,  70), ('9000104', 'BR0272304', 'ATO-2610', 300, 100),
    ('9000105', 'BR0272306', 'PAR-2603',  35, 130), ('9000105', 'BR0272306', 'PAR-2612', 450, 200),
    ('9000106', 'BR0272302', 'ENA-2602',  40, 100), ('9000106', 'BR0272302', 'ENA-2611', 390, 140),
    ('9000106', 'BR0272305', 'AZI-2602',  22,  65)
) AS l (cnes, catmat, numero_lote, dias, quantidade)
JOIN ubs u ON u.cnes = l.cnes
JOIN medicamento m ON m.catmat = l.catmat
JOIN item_estoque i ON i.ubs_id = u.id AND i.medicamento_id = m.id;

-- Demais itens com saldo e ainda sem lote: um lote único com validade longa
INSERT INTO lote_estoque (item_estoque_id, numero_lote, validade, quantidade)
SELECT i.id, 'LT-' || m.catmat, CURRENT_DATE + 180 + (i.id % 7)::INTEGER * 60, i.quantidade
FROM item_estoque i
JOIN medicamento m ON m.id = i.medicamento_id
WHERE i.quantidade > 0
  AND NOT EXISTS (SELECT 1 FROM lote_estoque l WHERE l.item_estoque_id = i.id);
