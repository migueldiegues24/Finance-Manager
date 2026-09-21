-- Campos que alguns extratos trazem (ex.: CGD). Ficam a NULL nos
-- movimentos importados pelo CSV genérico.
ALTER TABLE transactions ADD COLUMN movement_date DATE;
ALTER TABLE transactions ADD COLUMN balance_after NUMERIC(12, 2);

-- Banco de origem de cada importação; as existentes vieram todas do CSV genérico.
ALTER TABLE statement_imports ADD COLUMN bank VARCHAR(20) NOT NULL DEFAULT 'GENERIC';
