/// <reference types="node" />
import assert from "node:assert/strict";
import { test } from "node:test";
import { isReplayableBody } from "./body.ts";
import {
  ACCESS_TOKEN_MARGIN_MS,
  LEGACY_REFRESH_KEY,
  SessionEndedError,
  SESSION_CHANNEL_NAME,
  SessionUnavailableError,
  TOKENS_KEY,
  createSessionManager,
  openSessionChannel,
  type BroadcastChannelLike,
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
  session.setTokens({ accessToken: expired(), refreshToken: "rt-0" }, true);

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
  session.setTokens({ accessToken: expired(), refreshToken: "rt-0" }, true);
  storage.setItem(TOKENS_KEY, stored(fakeJwt("rui@teste.pt", -1_000), "rt-rui"));

  await assert.rejects(session.refresh(), SessionEndedError);
  assert.equal(server.calls, 0);
  assert.deepEqual(events, ["user-changed"]);
});

test("logout durante uma renovação em curso não repõe a sessão", async () => {
  const storage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const { session } = manager(storage, server);

  const pending = session.refresh();
  session.clear();
  await assert.rejects(pending, SessionEndedError);
  assert.equal(session.hasSession(), false);
  assert.equal(storage.data.has(TOKENS_KEY), false);
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

// ---------- Dois modos: localStorage ("Manter sessão iniciada") e sessionStorage ----------

// BroadcastChannel falso: entrega a todos os outros canais do mesmo hub, de
// forma assíncrona, como o real (nunca ao próprio emissor).
function channelHub() {
  const members = new Set<{ deliver: (data: unknown) => void }>();
  const hub = {
    sent: [] as unknown[],
    open(): BroadcastChannelLike {
      const listeners: ((event: { data: unknown }) => void)[] = [];
      const self = {
        deliver: (data: unknown) => listeners.forEach((listener) => listener({ data })),
      };
      members.add(self);
      return {
        postMessage(message) {
          // Como o real: a mensagem é copiada (structured clone).
          const data = structuredClone(message);
          hub.sent.push(data);
          for (const other of members) if (other !== self) setTimeout(() => other.deliver(data), 0);
        },
        addEventListener: (_type, listener) => void listeners.push(listener),
      };
    },
  };
  return hub;
}

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

// Um separador: localStorage partilhado, sessionStorage próprio.
function tab(opts: {
  local: KeyValueStorage;
  tabStorage?: KeyValueStorage;
  server: { refresh: (t: string) => Promise<RefreshResponse> };
  channel?: BroadcastChannelLike | null;
  locks?: LockManagerLike;
}) {
  const tabStorage = opts.tabStorage ?? memoryStorage();
  const session = createSessionManager({
    storage: opts.local,
    tabStorage,
    channel: opts.channel ?? null,
    locks: opts.locks,
    refreshRequest: (t) => opts.server.refresh(t),
    now: () => NOW,
  });
  const events: SessionEvent[] = [];
  session.subscribe((event) => events.push(event));
  return { session, events, tabStorage: tabStorage as ReturnType<typeof memoryStorage> };
}

const pair = (refreshToken: string, user = "ana@teste.pt") => ({
  accessToken: fakeJwt(user, 60_000, refreshToken),
  refreshToken,
});

test("login sem 'Manter sessão iniciada' grava só no sessionStorage", () => {
  const local = memoryStorage();
  const { session, tabStorage } = tab({ local, server: fakeServer() });

  session.setTokens(pair("rt-0"), false);

  assert.equal(local.data.has(TOKENS_KEY), false);
  assert.equal(JSON.parse(tabStorage.data.get(TOKENS_KEY)!).refreshToken, "rt-0");
});

test("login com 'Manter sessão iniciada' grava só no localStorage", () => {
  const local = memoryStorage();
  const { session, tabStorage } = tab({ local, server: fakeServer() });

  session.setTokens(pair("rt-0"), true);

  assert.equal(JSON.parse(local.data.get(TOKENS_KEY)!).refreshToken, "rt-0");
  assert.equal(tabStorage.data.has(TOKENS_KEY), false);
});

test("mudar de modo entre logins apaga o par do outro armazenamento", () => {
  const local = memoryStorage({ [LEGACY_REFRESH_KEY]: "antigo" });
  const { session, tabStorage } = tab({ local, server: fakeServer() });

  session.setTokens(pair("rt-lembrado"), true);
  session.setTokens(pair("rt-curto"), false);
  assert.equal(local.data.size, 0, "localStorage fica vazio, incluindo a chave antiga");
  assert.equal(JSON.parse(tabStorage.data.get(TOKENS_KEY)!).refreshToken, "rt-curto");

  session.setTokens(pair("rt-lembrado-2"), true);
  assert.equal(tabStorage.data.has(TOKENS_KEY), false);
  assert.equal(JSON.parse(local.data.get(TOKENS_KEY)!).refreshToken, "rt-lembrado-2");
});

test("sessão curta: o arranque lê o sessionStorage e a renovação volta a escrever lá", async () => {
  const local = memoryStorage();
  const tabStorage = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const { session } = tab({ local, tabStorage, server });

  assert.equal(await session.restore(), true);
  assert.equal(server.calls, 1);
  assert.equal(JSON.parse(tabStorage.data.get(TOKENS_KEY)!).refreshToken, "rt-1");
  assert.equal(local.data.has(TOKENS_KEY), false);
});

test("sessão recordada: a renovação continua no localStorage", async () => {
  const local = memoryStorage({ [TOKENS_KEY]: stored(expired(), "rt-0") });
  const server = fakeServer();
  const { session, tabStorage } = tab({ local, server });

  assert.equal(await session.restore(), true);
  assert.equal(JSON.parse(local.data.get(TOKENS_KEY)!).refreshToken, "rt-1");
  assert.equal(tabStorage.data.has(TOKENS_KEY), false);
});

test("replaceTokens mantém a sessão curta no sessionStorage", () => {
  const local = memoryStorage();
  const { session, tabStorage } = tab({ local, server: fakeServer() });
  session.setTokens(pair("rt-0"), false);

  session.replaceTokens(pair("rt-nova-password"));

  assert.equal(session.getRefreshToken(), "rt-nova-password");
  assert.equal(JSON.parse(tabStorage.data.get(TOKENS_KEY)!).refreshToken, "rt-nova-password");
  assert.equal(local.data.has(TOKENS_KEY), false);
});

test("replaceTokens mantém a sessão recordada no localStorage", () => {
  const local = memoryStorage();
  const { session, tabStorage } = tab({ local, server: fakeServer() });
  session.setTokens(pair("rt-0"), true);

  session.replaceTokens(pair("rt-nova-password"));

  assert.equal(JSON.parse(local.data.get(TOKENS_KEY)!).refreshToken, "rt-nova-password");
  assert.equal(tabStorage.data.has(TOKENS_KEY), false);
});

test("canal: replaceTokens chega aos outros separadores no mesmo modo", async () => {
  const local = memoryStorage();
  const hub = channelHub();
  const server = fakeServer();
  const a = tab({ local, server, channel: hub.open() });
  const b = tab({ local, server, channel: hub.open() });
  a.session.setTokens(pair("rt-0"), false);
  await flush();

  a.session.replaceTokens(pair("rt-nova-password"));
  await flush();

  assert.equal(b.session.getRefreshToken(), "rt-nova-password");
  assert.equal(JSON.parse(b.tabStorage.data.get(TOKENS_KEY)!).refreshToken, "rt-nova-password");
  assert.equal(server.calls, 0);
});

test("sair limpa os dois armazenamentos", () => {
  const local = memoryStorage({ [TOKENS_KEY]: stored(null, "rt-velho") });
  const { session, tabStorage } = tab({ local, server: fakeServer() });
  session.setTokens(pair("rt-0"), false);
  local.setItem(TOKENS_KEY, stored(null, "rt-velho"));

  session.clear();

  assert.equal(local.data.has(TOKENS_KEY), false);
  assert.equal(tabStorage.data.has(TOKENS_KEY), false);
  assert.equal(session.hasSession(), false);
});

test("canal: renovação curta num separador chega ao sessionStorage do outro, sem gastar o token duas vezes", async () => {
  const local = memoryStorage();
  const hub = channelHub();
  const server = fakeServer();
  const locks = fakeLocks();
  const a = tab({ local, server, locks, channel: hub.open() });
  const b = tab({ local, server, locks, channel: hub.open() });
  // Separador duplicado: os dois começam com a mesma cópia do sessionStorage.
  a.session.setTokens({ accessToken: expired(), refreshToken: "rt-0" }, false);
  await flush();
  assert.equal(JSON.parse(b.tabStorage.data.get(TOKENS_KEY)!).refreshToken, "rt-0");

  await a.session.refresh();
  await flush();

  assert.equal(b.session.getRefreshToken(), "rt-1");
  assert.equal(JSON.parse(b.tabStorage.data.get(TOKENS_KEY)!).refreshToken, "rt-1");
  assert.deepEqual(b.events, ["updated", "updated"]);
  // B já tem um access token válido: não renova de novo.
  await b.session.refresh();
  assert.equal(server.calls, 1);
  assert.equal(local.data.has(TOKENS_KEY), false);
});

test("canal: sair num separador termina a sessão curta nos outros e limpa-lhes o sessionStorage", async () => {
  const local = memoryStorage();
  const hub = channelHub();
  const a = tab({ local, server: fakeServer(), channel: hub.open() });
  const b = tab({ local, server: fakeServer(), channel: hub.open() });
  a.session.setTokens(pair("rt-0"), false);
  await flush();

  a.session.clear();
  await flush();

  assert.equal(b.session.hasSession(), false);
  assert.equal(b.events.at(-1), "ended");
  assert.equal(b.tabStorage.data.has(TOKENS_KEY), false);
});

test("canal: sair também funciona na sessão recordada", async () => {
  const local = memoryStorage();
  const hub = channelHub();
  const a = tab({ local, server: fakeServer(), channel: hub.open() });
  const b = tab({ local, server: fakeServer(), channel: hub.open() });
  a.session.setTokens(pair("rt-0"), true);
  await flush();
  assert.equal(b.session.getRefreshToken(), "rt-0");

  a.session.clear();
  await flush();

  assert.equal(b.session.hasSession(), false);
  assert.equal(local.data.has(TOKENS_KEY), false);
});

test("canal: o servidor recusar o token num separador termina a sessão em todos", async () => {
  const local = memoryStorage();
  const hub = channelHub();
  const server = fakeServer();
  const a = tab({ local, server, channel: hub.open() });
  const b = tab({ local, server, channel: hub.open() });
  a.session.setTokens({ accessToken: expired(), refreshToken: "rt-revogado" }, false);
  await flush();

  await assert.rejects(a.session.refresh(), SessionEndedError);
  await flush();

  assert.equal(b.session.hasSession(), false);
  assert.equal(b.tabStorage.data.has(TOKENS_KEY), false);
});

test("canal: login curto noutro separador passa este para sessão curta", async () => {
  const local = memoryStorage();
  const hub = channelHub();
  const a = tab({ local, server: fakeServer(), channel: hub.open() });
  const b = tab({ local, server: fakeServer(), channel: hub.open() });
  b.session.setTokens(pair("rt-lembrado"), true);
  await flush();

  a.session.setTokens(pair("rt-curto"), false);
  await flush();

  assert.equal(b.session.getRefreshToken(), "rt-curto");
  assert.equal(b.session.hasSession(), true);
  assert.equal(JSON.parse(b.tabStorage.data.get(TOKENS_KEY)!).refreshToken, "rt-curto");
  assert.equal(local.data.has(TOKENS_KEY), false);
  assert.ok(!b.events.includes("ended"), "a limpeza do localStorage não conta como logout");
});

test("canal: login de outra conta guarda o par novo e pede recomeço", async () => {
  const local = memoryStorage();
  const hub = channelHub();
  const a = tab({ local, server: fakeServer(), channel: hub.open() });
  const b = tab({ local, server: fakeServer(), channel: hub.open() });
  b.session.setTokens(pair("rt-ana"), false);
  await flush();

  a.session.setTokens(pair("rt-rui", "rui@teste.pt"), false);
  await flush();

  assert.equal(b.events.at(-1), "user-changed");
  assert.equal(b.session.hasSession(), false);
  // O reload que se segue encontra a sessão da conta nova, não a antiga.
  assert.equal(JSON.parse(b.tabStorage.data.get(TOKENS_KEY)!).refreshToken, "rt-rui");
});

test("canal: com canal o evento storage é ignorado (o canal já traz tudo)", () => {
  const local = memoryStorage();
  const hub = channelHub();
  const { session, events } = tab({ local, server: fakeServer(), channel: hub.open() });
  session.setTokens(pair("rt-0"), true);

  session.handleStorageChange(TOKENS_KEY, null);

  assert.equal(session.hasSession(), true);
  assert.deepEqual(events, []);
});

test("canal: mensagens inválidas são ignoradas", async () => {
  const local = memoryStorage();
  const hub = channelHub();
  const sender = hub.open();
  const { session, events } = tab({ local, server: fakeServer(), channel: hub.open() });
  session.setTokens(pair("rt-0"), false);

  for (const data of [null, "ended", { type: "tokens" }, { type: "tokens", remember: true, tokens: { accessToken: 1 } }, { type: "outro" }]) {
    sender.postMessage(data);
  }
  await flush();

  assert.equal(session.getRefreshToken(), "rt-0");
  assert.deepEqual(events, []);
});

test("sem BroadcastChannel: openSessionChannel devolve null, também se o construtor falhar", () => {
  assert.equal(openSessionChannel(undefined), null);
  class Failing {
    constructor() {
      throw new Error("SecurityError");
    }
  }
  assert.equal(openSessionChannel(Failing as unknown as new (name: string) => BroadcastChannelLike), null);

  const names: string[] = [];
  class Working {
    constructor(name: string) {
      names.push(name);
    }
    postMessage() {}
    addEventListener() {}
  }
  assert.ok(openSessionChannel(Working) instanceof Working);
  assert.deepEqual(names, [SESSION_CHANNEL_NAME]);
});

test("sem BroadcastChannel: os dois modos funcionam no separador e o evento storage sincroniza a sessão recordada", async () => {
  const local = memoryStorage();
  const server = fakeServer();
  const a = tab({ local, server, channel: null });
  const b = tab({ local, server, channel: null });

  // Curto: grava e renova no sessionStorage deste separador, sem erro.
  a.session.setTokens({ accessToken: expired(), refreshToken: "rt-0" }, false);
  await a.session.refresh();
  assert.equal(JSON.parse(a.tabStorage.data.get(TOKENS_KEY)!).refreshToken, "rt-1");
  // Limitação documentada: o outro separador não sabe desta sessão curta.
  assert.equal(b.session.hasSession(), false);

  // Recordada: o evento storage continua a levar login e logout ao outro separador.
  a.session.setTokens(pair("rt-lembrado"), true);
  b.session.handleStorageChange(TOKENS_KEY, local.getItem(TOKENS_KEY));
  assert.equal(b.session.getRefreshToken(), "rt-lembrado");
  a.session.clear();
  b.session.handleStorageChange(TOKENS_KEY, null);
  assert.equal(b.session.hasSession(), false);
});

test("canal que falha ao enviar não impede login, renovação nem logout", async () => {
  const local = memoryStorage();
  const broken: BroadcastChannelLike = {
    postMessage() {
      throw new Error("DataCloneError");
    },
    addEventListener() {},
  };
  const server = fakeServer();
  const { session, tabStorage } = tab({ local, server, channel: broken });

  session.setTokens({ accessToken: expired(), refreshToken: "rt-0" }, false);
  await session.refresh();
  assert.equal(JSON.parse(tabStorage.data.get(TOKENS_KEY)!).refreshToken, "rt-1");
  session.clear();
  assert.equal(session.hasSession(), false);
});
