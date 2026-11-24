-- Vínculo opcional do gestor com uma UBS.
-- Gestor com vínculo enxerga só a UBS dele no painel; sem vínculo, enxerga a rede.
-- A chave estrangeira é RESTRICT de propósito: apagar a UBS não pode deixar o gestor sem vínculo e ver a rede inteira.

ALTER TABLE usuario
    ADD COLUMN ubs_gestao_id BIGINT REFERENCES ubs (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_usuario_ubs_gestao_so_gestor CHECK (ubs_gestao_id IS NULL OR papel = 'GESTOR');
