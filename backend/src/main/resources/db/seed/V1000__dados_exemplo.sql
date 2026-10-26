-- Dados FICTÍCIOS para desenvolvimento local (perfil "dev").
-- Não são carregados em produção. Códigos CNES/CATMAT abaixo são apenas ilustrativos.

INSERT INTO ubs (cnes, nome, tipo, bairro, endereco, telefone, horario, codigo_ibge_municipio, latitude, longitude, ultima_sincronizacao) VALUES
    ('9000101', 'UBS Benedito Bentes I',        'UBS', 'Benedito Bentes',      'Rua Projetada A, 120',         '(82) 3000-0101', 'Seg a Sex · 7h às 17h', '2704302', -9.5480, -35.7210, NOW() - INTERVAL '12 minutes'),
    ('9000102', 'USF Village Campestre II',     'USF', 'Cidade Universitária', 'Av. Central do Village, 45',   '(82) 3000-0102', 'Seg a Sex · 7h às 16h', '2704302', -9.5420, -35.7330, NOW() - INTERVAL '35 minutes'),
    ('9000103', 'UBS Clima Bom',                'UBS', 'Clima Bom',            'Rua do Posto, 300',            '(82) 3000-0103', 'Seg a Sex · 7h às 17h', '2704302', -9.5600, -35.7400, NOW() - INTERVAL '3 hours'),
    ('9000104', 'UBS Tabuleiro do Martins',     'UBS', 'Tabuleiro do Martins', 'Av. Menino Marcelo, s/n',      '(82) 3000-0104', 'Seg a Sex · 7h às 19h', '2704302', -9.5600, -35.7600, NOW() - INTERVAL '20 minutes'),
    ('9000105', 'UBS Santos Dumont',            'UBS', 'Santos Dumont',        'Rua São Vicente, 88',          '(82) 3000-0105', 'Seg a Sex · 7h às 17h', '2704302', -9.5250, -35.7450, NOW() - INTERVAL '70 minutes'),
    ('9000106', 'UBS Cidade Universitária',     'UBS', 'Cidade Universitária', 'Rua Eng. Mário de Gusmão, 10', '(82) 3000-0106', 'Seg a Sex · 7h às 19h', '2704302', -9.5550, -35.7750, NOW() - INTERVAL '8 minutes'),
    ('9000107', 'USF Riacho Doce (zona rural)', 'USF', 'Zona rural',           'Sítio Riacho Doce, km 4',      '(82) 3000-0107', 'Seg a Qui · 8h às 14h', '2704302', -9.5990, -35.6560, NOW() - INTERVAL '2 days');

INSERT INTO medicamento (catmat, nome, concentracao, forma_farmaceutica, categoria, rename) VALUES
    ('BR0267690', 'Metformina',          '850 mg',    'comprimido',   'Diabetes',     TRUE),
    ('BR0267612', 'Glibenclamida',       '5 mg',      'comprimido',   'Diabetes',     TRUE),
    ('BR0271157', 'Insulina humana NPH', '100 UI/mL', 'frasco 10 mL', 'Diabetes',     TRUE),
    ('BR0268856', 'Losartana potássica', '50 mg',     'comprimido',   'Hipertensão',  TRUE),
    ('BR0267620', 'Hidroclorotiazida',   '25 mg',     'comprimido',   'Hipertensão',  TRUE),
    ('BR0267515', 'Captopril',           '25 mg',     'comprimido',   'Hipertensão',  TRUE),
    ('BR0268280', 'Sinvastatina',        '20 mg',     'comprimido',   'Colesterol',   TRUE),
    ('BR0269919', 'Amoxicilina',         '500 mg',    'cápsula',      'Antibióticos', TRUE),
    ('BR0267543', 'Dipirona',            '500 mg',    'comprimido',   'Dor e febre',  TRUE),
    ('BR0268338', 'Salbutamol',          '100 mcg',   'aerossol',     'Asma',         TRUE);

-- Estoque: (cnes, catmat, status, quantidade)
INSERT INTO item_estoque (ubs_id, medicamento_id, status, quantidade)
SELECT u.id, m.id, e.status, e.quantidade
FROM (VALUES
    ('9000101', 'BR0267690', 'EM_FALTA',     0), ('9000102', 'BR0267690', 'RESERVADO', 14), ('9000103', 'BR0267690', 'DISPONIVEL', 220),
    ('9000104', 'BR0267690', 'DISPONIVEL',  85), ('9000106', 'BR0267690', 'DISPONIVEL', 310), ('9000107', 'BR0267690', 'RESERVADO', 6),
    ('9000101', 'BR0268856', 'DISPONIVEL', 260), ('9000102', 'BR0268856', 'DISPONIVEL', 180), ('9000104', 'BR0268856', 'RESERVADO', 10),
    ('9000101', 'BR0271157', 'RESERVADO',    4), ('9000103', 'BR0271157', 'DISPONIVEL',  18), ('9000105', 'BR0271157', 'EM_FALTA',    0),
    ('9000101', 'BR0267612', 'DISPONIVEL', 140), ('9000106', 'BR0267612', 'EM_FALTA',    0), ('9000101', 'BR0267543', 'DISPONIVEL', 300),
    ('9000103', 'BR0267515', 'EM_FALTA',     0), ('9000106', 'BR0269919', 'DISPONIVEL', 190), ('9000107', 'BR0268338', 'EM_FALTA',   0)
) AS e (cnes, catmat, status, quantidade)
JOIN ubs u ON u.cnes = e.cnes
JOIN medicamento m ON m.catmat = e.catmat;
