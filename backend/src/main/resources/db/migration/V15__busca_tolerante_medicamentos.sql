-- Normalização da busca por texto e reconhecimento de voz (PDS-51).
-- As migrations anteriores e os índices da V10 são preservados.

CREATE EXTENSION IF NOT EXISTS unaccent;

ALTER TABLE medicamento
    ADD COLUMN nome_busca TEXT,
    ADD COLUMN categoria_busca TEXT;

-- Normaliza os medicamentos que já estão cadastrados.
UPDATE medicamento
SET nome_busca = lower(unaccent(nome)),
    categoria_busca = lower(unaccent(categoria));

ALTER TABLE medicamento
    ALTER COLUMN nome_busca SET NOT NULL,
    ALTER COLUMN categoria_busca SET NOT NULL;

-- Mantém os valores normalizados atualizados, inclusive nas sincronizações.
CREATE FUNCTION atualizar_busca_medicamento()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.nome_busca := lower(unaccent(NEW.nome));
    NEW.categoria_busca := lower(unaccent(NEW.categoria));
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_medicamento_busca
BEFORE INSERT OR UPDATE OF nome, categoria
ON medicamento
FOR EACH ROW
EXECUTE FUNCTION atualizar_busca_medicamento();

-- Índices para busca parcial, igualdade e similaridade.
CREATE INDEX idx_medicamento_nome_busca_trgm
    ON medicamento USING gin (nome_busca gin_trgm_ops);

CREATE INDEX idx_medicamento_categoria_busca_trgm
    ON medicamento USING gin (categoria_busca gin_trgm_ops);
