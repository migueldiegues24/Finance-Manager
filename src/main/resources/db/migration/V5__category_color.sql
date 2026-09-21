-- Cor escolhida pelo utilizador para cada categoria (#RRGGBB em maiúsculas).
-- NULL = cor automática. As categorias existentes ficam a NULL.
ALTER TABLE categories ADD COLUMN color VARCHAR(7);

-- Segunda barreira, além da validação no serviço: só #RRGGBB em maiúsculas.
ALTER TABLE categories ADD CONSTRAINT chk_categories_color
    CHECK (color IS NULL OR color ~ '^#[0-9A-F]{6}$');
