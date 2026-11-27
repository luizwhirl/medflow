-- Vincula a lista de desejos ao usuário autenticado.
-- Registros antigos identificados por CNS precisam de uma
-- estratégia de migração antes da alteração do esquema.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM item_lista_desejo) THEN
        RAISE EXCEPTION
            'Existem itens de lista de desejos vinculados ao CNS. '
            'É necessário migrar esses dados para usuários antes de aplicar a V5.';
    END IF;
END
$$;

-- Remove a unicidade baseada no CNS.
ALTER TABLE item_lista_desejo
    DROP CONSTRAINT uk_item_lista_desejo;

-- Substitui o CNS pela referência ao usuário autenticado.
ALTER TABLE item_lista_desejo
    DROP COLUMN cidadao_cns,
    ADD COLUMN usuario_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_item_lista_desejo_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id);

-- Impede duplicatas para o mesmo usuário, UBS e medicamento.
ALTER TABLE item_lista_desejo
    ADD CONSTRAINT uk_item_lista_desejo
        UNIQUE (usuario_id, ubs_id, medicamento_id);
