-- Apoia a busca de UBS por proximidade: a caixa de latitude/longitude descarta as unidades distantes
-- antes da conta de distância.
CREATE INDEX idx_ubs_localizacao ON ubs (latitude, longitude);
