-- Remessas FICTÍCIAS para desenvolvimento local (perfil "dev"). Depende de V1000 e das tabelas de V6.
-- "etapas" é quantas etapas já aconteceram (1 = só PEDIDO ... 5 = chegou e está DISPONIVEL).

WITH dados (cnes, catmat, quantidade, previsao_dias, dias_atras, etapas) AS (
    VALUES
        ('9000101', 'BR0267690', 300, 2,  3, 3), -- Metformina: em transporte
        ('9000105', 'BR0271157',  40, 5,  2, 2), -- Insulina NPH: separada na CAF
        ('9000103', 'BR0267515', 200, 9,  2, 1), -- Captopril: pedido feito
        ('9000106', 'BR0267612', 150, 1,  4, 3), -- Glibenclamida: em transporte
        ('9000107', 'BR0268338',  30, 12, 2, 1), -- Salbutamol: pedido feito
        ('9000104', 'BR0268856', 120, -1, 6, 5)  -- Losartana: já chegou (concluída)
),
novas AS (
    INSERT INTO remessa (ubs_id, medicamento_id, quantidade, previsao_chegada, criado_em)
    SELECT u.id, m.id, d.quantidade, CURRENT_DATE + d.previsao_dias, NOW() - d.dias_atras * INTERVAL '1 day'
    FROM dados d
    JOIN ubs u ON u.cnes = d.cnes
    JOIN medicamento m ON m.catmat = d.catmat
    RETURNING id, ubs_id, medicamento_id, criado_em
)
INSERT INTO remessa_evento (remessa_id, etapa, ocorrido_em)
SELECT n.id, e.etapa, n.criado_em + (e.ordem - 1) * INTERVAL '8 hours'
FROM novas n
JOIN ubs u ON u.id = n.ubs_id
JOIN medicamento m ON m.id = n.medicamento_id
JOIN dados d ON d.cnes = u.cnes AND d.catmat = m.catmat
JOIN (VALUES (1, 'PEDIDO'), (2, 'SEPARADO_CAF'), (3, 'EM_TRANSPORTE'), (4, 'RECEBIDO'), (5, 'DISPONIVEL'))
    AS e (ordem, etapa) ON e.ordem <= d.etapas;
