-- Lotes FICTÍCIOS para desenvolvimento local (perfil "dev"). Depende de V1000 e da tabela de V2.
-- A soma dos lotes de cada item é igual a item_estoque.quantidade.

-- Lotes que vencem em até 60 dias (alimentam a aba "Validade" do painel)
INSERT INTO lote_estoque (item_estoque_id, numero_lote, validade, quantidade)
SELECT i.id, l.numero_lote, CURRENT_DATE + l.dias, l.quantidade
FROM (VALUES
    ('9000106', 'BR0269919', 'AMX-2601', 42,  120), ('9000106', 'BR0269919', 'AMX-2605', 210, 70),
    ('9000101', 'BR0268856', 'LOS-2602', 55,  100), ('9000101', 'BR0268856', 'LOS-2610', 365, 160),
    ('9000101', 'BR0267543', 'DIP-2603', 58,   90), ('9000101', 'BR0267543', 'DIP-2611', 300, 210)
) AS l (cnes, catmat, numero_lote, dias, quantidade)
JOIN ubs u ON u.cnes = l.cnes
JOIN medicamento m ON m.catmat = l.catmat
JOIN item_estoque i ON i.ubs_id = u.id AND i.medicamento_id = m.id;

-- Demais itens com saldo: um lote único com validade longa
INSERT INTO lote_estoque (item_estoque_id, numero_lote, validade, quantidade)
SELECT i.id, 'LT-' || m.catmat, CURRENT_DATE + 180, i.quantidade
FROM item_estoque i
JOIN medicamento m ON m.id = i.medicamento_id
WHERE i.quantidade > 0
  AND NOT EXISTS (SELECT 1 FROM lote_estoque l WHERE l.item_estoque_id = i.id);
