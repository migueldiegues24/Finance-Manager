# Finance Manager
 
A personal finance web app: you upload your bank statement, review the parsed transactions, and the app imports them without duplicates, categorizes them with your own rules, and shows where your money went each month. Built as a Spring Boot REST API with a React + TypeScript frontend, and previously deployed in production (API on Railway, frontend on Vercel).
 
```
React + TypeScript (Vite)  →  REST API (Spring Boot, JWT)  →  PostgreSQL (Flyway migrations)
                                      ↑
                    CSV bank statement → parser → preview → confirm
```
 
## Screenshots
 
<!-- Add screenshots here, e.g.: -->
<!-- ![Dashboard](docs/screenshots/dashboard.png) -->
<!-- ![Import preview](docs/screenshots/import.png) -->
 
## Features
 
- **Statement import in two steps**: the file is parsed first and shown as a preview (with duplicates already flagged), and nothing is written until the user confirms.
- **Duplicate detection** through a transaction fingerprint, so importing the same statement twice, or overlapping statements, never creates repeated transactions.
- **Pluggable parsers**: a `StatementParser` interface with a registry, currently with a parser for CGD statements and a generic CSV format. Supporting a new bank means adding one class.
- **Categories and categorization rules**: user-defined categories (with colors) and rules that assign categories automatically on import.
- **Monthly dashboard** with totals per category and navigation between months.
- **Account management**: change password, see and revoke active sessions, export all data as JSON, delete the account.
## Security
 
Authentication and abuse protection were a big part of the work:
 
- **JWT access tokens (15 min) + rotating refresh tokens** stored server-side, with two session modes ("remember me" or short session).
- **Session management**: each session is tracked, so the user can end a single session or all the others.
- **Rate limiting with exponential backoff** on login, registration, refresh, password changes and data export, per IP and per account.
- **Authorization checks on every resource**, covered by integration tests that try to access other users' data.
- **Request limits**: maximum upload size, multipart part count and body read timeout.
- **Security headers and a strict Content Security Policy** on the frontend.
- Password policy and a flag to disable public registration.
## A few interesting problems
 
- **Identical transactions in the same statement.** Two equal coffees on the same day are two real transactions, not a duplicate. The fingerprint includes an occurrence index, so repeated rows within one file are kept, while the same rows imported again later are recognized as duplicates. The same function runs at parse time (to flag) and at confirm time (to reject), so both sides always agree.
- **Rate limits bypassed through `X-Forwarded-For`.** Behind the hosting proxy, the framework's forwarded-header handling took the *first* `X-Forwarded-For` value as the client IP, which the client controls. Changing that header on each request was enough to skip every per-IP limit. The fix was to disable that handling and resolve the client IP explicitly from the value appended by the proxy, with an integration test that reproduces the spoofing attempt.
- **Revocation vs. stateless tokens.** Revoking a session invalidates its refresh token immediately, but an access token already issued remains valid until it expires (at most 15 min). This is a documented, deliberate trade-off; deleting the account has no such window, because the user is loaded on every request.
## Tech stack
 
**Backend**: Java 21, Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, Flyway, JJWT, Bean Validation, OpenCSV, Lombok
**Frontend**: React, TypeScript, Vite, React Router
**Testing**: JUnit, Spring Boot Test, H2, 240+ unit and integration tests
**Infrastructure**: Docker (multi-stage build), Railway, Vercel
 
## Testing
 
The test suite covers the services (import, parsing, categorization, dashboard, auth, password policy) and the API end to end, including cross-user access, concurrent imports, re-imports, rate limiting, IP spoofing, session handling and security headers.
 
```bash
./mvnw test
```
 
## Running locally
 
Requires Java 21, Node.js and a PostgreSQL instance.
 
```bash
# 1. Database
docker run -d --name financemanager-db -p 5432:5432 \
  -e POSTGRES_DB=financemanager -e POSTGRES_PASSWORD=postgres postgres
 
# 2. Backend (http://localhost:8080), Flyway creates the schema on startup
export JWT_SECRET=<a long random secret>
./mvnw spring-boot:run
 
# 3. Frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```
 
All environment variables (database, CORS, token lifetimes, registration, proxy settings) are described in [`docs/environment.md`](docs/environment.md).
 
## Project structure
 
```
src/main/java/com/miguel/financemanager/
├── controller/    # REST endpoints (auth, account, imports, transactions, categories, rules, dashboard)
├── service/       # business logic, import flow, statement parsers
├── security/      # JWT, refresh tokens, rate limiting, client IP resolution
├── entity/        # JPA entities
├── repository/    # Spring Data repositories
├── dto/           # request and response objects
├── config/        # security, CORS, request limits
└── exception/     # global error handling
src/main/resources/db/migration/   # Flyway migrations
frontend/                          # React + TypeScript app
docs/                              # environment variables and backlog
```
 
## License
 
MIT
