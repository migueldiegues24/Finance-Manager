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
| `TRUST_X_FORWARDED_FOR` | Usa o último valor do cabeçalho `X-Forwarded-For` (o que o proxy acrescenta) como IP do cliente nos limites de tentativas de login, registo e refresh. **Deve ficar `true` no Railway.** Ver a nota abaixo. | `false` |

### `TRUST_X_FORWARDED_FOR` e o Railway

No Railway, os pedidos chegam à app através do proxy da plataforma, por isso
o endereço da ligação é o do proxy e não o do visitante. **Com esta variável
a `false` no Railway, todos os visitantes contam como o mesmo IP:** bastam 20
logins falhados (de quem quer que seja) para o limite por IP bloquear o login
de **todos os utilizadores**, e o mesmo acontece com o registo (10 por hora
para toda a gente) e com o refresh.

Com `true`, a app usa o último valor do `X-Forwarded-For`, que é o que o
proxy do Railway acrescenta com o IP que viu; os valores anteriores vêm do
cliente e são ignorados. **Não ligar sem um proxy à frente**: aí o cabeçalho
vem do próprio cliente, que podia mudar de "IP" a cada pedido e fugir ao
limite. Se um dia houver outro proxy à frente do Railway (ex.: Cloudflare),
o último valor passa a ser o desse proxy e isto tem de ser revisto.

Os contadores estão em memória: perdem-se num restart e, com várias réplicas,
cada uma tem os seus.

## Frontend

Lida no build do Vite (`frontend/src/api/client.ts`).

| Variável | O que faz | Por omissão |
| --- | --- | --- |
| `VITE_API_BASE_URL` | URL base da API do backend. | `http://localhost:8080/api` |
