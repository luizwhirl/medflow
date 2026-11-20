-- Dados FICTÍCIOS para desenvolvimento local (perfil "dev"). Depende de V1000 e V1001.
-- Completa o estoque: todos os medicamentos passam a ter posição nas 7 UBS (10 x 7 = 70 itens).
-- Status segue a regra de app.estoque.limite-reservado (20): 0 = EM_FALTA, 1 a 20 = RESERVADO, acima = DISPONIVEL.

-- Estoque: (cnes, catmat, status, quantidade). Itens que já existem no V1000 são mantidos.
INSERT INTO item_estoque (ubs_id, medicamento_id, status, quantidade)
SELECT u.id, m.id, e.status, e.quantidade
FROM (VALUES
    -- UBS Benedito Bentes I (unidade do painel da gestão)
    ('9000101', 'BR0267620', 'DISPONIVEL', 120), ('9000101', 'BR0267515', 'EM_FALTA',     0),
    ('9000101', 'BR0268280', 'DISPONIVEL',  85), ('9000101', 'BR0269919', 'RESERVADO',   16),
    ('9000101', 'BR0268338', 'DISPONIVEL',  22),
    -- USF Village Campestre II
    ('9000102', 'BR0267612', 'DISPONIVEL',  95), ('9000102', 'BR0271157', 'EM_FALTA',     0),
    ('9000102', 'BR0267620', 'DISPONIVEL', 160), ('9000102', 'BR0267515', 'RESERVADO',   12),
    ('9000102', 'BR0268280', 'EM_FALTA',     0), ('9000102', 'BR0269919', 'DISPONIVEL',  75),
    ('9000102', 'BR0267543', 'DISPONIVEL', 240), ('9000102', 'BR0268338', 'EM_FALTA',     0),
    -- UBS Clima Bom
    ('9000103', 'BR0267612', 'RESERVADO',   18), ('9000103', 'BR0268856', 'EM_FALTA',     0),
    ('9000103', 'BR0267620', 'DISPONIVEL', 200), ('9000103', 'BR0268280', 'DISPONIVEL', 130),
    ('9000103', 'BR0269919', 'EM_FALTA',     0), ('9000103', 'BR0267543', 'DISPONIVEL', 410),
    ('9000103', 'BR0268338', 'RESERVADO',    9),
    -- UBS Tabuleiro do Martins
    ('9000104', 'BR0267612', 'DISPONIVEL', 160), ('9000104', 'BR0271157', 'DISPONIVEL',  26),
    ('9000104', 'BR0267620', 'EM_FALTA',     0), ('9000104', 'BR0267515', 'DISPONIVEL', 180),
    ('9000104', 'BR0268280', 'DISPONIVEL', 240), ('9000104', 'BR0269919', 'RESERVADO',    3),
    ('9000104', 'BR0267543', 'EM_FALTA',     0), ('9000104', 'BR0268338', 'DISPONIVEL',  35),
    -- UBS Santos Dumont
    ('9000105', 'BR0267690', 'DISPONIVEL', 140), ('9000105', 'BR0267612', 'EM_FALTA',     0),
    ('9000105', 'BR0268856', 'DISPONIVEL', 300), ('9000105', 'BR0267620', 'RESERVADO',   15),
    ('9000105', 'BR0267515', 'DISPONIVEL',  90), ('9000105', 'BR0268280', 'EM_FALTA',     0),
    ('9000105', 'BR0269919', 'DISPONIVEL', 110), ('9000105', 'BR0267543', 'DISPONIVEL', 260),
    ('9000105', 'BR0268338', 'RESERVADO',   20),
    -- UBS Cidade Universitária
    ('9000106', 'BR0271157', 'DISPONIVEL',  40), ('9000106', 'BR0268856', 'DISPONIVEL', 220),
    ('9000106', 'BR0267620', 'DISPONIVEL', 140), ('9000106', 'BR0267515', 'EM_FALTA',     0),
    ('9000106', 'BR0268280', 'DISPONIVEL', 175), ('9000106', 'BR0267543', 'DISPONIVEL', 350),
    ('9000106', 'BR0268338', 'RESERVADO',   11),
    -- USF Riacho Doce (zona rural, sincronização atrasada): estoque baixo
    ('9000107', 'BR0267612', 'DISPONIVEL',  30), ('9000107', 'BR0271157', 'EM_FALTA',     0),
    ('9000107', 'BR0268856', 'RESERVADO',    8), ('9000107', 'BR0267620', 'EM_FALTA',     0),
    ('9000107', 'BR0267515', 'DISPONIVEL',  25), ('9000107', 'BR0268280', 'EM_FALTA',     0),
    ('9000107', 'BR0269919', 'RESERVADO',   12), ('9000107', 'BR0267543', 'DISPONIVEL',  60)
) AS e (cnes, catmat, status, quantidade)
JOIN ubs u ON u.cnes = e.cnes
JOIN medicamento m ON m.catmat = e.catmat
ON CONFLICT (ubs_id, medicamento_id) DO NOTHING;

-- Lotes vencendo em até 60 dias, espalhados pelas unidades (a soma bate com a quantidade do item)
INSERT INTO lote_estoque (item_estoque_id, numero_lote, validade, quantidade)
SELECT i.id, l.numero_lote, CURRENT_DATE + l.dias, l.quantidade
FROM (VALUES
    ('9000101', 'BR0267620', 'HCT-2601',  25,  40), ('9000101', 'BR0267620', 'HCT-2612', 400,  80),
    ('9000101', 'BR0268280', 'SIN-2602',  12,  30), ('9000101', 'BR0268280', 'SIN-2609', 240,  55),
    ('9000102', 'BR0269919', 'AMX-2603',  20,  75),
    ('9000103', 'BR0268280', 'SIN-2604',  33,  50), ('9000103', 'BR0268280', 'SIN-2611', 300,  80),
    ('9000104', 'BR0271157', 'INS-2601',   7,  10), ('9000104', 'BR0271157', 'INS-2606', 150,  16),
    ('9000105', 'BR0267690', 'MET-2602',  45,  60), ('9000105', 'BR0267690', 'MET-2610', 330,  80),
    ('9000105', 'BR0267543', 'DIP-2604',  52, 100), ('9000105', 'BR0267543', 'DIP-2612', 420, 160),
    ('9000106', 'BR0268280', 'SIN-2605',  28,  75), ('9000106', 'BR0268280', 'SIN-2613', 360, 100),
    ('9000107', 'BR0269919', 'AMX-2602',  15,  12)
) AS l (cnes, catmat, numero_lote, dias, quantidade)
JOIN ubs u ON u.cnes = l.cnes
JOIN medicamento m ON m.catmat = l.catmat
JOIN item_estoque i ON i.ubs_id = u.id AND i.medicamento_id = m.id;

-- Demais itens com saldo e ainda sem lote: um lote único com validade longa (entre 6 meses e 1 ano e meio)
INSERT INTO lote_estoque (item_estoque_id, numero_lote, validade, quantidade)
SELECT i.id, 'LT-' || m.catmat, CURRENT_DATE + 180 + (i.id % 7)::INTEGER * 60, i.quantidade
FROM item_estoque i
JOIN medicamento m ON m.id = i.medicamento_id
WHERE i.quantidade > 0
  AND NOT EXISTS (SELECT 1 FROM lote_estoque l WHERE l.item_estoque_id = i.id);
