-- Modo da sessão de cada refresh token: true = "Manter sessão iniciada"
-- (14 dias, localStorage), false = sessão curta (12 h, sessionStorage).
-- A renovação herda o modo do token consumido.

-- 1) As linhas existentes ficam true: são sessões antigas, guardadas em
--    localStorage, e não podem passar a curtas na próxima renovação.
ALTER TABLE refresh_tokens ADD COLUMN remember_me BOOLEAN NOT NULL DEFAULT true;

-- 2) A partir daqui o omisso é false ("não recordar"). O código grava sempre
--    o valor explicitamente; este default só cobre inserções fora da app.
ALTER TABLE refresh_tokens ALTER COLUMN remember_me SET DEFAULT false;
