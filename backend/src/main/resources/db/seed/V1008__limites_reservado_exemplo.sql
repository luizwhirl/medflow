-- Limites de reservado FICTÍCIOS para desenvolvimento local (perfil "dev"). Depende de V1000 e da coluna de V11.
-- Consumo alto pede mais saldo; item raro, menos. Os demais usam o limite global.

UPDATE medicamento SET limite_reservado = 50 WHERE catmat = 'BR0267690'; -- Metformina
UPDATE medicamento SET limite_reservado = 30 WHERE catmat = 'BR0268856'; -- Losartana
UPDATE medicamento SET limite_reservado = 10 WHERE catmat = 'BR0271157'; -- Insulina NPH
