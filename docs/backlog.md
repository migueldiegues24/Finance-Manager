# Backlog

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
