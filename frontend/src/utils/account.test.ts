/// <reference types="node" />
import assert from "node:assert/strict";
import { test } from "node:test";
import { filenameFromDisposition } from "./download.ts";
import { formatDateTime, sessionModeLabel, sortSessions, type AccountSession } from "./sessions.ts";

test("filenameFromDisposition lê o nome com e sem aspas", () => {
  assert.equal(
    filenameFromDisposition('attachment; filename="finance-manager-2026-09-24.json"', "x.json"),
    "finance-manager-2026-09-24.json",
  );
  assert.equal(filenameFromDisposition("attachment; filename=dados.json", "x.json"), "dados.json");
});

test("filenameFromDisposition usa o nome de recurso quando falta ou é suspeito", () => {
  assert.equal(filenameFromDisposition(null, "x.json"), "x.json");
  assert.equal(filenameFromDisposition("attachment", "x.json"), "x.json");
  assert.equal(filenameFromDisposition('attachment; filename="../../etc/passwd"', "x.json"), "x.json");
  assert.equal(filenameFromDisposition('attachment; filename="a\\b.json"', "x.json"), "x.json");
});

const session = (id: number, createdAt: string, current = false): AccountSession => ({
  id,
  createdAt,
  expiresAt: "2026-10-08T10:00:00Z",
  mode: "SHORT",
  current,
});

test("sortSessions põe a atual primeiro e as outras da mais recente para a mais antiga", () => {
  const sorted = sortSessions([
    session(1, "2026-09-20T10:00:00Z"),
    session(2, "2026-09-22T10:00:00Z"),
    session(3, "2026-09-21T10:00:00Z", true),
  ]);
  assert.deepEqual(
    sorted.map((s) => s.id),
    [3, 2, 1],
  );
});

test("sortSessions não altera a lista original", () => {
  const list = [session(1, "2026-09-20T10:00:00Z"), session(2, "2026-09-22T10:00:00Z")];
  sortSessions(list);
  assert.deepEqual(
    list.map((s) => s.id),
    [1, 2],
  );
});

test("sessionModeLabel distingue os dois modos", () => {
  assert.equal(sessionModeLabel("REMEMBERED"), "Sessão mantida");
  assert.equal(sessionModeLabel("SHORT"), "Sessão curta");
});

test("formatDateTime formata datas válidas e devolve o texto das inválidas", () => {
  assert.match(formatDateTime("2026-09-24T18:30:00Z"), /2026/);
  assert.equal(formatDateTime("não é data"), "não é data");
});
