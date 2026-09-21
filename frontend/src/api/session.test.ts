/// <reference types="node" />
import assert from "node:assert/strict";
import { test } from "node:test";
import { isReplayableBody } from "./body.ts";
import {
  ACCESS_TOKEN_MARGIN_MS,
  LEGACY_REFRESH_KEY,
  SessionEndedError,
  SessionUnavailableError,
  TOKENS_KEY,
  createSessionManager,
  type KeyValueStorage,
  type LockManagerLike,
  type RefreshResponse,
  type SessionEvent,
} from "./session.ts";

// Tokens de teste: valores fictícios, gerados aqui, sem relação com tokens reais.
const NOW = 1_800_000_000_000;

function fakeJwt(sub: string, expiresInMs: number, id = ""): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "HS512" })}.${encode({ sub, exp: (NOW + expiresInMs) / 1000, jti: id })}.assinatura`;
}

function memoryStorage(initial: Record<string, string> = {}): KeyValueStorage & { data: Map<string, string> } {
  const data = new Map(Object.entries(initial));
  return {
    data,
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => void data.set(key, value),
    removeItem: (key) => void data.delete(key),
  };
}

function stored(accessToken: string | null, refreshToken: string) {
  return JSON.stringify({ accessToken, refreshToken });
}

function jsonResponse(status: number, body: unknown = {}): RefreshResponse {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

// Servidor falso com rotação: cada refresh token só serve uma vez.
function fakeServer(user = "ana@teste.pt") {
  const valid = new Set<string>(["rt-0"]);
  let issued = 0;
  const server = {
    calls: 0,
    next: null as null | (() => Promise<RefreshResponse>),
    async refresh(refreshToken: string): Promise<RefreshResponse> {
      server.calls++;
      if (server.next) {
        const next = server.next;
        server.next = null;
        return next();
      }
      await new Promise((resolve) => setTimeout(resolve, 5));
      if (!valid.delete(refreshToken)) return jsonResponse(400, { error: "Refresh token já foi utilizado" });
      issued++;
      valid.add(`rt-${issued}`);
      return jsonResponse(200, { accessToken: fakeJwt(user, 120_000, `at-${issued}`), refreshToken: `rt-${issued}` });
    },
  };
  return server;
}

// Bloqueio falso que serializa, como navigator.locks entre separadores.
function fakeLocks(): LockManagerLike & { requests: number } {
  let tail: Promise<unknown> = Promise.resolve();
  const locks = {
    requests: 0,
    request<T>(_name: string, callback: () => Promise<T>): Promise<T> {
      locks.requests++;
      const result = tail.then(callback);
      tail = result.catch(() => {});
      return result;
    },
  };
  return locks;
}

function manager(storage: KeyValueStorage | null, server: { refresh: (t: string) => Promise<RefreshResponse> }, locks?: LockManagerLike) {
  const session = createSessionManager({ storage, refreshRequest: (t) => server.refresh(t), locks, now: () => NOW });
  const events: SessionEvent[] = [];
  session.subscribe((event) => events.push(event));
  return { session, events };
}

const expired = () => fakeJwt("ana@teste.pt", -1_000, "velho");

test("N chamadas concorrentes fazem um só pedido e todas recebem o mesmo par", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const { session } = manager(storage, server);

  const results = await Promise.all(Array.from({ length: 5 }, () => session.refresh()));
  assert.equal(server.calls, 1);
  assert.ok(results.every((r) => r.refreshToken === results[0].refreshToken));
});

test("arranque com StrictMode (restore duas vezes) gasta o token uma só vez", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const { session } = manager(storage, server);

  const [a, b] = await Promise.all([session.restore(), session.restore()]);
  assert.equal(a, true);
  assert.equal(b, true);
  assert.equal(server.calls, 1);
});

test("arranque com access token guardado e válido não faz pedido", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(fakeJwt("ana@teste.pt", 60_000), "rt-0") });
  const server = fakeServer();
  const { session } = manager(storage, server);

  assert.equal(await session.restore(), true);
  assert.equal(server.calls, 0);
});

test("margem de 30 s: exatamente na margem renova, 1 ms acima não", async () => {
  for (const [expiresIn, expectedCalls] of [
    [ACCESS_TOKEN_MARGIN_MS, 1],
    [ACCESS_TOKEN_MARGIN_MS + 1, 0],
  ] as const) {
    const storage = memoryStorage({ [TOKENS_KEY]: stored(fakeJwt("ana@teste.pt", expiresIn), "rt-0") });
    const server = fakeServer();
    const { session } = manager(storage, server);
    await session.restore();
    assert.equal(server.calls, expectedCalls, `expira em ${expiresIn} ms`);
  }
});

test("uma falha chega a todos os que esperam, e a chamada seguinte tenta de novo", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const { session } = manager(storage, server);
  server.next = async () => {
    throw new TypeError("Failed to fetch");
  };

  const results = await Promise.allSettled([session.refresh(), session.refresh(), session.refresh()]);
  assert.equal(server.calls, 1);
  for (const r of results) {
    assert.equal(r.status, "rejected");
    assert.ok((r as PromiseRejectedResult).reason instanceof SessionUnavailableError);
  }

  // A promessa foi limpa: nova ronda, novo pedido, agora com sucesso.
  await session.refresh();
  assert.equal(server.calls, 2);
  assert.equal(session.getRefreshToken(), "rt-1");
});

test("a promessa partilhada é limpa entre rondas bem-sucedidas", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const { session } = manager(storage, server);

  const first = await session.refresh();
  // O access token novo é válido, mas foi recusado com 401: tem de renovar outra vez.
  const second = await session.refresh(first.accessToken);
  assert.equal(server.calls, 2);
  assert.notEqual(first.refreshToken, second.refreshToken);
});

test("erro de rede, timeout ou 5xx mantêm os tokens guardados", async () => {
  const failures: Array<() => Promise<RefreshResponse>> = [
    async () => {
      throw new TypeError("Failed to fetch");
    },
    async () => {
      throw new DOMException("signal timed out", "TimeoutError");
    },
    async () => jsonResponse(503),
    async () => jsonResponse(500),
  ];
  for (const failure of failures) {
    const before = stored(expired(), "rt-0");
    const storage = memoryStorage({ [TOKENS_KEY]: before });
    const server = fakeServer();
    const { session, events } = manager(storage, server);
    server.next = failure;

    await assert.rejects(session.restore(), SessionUnavailableError);
    assert.equal(storage.data.get(TOKENS_KEY), before);
    assert.equal(session.hasSession(), true);
    assert.deepEqual(events, []);
  }
});

test("400 ou 401 do endpoint de renovação apagam os tokens e terminam a sessão", async () => {
  for (const status of [400, 401]) {
    const storage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
    const server = fakeServer();
    const { session, events } = manager(storage, server);
    server.next = async () => jsonResponse(status);

    await assert.rejects(session.refresh(), SessionEndedError);
    assert.equal(storage.data.has(TOKENS_KEY), false);
    assert.equal(session.hasSession(), false);
    assert.deepEqual(events, ["ended"]);
  }
});

test("restore devolve false (sem sessão) quando o servidor recusa o token", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const { session } = manager(storage, server);
  server.next = async () => jsonResponse(400);
  assert.equal(await session.restore(), false);
});

test("dois separadores com bloqueio que serializa fazem uma só renovação", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const locks = fakeLocks();
  const tabA = manager(storage, server, locks);
  const tabB = manager(storage, server, locks);

  const [a, b] = await Promise.all([tabA.session.restore(), tabB.session.restore()]);
  assert.equal(a && b, true);
  assert.equal(server.calls, 1);
  assert.equal(locks.requests, 2);
  assert.equal(tabA.session.getRefreshToken(), "rt-1");
  assert.equal(tabB.session.getRefreshToken(), "rt-1");
  assert.equal(tabA.session.getAccessToken(), tabB.session.getAccessToken());
});

test("dois separadores: o 401 do mesmo access token só renova uma vez", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(fakeJwt("ana@teste.pt", 60_000, "a0"), "rt-0") });
  const server = fakeServer();
  const locks = fakeLocks();
  const tabA = manager(storage, server, locks);
  const tabB = manager(storage, server, locks);
  await tabA.session.restore();
  await tabB.session.restore();
  const rejected = tabA.session.getAccessToken();

  await Promise.all([tabA.session.refresh(rejected), tabB.session.refresh(rejected)]);
  assert.equal(server.calls, 1);
  assert.equal(tabB.session.getRefreshToken(), "rt-1");
});

test("sem navigator.locks continua a funcionar (single-flight por separador)", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const { session } = manager(storage, server, undefined);

  await Promise.all([session.restore(), session.refresh(), session.refresh()]);
  assert.equal(server.calls, 1);
  assert.equal(session.getRefreshToken(), "rt-1");
});

test("400 com um par mais novo já guardado por outro separador: adota-o", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const { session, events } = manager(storage, server);
  await session.restore().catch(() => {});
  server.calls = 0;

  // Sem bloqueio: outro separador rodou o token enquanto o pedido estava em curso.
  const newer = stored(fakeJwt("ana@teste.pt", 120_000, "outro"), "rt-outro");
  server.next = async () => {
    storage.setItem(TOKENS_KEY, newer);
    return jsonResponse(400);
  };
  // Força a renovação (o access token atual é o recusado).
  await session.refresh(session.getAccessToken());
  assert.equal(session.getRefreshToken(), "rt-outro");
  assert.equal(storage.data.get(TOKENS_KEY), newer);
  assert.ok(!events.includes("ended"));
});

test("migra a chave antiga (só refresh token) e renova com ela", async () => {
  const storage = memoryStorage({ [LEGACY_REFRESH_KEY]: "rt-0" });
  const server = fakeServer();
  const { session } = manager(storage, server);

  assert.equal(await session.restore(), true);
  assert.equal(server.calls, 1);
  assert.equal(storage.data.has(LEGACY_REFRESH_KEY), false);
  assert.equal(JSON.parse(storage.data.get(TOKENS_KEY)!).refreshToken, "rt-1");
});

test("JSON inválido ou com formato inesperado conta como sem sessão", async () => {
  for (const raw of ["{nao-e-json", "null", "42", '"texto"', "{}", '{"refreshToken":""}', '{"refreshToken":1}', '{"accessToken":5,"refreshToken":"rt-0"}']) {
    const storage = memoryStorage({ [TOKENS_KEY]: raw });
    const server = fakeServer();
    const { session } = manager(storage, server);
    assert.equal(await session.restore(), false, raw);
    assert.equal(server.calls, 0);
  }
});

test("armazenamento que lança: a sessão continua em memória", async () => {
  const throwing: KeyValueStorage = {
    getItem() {
      throw new Error("SecurityError");
    },
    setItem() {
      throw new Error("QuotaExceededError");
    },
    removeItem() {
      throw new Error("SecurityError");
    },
  };
  const server = fakeServer();
  const { session } = manager(throwing, server);
  session.setTokens({ accessToken: expired(), refreshToken: "rt-0" });

  await session.refresh();
  assert.equal(session.getRefreshToken(), "rt-1");
});

test("evento storage: logout noutro separador termina a sessão aqui", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(fakeJwt("ana@teste.pt", 60_000), "rt-0") });
  const { session, events } = manager(storage, fakeServer());
  await session.restore();

  session.handleStorageChange(TOKENS_KEY, null);
  assert.equal(session.hasSession(), false);
  assert.deepEqual(events, ["ended"]);
});

test("evento storage: renovação noutro separador atualiza os tokens em memória", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(fakeJwt("ana@teste.pt", 60_000), "rt-0") });
  const { session, events } = manager(storage, fakeServer());
  await session.restore();

  const newer = fakeJwt("ana@teste.pt", 120_000, "novo");
  session.handleStorageChange(TOKENS_KEY, stored(newer, "rt-9"));
  assert.equal(session.getAccessToken(), newer);
  assert.equal(session.getRefreshToken(), "rt-9");
  assert.deepEqual(events, ["updated"]);

  // Outras chaves não interessam.
  session.handleStorageChange("theme", "dark");
  assert.deepEqual(events, ["updated"]);
});

test("evento storage de outro utilizador: descarta o estado sem adotar os tokens", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(fakeJwt("ana@teste.pt", 60_000), "rt-0") });
  const { session, events } = manager(storage, fakeServer());
  await session.restore();

  session.handleStorageChange(TOKENS_KEY, stored(fakeJwt("rui@teste.pt", 60_000), "rt-rui"));
  assert.equal(session.hasSession(), false);
  assert.equal(session.getAccessToken(), null);
  assert.deepEqual(events, ["user-changed"]);
});

test("renovação que encontra no armazenamento a sessão de outro utilizador não a usa", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const { session, events } = manager(storage, server);
  session.setTokens({ accessToken: expired(), refreshToken: "rt-0" });
  storage.setItem(TOKENS_KEY, stored(fakeJwt("rui@teste.pt", -1_000), "rt-rui"));

  await assert.rejects(session.refresh(), SessionEndedError);
  assert.equal(server.calls, 0);
  assert.deepEqual(events, ["user-changed"]);
});

test("os erros não expõem tokens", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const { session } = manager(storage, server);
  server.next = async () => jsonResponse(400, { error: "Refresh token inválido" });

  const err = await session.refresh().catch((e: Error) => e);
  assert.ok(err instanceof SessionEndedError);
  assert.ok(!String(err.message).includes("rt-0"));
});

test("só os corpos reutilizáveis permitem repetir o pedido", () => {
  assert.equal(isReplayableBody(undefined), true);
  assert.equal(isReplayableBody(null), true);
  assert.equal(isReplayableBody('{"a":1}'), true);
  assert.equal(isReplayableBody(new FormData()), true);
  assert.equal(isReplayableBody(new Blob(["x"])), true);
  assert.equal(isReplayableBody(new ReadableStream()), false);
});
