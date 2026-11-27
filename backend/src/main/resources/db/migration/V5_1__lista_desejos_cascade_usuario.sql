-- Permite excluir uma conta e remover automaticamente
-- os itens da lista de desejos pertencentes a ela.
--
-- A V5 original permanece inalterada para preservar
-- o histórico e o checksum do Flyway.

ALTER TABLE item_lista_desejo
    DROP CONSTRAINT fk_item_lista_desejo_usuario,
    ADD CONSTRAINT fk_item_lista_desejo_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario (id)
        ON DELETE CASCADE;
