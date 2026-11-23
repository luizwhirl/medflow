-- Remessa FICTÍCIA para desenvolvimento local (perfil "dev"). Depende de V1000 e das tabelas de V6 (remessas).
-- Completa o V1006: lá há remessas paradas em PEDIDO, SEPARADO_CAF, EM_TRANSPORTE e DISPONIVEL; esta fica em
-- RECEBIDO (chegou à UBS e ainda não foi para a prateleira), para a tela de rastreio ter todas as etapas.

WITH nova AS (
    INSERT INTO remessa (ubs_id, medicamento_id, quantidade, previsao_chegada, criado_em)
    SELECT u.id, m.id, 60, CURRENT_DATE, NOW() - INTERVAL '3 days'
    FROM ubs u, medicamento m
    WHERE u.cnes = '9000102' AND m.catmat = 'BR0269919' -- USF Village Campestre II, Amoxicilina
    RETURNING id, criado_em
)
INSERT INTO remessa_evento (remessa_id, etapa, ocorrido_em)
SELECT n.id, e.etapa, n.criado_em + (e.ordem - 1) * INTERVAL '16 hours'
FROM nova n
CROSS JOIN (VALUES (1, 'PEDIDO'), (2, 'SEPARADO_CAF'), (3, 'EM_TRANSPORTE'), (4, 'RECEBIDO')) AS e (ordem, etapa);
