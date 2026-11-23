-- Compatibilidade temporária com o PR #33 (lista de desejos por dispositivo).
-- A V8 já cria os avisos por dispositivo: aqui apenas se habilita o UUID
-- nos itens da lista enquanto o PR #33 ainda não estiver integrado.
-- Todas as alterações abaixo são idempotentes após a integração do #33.
-- Não há exclusão ou migração dos avisos existentes.

ALTER TABLE item_lista_desejo
    ALTER COLUMN usuario_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS dispositivo_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_item_lista_desejo_dono'
          AND conrelid = 'item_lista_desejo'::regclass
    ) THEN
        ALTER TABLE item_lista_desejo
            ADD CONSTRAINT ck_item_lista_desejo_dono
            CHECK ((usuario_id IS NULL) <> (dispositivo_id IS NULL));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_item_lista_desejo_dispositivo'
          AND conrelid = 'item_lista_desejo'::regclass
    ) THEN
        ALTER TABLE item_lista_desejo
            ADD CONSTRAINT uk_item_lista_desejo_dispositivo
            UNIQUE (dispositivo_id, ubs_id, medicamento_id);
    END IF;
END
$$;
