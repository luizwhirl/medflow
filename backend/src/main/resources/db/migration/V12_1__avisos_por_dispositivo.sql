-- Conversão dos avisos autenticados para o modelo sem login.
-- NÃO altera a V8 aplicada anteriormente; não é possível associar avisos
-- antigos de uma conta a um dispositivo sem o consentimento de sua pessoa.
-- Os avisos antigos são descartados antes de remover a FK pessoal.
-- O trecho da lista é compatível com a versão de dispositivo do PR #33.

ALTER TABLE item_lista_desejo
    ALTER COLUMN usuario_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS dispositivo_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_item_lista_desejo_dono'
          AND conrelid = 'item_lista_desejo'::regclass
    ) THEN
        ALTER TABLE item_lista_desejo
            ADD CONSTRAINT ck_item_lista_desejo_dono
            CHECK ((usuario_id IS NULL) <> (dispositivo_id IS NULL));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_item_lista_desejo_dispositivo'
          AND conrelid = 'item_lista_desejo'::regclass
    ) THEN
        ALTER TABLE item_lista_desejo
            ADD CONSTRAINT uk_item_lista_desejo_dispositivo
            UNIQUE (dispositivo_id, ubs_id, medicamento_id);
    END IF;
END
$$;

-- Os dados existentes não têm dispositivo proprietário e não podem ser
-- associados automaticamente a um aparelho.
DELETE FROM aviso;

DROP INDEX IF EXISTS idx_aviso_usuario_criado_em;
DROP INDEX IF EXISTS idx_aviso_usuario_nao_lido;

ALTER TABLE aviso
    DROP CONSTRAINT uk_aviso_usuario_evento,
    DROP CONSTRAINT fk_aviso_usuario,
    DROP COLUMN usuario_id,
    ADD COLUMN dispositivo_id UUID NOT NULL,
    ADD CONSTRAINT uk_aviso_dispositivo_evento
        UNIQUE (dispositivo_id, chave_evento);

CREATE INDEX idx_aviso_dispositivo_criado_em
    ON aviso (dispositivo_id, criado_em DESC, id DESC);

CREATE INDEX idx_aviso_dispositivo_nao_lido
    ON aviso (dispositivo_id)
    WHERE lido = FALSE;
