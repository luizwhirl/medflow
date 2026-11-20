-- Dados FICTÍCIOS para desenvolvimento local (perfil "dev"). Depende de V1000 e V1100.
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

-- Estoque: (cnes, catmat, quantidade). O status sai da regra global de app.estoque.limite-reservado (20):
-- 0 = EM_FALTA, 1 a 20 = RESERVADO, acima = DISPONIVEL. Vale porque estes 7 medicamentos não têm limite de
-- reservado próprio; se algum ganhar um (limite por medicamento, PDS-37), atualize o CASE abaixo.
INSERT INTO item_estoque (ubs_id, medicamento_id, status, quantidade)
SELECT u.id, m.id,
       CASE WHEN e.quantidade = 0 THEN 'EM_FALTA' WHEN e.quantidade <= 20 THEN 'RESERVADO' ELSE 'DISPONIVEL' END,
       e.quantidade
FROM (VALUES
    -- UBS Benedito Bentes I
    ('9000101', 'BR0272301', 140), ('9000101', 'BR0272302', 200), ('9000101', 'BR0272303',   0), ('9000101', 'BR0272304',  60),
    ('9000101', 'BR0272305',  30), ('9000101', 'BR0272306', 380), ('9000101', 'BR0272307',  10),
    -- USF Village Campestre II
    ('9000102', 'BR0272301',   0), ('9000102', 'BR0272302',  75), ('9000102', 'BR0272303', 150), ('9000102', 'BR0272304',   0),
    ('9000102', 'BR0272305',  12), ('9000102', 'BR0272306', 290), ('9000102', 'BR0272307',  24),
    -- UBS Clima Bom
    ('9000103', 'BR0272301', 260), ('9000103', 'BR0272302',   0), ('9000103', 'BR0272303', 110), ('9000103', 'BR0272304',  14),
    ('9000103', 'BR0272305',  45), ('9000103', 'BR0272306',   0), ('9000103', 'BR0272307',   0),
    -- UBS Tabuleiro do Martins
    ('9000104', 'BR0272301',  90), ('9000104', 'BR0272302', 130), ('9000104', 'BR0272303',   7), ('9000104', 'BR0272304', 170),
    ('9000104', 'BR0272305',   0), ('9000104', 'BR0272306', 210), ('9000104', 'BR0272307',  16),
    -- UBS Santos Dumont
    ('9000105', 'BR0272301',  15), ('9000105', 'BR0272302',  18), ('9000105', 'BR0272303', 220), ('9000105', 'BR0272304',  95),
    ('9000105', 'BR0272305',   0), ('9000105', 'BR0272306', 330), ('9000105', 'BR0272307',  28),
    -- UBS Cidade Universitária
    ('9000106', 'BR0272301', 180), ('9000106', 'BR0272302', 240), ('9000106', 'BR0272303',   0), ('9000106', 'BR0272304', 120),
    ('9000106', 'BR0272305',  65), ('9000106', 'BR0272306', 450), ('9000106', 'BR0272307',   0),
    -- USF Riacho Doce
    ('9000107', 'BR0272301',   0), ('9000107', 'BR0272302',  40), ('9000107', 'BR0272303',  22), ('9000107', 'BR0272304',   0),
    ('9000107', 'BR0272305',   5), ('9000107', 'BR0272306',  90), ('9000107', 'BR0272307',   0)
) AS e (cnes, catmat, quantidade)
JOIN ubs u ON u.cnes = e.cnes
JOIN medicamento m ON m.catmat = e.catmat;

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
