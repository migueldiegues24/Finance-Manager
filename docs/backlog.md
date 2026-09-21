# Backlog

## Backend

- **`PUT /api/rules/{id}` para editar regras.** Hoje `/api/rules` só tem
  GET, POST e DELETE, por isso o frontend só deixa criar e apagar regras
  (o diálogo "Nova regra" não edita). O endpoint deve aceitar `keyword`,
  `categoryId` e `priority`, validar que a regra e a categoria pertencem ao
  utilizador, e ter testes de serviço e de integração. Depois, o
  `RuleDialog` passa a servir também para editar, como o `CategoryDialog`.
  _Registado a 2026-09-21, na feat/ui-polish._
