-- Movimentações FICTÍCIAS para desenvolvimento local (perfil "dev"). Depende de V1000 e da tabela de V7.
-- Nos itens abaixo, a soma das movimentações é igual a item_estoque.quantidade.

INSERT INTO movimentacao_estoque (item_estoque_id, tipo, quantidade, ocorrido_em)
SELECT i.id, m.tipo, m.quantidade, NOW() - m.dias_atras * INTERVAL '1 day'
FROM (VALUES
    ('9000103', 'BR0267690', 'ENTRADA',      400, 25), ('9000103', 'BR0267690', 'DISPENSACAO',  -60, 12),
    ('9000103', 'BR0267690', 'DISPENSACAO',  -45,  8), ('9000103', 'BR0267690', 'DISPENSACAO',  -75,  3),
    ('9000104', 'BR0267690', 'ENTRADA',      200, 20), ('9000104', 'BR0267690', 'DISPENSACAO',  -70, 10),
    ('9000104', 'BR0267690', 'DISPENSACAO',  -45,  2),
    ('9000106', 'BR0267690', 'ENTRADA',      500, 28), ('9000106', 'BR0267690', 'DISPENSACAO', -120, 15),
    ('9000106', 'BR0267690', 'DISPENSACAO',  -70,  4),
    ('9000101', 'BR0268856', 'ENTRADA',      300, 18), ('9000101', 'BR0268856', 'DISPENSACAO',  -30,  9),
    ('9000101', 'BR0268856', 'AJUSTE',        10,  5), ('9000101', 'BR0268856', 'DISPENSACAO',  -20,  1),
    ('9000101', 'BR0267543', 'ENTRADA',      500, 22), ('9000101', 'BR0267543', 'DISPENSACAO', -200,  6),
    ('9000106', 'BR0269919', 'ENTRADA',      250, 14), ('9000106', 'BR0269919', 'DISPENSACAO',  -60,  2),
    ('9000101', 'BR0267612', 'ENTRADA',      200, 16), ('9000101', 'BR0267612', 'DISPENSACAO',  -60,  7)
) AS m (cnes, catmat, tipo, quantidade, dias_atras)
JOIN ubs u ON u.cnes = m.cnes
JOIN medicamento med ON med.catmat = m.catmat
JOIN item_estoque i ON i.ubs_id = u.id AND i.medicamento_id = med.id;
