-- Índices da busca de medicamentos (GET /api/medicamentos).
-- A busca usa lower(nome) LIKE '%termo%' e lower(categoria) LIKE/=, que um B-tree não atende
-- (curinga no início). Os índices GIN com pg_trgm atendem LIKE com curinga e também a igualdade.
-- pg_trgm é extensão "trusted" (PostgreSQL 13+): o dono do banco pode criá-la sem superusuário.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_medicamento_nome_trgm ON medicamento USING gin (LOWER(nome) gin_trgm_ops);
CREATE INDEX idx_medicamento_categoria_trgm ON medicamento USING gin (LOWER(categoria) gin_trgm_ops);
