-- O hash (fingerprint de deduplicação) passa a ser único por utilizador.
-- Antes de aplicar: SELECT user_id, hash, COUNT(*) FROM transactions
--   GROUP BY user_id, hash HAVING COUNT(*) > 1;  -- tem de devolver 0 linhas.
DROP INDEX idx_transactions_user_hash;
CREATE UNIQUE INDEX uq_transactions_user_hash ON transactions (user_id, hash);
