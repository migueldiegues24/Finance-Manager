# Variáveis de ambiente

## Backend

Lidas no arranque (`src/main/resources/application.properties`).

| Variável | O que faz | Por omissão |
| --- | --- | --- |
| `PORT` | Porta HTTP do servidor. | `8080` |
| `SPRING_DATASOURCE_URL` | URL JDBC da base de dados PostgreSQL. | `jdbc:postgresql://localhost:5432/financemanager` |
| `SPRING_DATASOURCE_USERNAME` | Utilizador da base de dados. | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Password da base de dados. | `postgres` |
| `ALLOWED_ORIGINS` | Origens autorizadas pelo CORS, separadas por vírgulas. | `http://localhost:5173` |
| `JWT_SECRET` | Chave para assinar os access tokens. | Nenhum (obrigatória) |
| `JWT_ACCESS_EXPIRATION_MS` | Validade do access token, em milissegundos. | `900000` (15 min) |
| `JWT_REFRESH_EXPIRATION_HOURS_SHORT` | Validade do refresh token numa sessão sem "Manter sessão iniciada" (o login por omissão e o registo), em horas. Cada renovação emite um token com a mesma validade, contada a partir desse momento. | `12` |
| `JWT_REFRESH_EXPIRATION_DAYS_REMEMBERED` | Validade do refresh token com "Manter sessão iniciada" marcado, em dias. Cada renovação emite um token com a mesma validade, contada a partir desse momento. | `14` |
| `REGISTRATION_ENABLED` | Liga ou desliga o registo público. Com `false`, o `POST /api/auth/register` responde 403 e não cria nada; o login e as contas existentes não mudam. | `true` |

A renovação (`POST /api/auth/refresh`) mantém sempre o modo do token que consome: uma sessão curta nunca passa a longa, nem o contrário. As sessões anteriores a esta opção contam como "recordadas".

`JWT_REFRESH_EXPIRATION_DAYS` deixou de ser lida. Se ainda estiver definida num ambiente, é ignorada e pode ser removida.

## Frontend

Lida no build do Vite (`frontend/src/api/client.ts`).

| Variável | O que faz | Por omissão |
| --- | --- | --- |
| `VITE_API_BASE_URL` | URL base da API do backend. | `http://localhost:8080/api` |
