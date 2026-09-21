# Backlog

## Backend

- **`PUT /api/rules/{id}` para editar regras.** Hoje `/api/rules` só tem
  GET, POST e DELETE, por isso o frontend só deixa criar e apagar regras
  (o diálogo "Nova regra" não edita). O endpoint deve aceitar `keyword`,
  `categoryId` e `priority`, validar que a regra e a categoria pertencem ao
  utilizador, e ter testes de serviço e de integração. Depois, o
  `RuleDialog` passa a servir também para editar, como o `CategoryDialog`.
  _Registado a 2026-09-21, na feat/ui-polish._

## Autenticação (frontend e backend)

- **Renovação concorrente da sessão perde a sessão.** O refresh token roda a
  cada uso e só pode ser usado uma vez (`RefreshTokenService.consumeRefreshToken`
  revoga-o e emite um novo). Se duas renovações partirem ao mesmo tempo com o
  mesmo token, a segunda falha com "Refresh token já foi utilizado" e o
  frontend limpa a sessão. Acontece:
  - em desenvolvimento, porque o `StrictMode` corre duas vezes o efeito do
    `AuthProvider` que renova a sessão ao carregar a página;
  - em produção, com dois separadores a carregar (ou a renovar após um 401
    no `apiFetch`) ao mesmo tempo, porque partilham o token do `localStorage`.

  Ideias a avaliar (podem combinar-se):
  - **Renovação única por separador:** uma só promessa de renovação
    partilhada em `api/client.ts`, usada tanto pelo `AuthProvider` como pelo
    `apiFetch`; resolve o caso do `StrictMode` e de pedidos em paralelo.
  - **Coordenação entre separadores:** `navigator.locks` (só um separador
    renova de cada vez; os outros releem o token do `localStorage` depois
    do lock) ou `BroadcastChannel` para partilhar o par de tokens novo.
  - **Backend:** janela de tolerância curta (alguns segundos) em que o token
    acabado de rodar ainda é aceite e devolve o mesmo par novo, sem abrir
    reutilização indefinida; exige guardar a ligação ao token sucessor.

  Validar com um teste que dispare duas renovações em simultâneo (dois
  separadores no Chrome, ou o `StrictMode` em desenvolvimento).
  _Registado a 2026-09-21, na feat/auth-ui-and-theme._
