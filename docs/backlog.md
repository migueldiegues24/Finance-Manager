# Backlog

## Segurança

- **Prioridade alta: reintroduzir o HSTS atrás do proxy do Railway sem o
  `ForwardedHeaderFilter` completo.** O `server.forward-headers-strategy=framework`
  punha no `remoteAddr` o **primeiro** valor do `X-Forwarded-For`, que o
  cliente controla, e escondia o cabeçalho do `ClientIpResolver`. Bastava
  variar esse valor para contornar todos os limites de tentativas por IP
  (login por IP e por IP+email, registo, refresh), com
  `TRUST_X_FORWARDED_FOR` a `true` ou a `false`. O hotfix pôs a estratégia a
  `none`. O custo é que o Spring vê os pedidos como HTTP atrás do proxy e o
  HSTS deixa de sair em produção. Caminhos possíveis:
  - **Filtro próprio e mínimo** que só lê `X-Forwarded-Proto` para marcar o
    pedido como HTTPS (`isSecure()`/`getScheme()`), sem tocar no
    `remoteAddr`, no `X-Forwarded-For` nem noutros cabeçalhos.
  - **Confirmar com o suporte do Railway** qual cabeçalho é fiável para o IP
    do cliente (`X-Real-IP` ou a posição certa no `X-Forwarded-For`) antes de
    reativar qualquer forwarding. As respostas públicas contradizem-se:
    [em 2024](https://station.railway.com/questions/edge-proxy-x-forwarded-for-and-x-real-ip-c5a50049)
    o último valor era o fiável;
    [em 2026](https://station.railway.com/questions/security-critical-questions-on-edge-prox-8fddd775)
    há respostas de pessoal do Railway que dizem o contrário.

  Critério de fecho: `SecurityHeadersIntegrationTest` volta a exigir o HSTS
  com `X-Forwarded-Proto: https`, o `ForwardedForRateLimitIntegrationTest`
  continua verde, e em produção a API volta a enviar
  `strict-transport-security`.
  _Registado a 2026-09-24, na hotfix/forwarded-for-spoofing._

## Backend

- **`PUT /api/rules/{id}` para editar regras.** Hoje `/api/rules` só tem
  GET, POST e DELETE, por isso o frontend só deixa criar e apagar regras
  (o diálogo "Nova regra" não edita). O endpoint deve aceitar `keyword`,
  `categoryId` e `priority`, validar que a regra e a categoria pertencem ao
  utilizador, e ter testes de serviço e de integração. Depois, o
  `RuleDialog` passa a servir também para editar, como o `CategoryDialog`.
  _Registado a 2026-09-21, na feat/ui-polish._

## Frontend e backend

- **Categoria prevista na revisão da importação.** Hoje a categoria só é
  atribuída no confirm (regras aplicadas no `ImportWriter`), por isso a
  página de revisão não mostra categorias nem as suas cores. Para a mostrar
  antes de confirmar, o parse teria de aplicar as regras e devolver a
  categoria prevista (id, nome e cor) por movimento, e a revisão ganharia
  uma coluna com o ponto/etiqueta de cor ao lado do nome. Avaliar também se
  o utilizador pode trocar a categoria antes de confirmar.
  _Registado a 2026-09-21, na feat/category-colors._

## Dependências

- **Majors e 0.x adiadas (sem vulnerabilidade conhecida).** Verificado a
  2026-09-24 com `npm audit` (0 vulnerabilidades) e
  `mvn org.codehaus.mojo:versions-maven-plugin:2.21.0:display-dependency-updates`:
  - `io.jsonwebtoken:jjwt-*` 0.12.6 → 0.13.0: numa série 0.x a minor pode
    partir API; rever o changelog e os testes do `JwtService` antes.
  - `typescript` 6 → 7 e `@types/node` 24 → 26: majors; correr `tsc -b`,
    ESLint (`typescript-eslint` tem de suportar a versão) e o build.
  - Spring Boot 4.1.1 é a última estável (só há 4.2.0-M1); h2, lombok e
    flyway-database-postgresql vêm do BOM e sobem com o Boot.
  _Registado a 2026-09-24, na feat/security-headers-deps._

- **`./mvnw` não funciona.** Falta `.mvn/wrapper/maven-wrapper.properties`
  no repositório; hoje usa-se o `mvn` do sistema (o Dockerfile usa a
  imagem do Maven, por isso o deploy não depende disto). Regenerar com
  `mvn wrapper:wrapper` e commitar a pasta `.mvn/`.
  _Registado a 2026-09-24, na feat/security-headers-deps._

## Frontend

- **CSP bloqueia a barra de ferramentas do Vercel nos previews.** A CSP do
  `vercel.json` só autoriza scripts do próprio domínio, por isso a Vercel
  Toolbar (comentários nos preview deployments, carregada de
  `vercel.live`) não abre. Se fizer falta, acrescentar `https://vercel.live`
  a `script-src`, `connect-src`, `frame-src` e `img-src`, idealmente só nos
  previews. _Registado a 2026-09-24, na feat/security-headers-deps._

## Autenticação (frontend e backend)

- **[Prioridade alta] O registo revela se um email já existe.** Um
  `POST /api/auth/register` com um email registado responde 400 "Já existe
  uma conta com este email", o que permite testar se alguém tem conta
  (enumeração de contas). O login já não o revela, e o limite de 10 registos
  por hora por IP (feat/auth-rate-limiting) só abranda o ataque, não o
  impede. Resolver com a verificação de email: o registo responde sempre o
  mesmo ("enviámos um email de confirmação") e, se a conta já existir, o
  email enviado avisa o dono em vez de criar outra.
  _Registado a 2026-09-23, na feat/auth-rate-limiting._

- **Subir o custo do BCrypt para 12, com rehash no login.** Hoje é o padrão
  do `BCryptPasswordEncoder` (10). Com 12 cada hash custa ~4x mais, o que
  abranda um ataque offline se a BD fugir (o login fica ~4x mais lento, o
  que o limite de tentativas torna aceitável). Como o custo vai dentro de
  cada hash, as passwords atuais continuam a funcionar; para as migrar,
  implementar `UserDetailsPasswordService` (o `DaoAuthenticationProvider`
  chama-o num login certo quando `upgradeEncoding` diz que o hash é fraco)
  e gravar o novo hash. Medir o tempo de um hash na máquina do Railway antes
  de escolher o custo final.
  _Registado a 2026-09-23, na feat/auth-rate-limiting._

- ~~**Renovação concorrente da sessão perde a sessão.**~~ **Resolvido** na
  `fix/session-refresh` (2026-09-21), só no frontend (`api/session.ts`):
  renovação única por separador (promessa partilhada, limpa ao terminar),
  `navigator.locks` entre separadores com releitura do par guardado dentro
  do bloqueio (sem `navigator.locks` fica o single-flight), só 400/401 do
  `/auth/refresh` terminam a sessão (rede, timeout e 5xx mantêm os tokens e
  mostram "Sem ligação" no arranque), um 401 renova e repete o pedido uma
  só vez, e o logout ou a troca de conta noutro separador propagam-se pelo
  evento `storage`. Testes em `api/session.test.ts`; verificado num Chrome
  com dois separadores contra o build de produção.

- **Tokens em armazenamento acessível a scripts.** O par (access e refresh
  token) está no `localStorage`, numa só chave JSON (`fm.auth.tokens`); um
  XSS pode lê-lo. O refresh token já lá estava antes; o access token passou
  a estar para os separadores partilharem a renovação. Alternativa: refresh
  token num cookie `HttpOnly; Secure; SameSite=Strict` com o caminho
  `/api/auth`, e o access token só em memória (exige mudar o backend, CORS
  com credenciais e proteção CSRF nos endpoints do cookie).
  _Registado a 2026-09-21, na fix/session-refresh._

- **Resposta perdida depois de o servidor rodar o token.** Se a resposta do
  `/auth/refresh` se perder (rede cai, timeout de 10 s no frontend) depois
  de o servidor já ter revogado o token, o frontend mantém o token antigo e
  a renovação seguinte dá 400, o que termina a sessão. Precisa de uma
  janela de tolerância no backend (alguns segundos em que o token acabado
  de rodar devolve o mesmo sucessor), a desenhar em conjunto com os dois
  pontos seguintes. _Registado a 2026-09-21, na fix/session-refresh._

- **Consumo do refresh token não é transacional.** `consumeRefreshToken`
  lê, verifica `revoked` e grava sem `@Transactional` nem bloqueio de linha
  (nem `AuthService.refresh`): dois pedidos exatamente simultâneos com o
  mesmo token podem ambos passar e emitir dois pares. Usar
  `UPDATE ... SET revoked = true WHERE token_hash = ? AND revoked = false`
  (e contar linhas) ou `SELECT ... FOR UPDATE`, dentro de uma transação.
  _Registado a 2026-09-21, na fix/session-refresh._

- **Sem deteção de reutilização.** Reutilizar um refresh token já usado só
  dá 400; não revoga a família nem as outras sessões do utilizador, por
  isso um token roubado e usado primeiro pelo atacante continua válido.
  Guardar a ligação ao token sucessor (família) e, fora da janela de
  tolerância, revogar a família inteira ao detetar reutilização.
  _Registado a 2026-09-21, na fix/session-refresh._

- **Refresh tokens usados nunca são apagados.** A tabela `refresh_tokens`
  cresce a cada renovação (a cada 15 min por sessão ativa) e os revogados
  ficam para sempre. Tarefa agendada que apague os expirados (e os
  revogados há mais de N dias, mantendo o rasto que se quiser para
  investigação de abuso). _Registado a 2026-09-21, na fix/session-refresh._

- **Logout durante uma renovação noutro separador.** O logout revoga o
  refresh token que o separador conhece; se outro separador o tiver rodado
  no mesmo instante, o sucessor fica válido no servidor (órfão) até
  expirar, embora já não esteja em nenhum browser. Resolve-se com a
  revogação por família do ponto anterior.
  _Registado a 2026-09-21, na fix/session-refresh._

<!-- teste de deploy com repositório privado, 22/09/2026 -->
