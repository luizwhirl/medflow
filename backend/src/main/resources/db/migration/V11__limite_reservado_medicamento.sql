-- Limite de estoque reservado por medicamento.
-- Com saldo até este valor o item aparece como RESERVADO. Nulo = usa o limite global (app.estoque.limite-reservado).

ALTER TABLE medicamento
    ADD COLUMN limite_reservado INTEGER CHECK (limite_reservado >= 0);
