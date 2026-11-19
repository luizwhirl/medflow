-- Permite usar a lista de desejos sem login.
-- Cada item pertence a uma conta (usuario_id) ou a um aparelho (dispositivo_id,
-- UUID gerado no navegador e enviado no cabeçalho X-Dispositivo-Id), nunca aos dois.

ALTER TABLE item_lista_desejo
    ALTER COLUMN usuario_id DROP NOT NULL,
    ADD COLUMN dispositivo_id UUID;

ALTER TABLE item_lista_desejo
    ADD CONSTRAINT ck_item_lista_desejo_dono
        CHECK ((usuario_id IS NULL) <> (dispositivo_id IS NULL));

-- Impede duplicatas para o mesmo aparelho, UBS e medicamento;
-- o índice da constraint também atende a listagem por aparelho.
ALTER TABLE item_lista_desejo
    ADD CONSTRAINT uk_item_lista_desejo_dispositivo
        UNIQUE (dispositivo_id, ubs_id, medicamento_id);
