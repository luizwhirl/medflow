-- O app não tem login para o cidadão: a lista de desejos passa a ser só do aparelho,
-- identificado por um UUID gerado no navegador e enviado no cabeçalho X-Dispositivo-Id.
--
-- Funciona com ou sem a V12_1__avisos_por_dispositivo.sql do PR de avisos (#35), que
-- antecipava parte desta mudança: os passos abaixo não falham se ela já tiver rodado.

-- Constraints que a V12_1 cria para o modelo "conta ou aparelho".
ALTER TABLE item_lista_desejo
    DROP CONSTRAINT IF EXISTS ck_item_lista_desejo_dono,
    DROP CONSTRAINT IF EXISTS uk_item_lista_desejo_dispositivo;

ALTER TABLE item_lista_desejo
    ADD COLUMN IF NOT EXISTS dispositivo_id UUID;

-- Os itens sem aparelho são os de contas, que só existem em desenvolvimento
-- (contas de exemplo) e não têm um aparelho para onde migrar.
DELETE FROM item_lista_desejo WHERE dispositivo_id IS NULL;

-- Remover a coluna também remove a uk_item_lista_desejo da V5 e a FK com cascade da V5_1.
ALTER TABLE item_lista_desejo
    DROP COLUMN usuario_id;

ALTER TABLE item_lista_desejo
    ALTER COLUMN dispositivo_id SET NOT NULL;

-- Impede duplicatas no mesmo aparelho, UBS e medicamento;
-- o índice da constraint também atende a listagem por aparelho.
ALTER TABLE item_lista_desejo
    ADD CONSTRAINT uk_item_lista_desejo UNIQUE (dispositivo_id, ubs_id, medicamento_id);
