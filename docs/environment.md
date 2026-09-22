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
| `JWT_REFRESH_EXPIRATION_DAYS` | Validade do refresh token, em dias. | `30` |
| `REGISTRATION_ENABLED` | Liga ou desliga o registo público. Com `false`, o `POST /api/auth/register` responde 403 e não cria nada; o login e as contas existentes não mudam. | `true` |

## Frontend

Lida no build do Vite (`frontend/src/api/client.ts`).

| Variável | O que faz | Por omissão |
| --- | --- | --- |
| `VITE_API_BASE_URL` | URL base da API do backend. | `http://localhost:8080/api` |
