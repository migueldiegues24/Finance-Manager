import { AppError } from "./errors.ts";
import { decodeJwtExpiry, decodeJwtSubject } from "../utils/jwt.ts";

// Sessão do utilizador: par de tokens em memória e no armazenamento, e a
// renovação do access token. Dois modos, escolhidos no login: "Manter sessão
// iniciada" guarda no localStorage (sobrevive a fechar o browser); sem essa
// opção (e no registo) guarda no sessionStorage, que acaba com o separador.
// A renovação escreve sempre no armazenamento de onde a sessão veio.
//
// Entre separadores: o sessionStorage não é partilhado e o evento storage não
// o cobre, por isso logins, renovações e logouts são anunciados num
// BroadcastChannel, e cada separador copia o par para o seu armazenamento.
// Sem BroadcastChannel (browsers antigos) vale só o evento storage: funciona
// no modo "Manter sessão iniciada"; no modo curto cada separador fica isolado.
//
// O refresh token roda a cada uso e só pode ser O refresh token roda a cada uso e só pode ser
// usado uma vez, por isso nunca podem partir duas renovações com o mesmo
// token: dentro do separador há uma só promessa partilhada (single-flight) e
// entre separadores um bloqueio (navigator.locks), dentro do qual se relê o
// armazenamento para aproveitar um par que outro separador já tenha obtido.
//
// Sem dependências do browser: tudo o que é externo é injetado, para os
// testes poderem simular separadores, bloqueios e respostas do servidor.

export const TOKENS_KEY = "fm.auth.tokens";
// Chave usada antes desta versão (só o refresh token, no localStorage); migrada ao ler.
export const LEGACY_REFRESH_KEY = "refreshToken";
export const SESSION_CHANNEL_NAME = "finance-manager:auth-session";
export const REFRESH_LOCK_NAME = "finance-manager:auth-refresh";
// Um access token que expira dentro desta margem já é tratado como expirado.
export const ACCESS_TOKEN_MARGIN_MS = 30_000;

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

// O que fica guardado. O access token pode faltar (sessão migrada da chave antiga).
export interface StoredTokens {
  accessToken: string | null;
  refreshToken: string;
}

export interface KeyValueStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

// Subconjunto de BroadcastChannel usado aqui.
export interface BroadcastChannelLike {
  postMessage(message: unknown): void;
  addEventListener(type: "message", listener: (event: { data: unknown }) => void): void;
}

// O que passa no canal entre separadores. remember diz em que armazenamento
// quem recebe deve guardar o par.
export type SessionMessage =
  | { type: "tokens"; tokens: StoredTokens; remember: boolean }
  | { type: "ended" };

export interface LockManagerLike {
  request<T>(name: string, callback: () => Promise<T>): Promise<T>;
}

// Subconjunto de Response usado aqui. Lança em erro de rede ou timeout.
export interface RefreshResponse {
  ok: boolean;
  status: number;
  json(): Promise<unknown>;
}

export interface SessionDeps {
  // localStorage: sessões com "Manter sessão iniciada".
  storage: KeyValueStorage | null;
  // sessionStorage: sessões curtas (sem a opção, ou após registo).
  tabStorage?: KeyValueStorage | null;
  channel?: BroadcastChannelLike | null;
  refreshRequest: (refreshToken: string) => Promise<RefreshResponse>;
  locks?: LockManagerLike | null;
  now?: () => number;
}

// updated: há um par novo (renovação neste ou noutro separador, ou login noutro separador).
// ended: a sessão terminou (logout noutro separador, ou o servidor recusou o refresh token).
// user-changed: outro separador entrou com outra conta; o estado local tem de ser descartado.
export type SessionEvent = "updated" | "ended" | "user-changed";

// O servidor recusou o refresh token (400/401): a sessão terminou.
export class SessionEndedError extends AppError {
  constructor() {
    super("A sessão terminou. Entra outra vez.");
    this.name = "SessionEndedError";
  }
}

// Falha transitória (rede, timeout, 5xx): os tokens mantêm-se e pode tentar-se de novo.
export class SessionUnavailableError extends AppError {
  constructor() {
    super("Sem ligação ao servidor. Tenta de novo.");
    this.name = "SessionUnavailableError";
  }
}

// Lê o par guardado. JSON inválido ou com formato inesperado conta como sem sessão.
export function parseStoredTokens(raw: string | null): StoredTokens | null {
  if (raw === null) return null;
  try {
    const value: unknown = JSON.parse(raw);
    if (!value || typeof value !== "object") return null;
    const { accessToken, refreshToken } = value as Record<string, unknown>;
    if (typeof refreshToken !== "string" || !refreshToken) return null;
    if (accessToken !== null && typeof accessToken !== "string") return null;
    return { accessToken: accessToken || null, refreshToken };
  } catch {
    return null;
  }
}

function parseMessage(data: unknown): SessionMessage | null {
  if (!data || typeof data !== "object") return null;
  const { type, tokens, remember } = data as Record<string, unknown>;
  if (type === "ended") return { type };
  if (type !== "tokens" || typeof remember !== "boolean") return null;
  const parsed = parseStoredTokens(JSON.stringify(tokens ?? null));
  return parsed ? { type, tokens: parsed, remember } : null;
}

function isTokenPair(value: unknown): value is TokenPair {
  if (!value || typeof value !== "object") return false;
  const { accessToken, refreshToken } = value as Record<string, unknown>;
  return typeof accessToken === "string" && !!accessToken && typeof refreshToken === "string" && !!refreshToken;
}

export function createSessionManager(deps: SessionDeps) {
  const { storage, tabStorage = null, channel = null, refreshRequest, locks = null, now = Date.now } = deps;
  let current: StoredTokens | null = null;
  // Modo da sessão atual: decide onde se escreve o par renovado.
  let remembered = false;
  let inflight: Promise<StoredTokens> | null = null;
  // Muda a cada login, logout ou fim de sessão: uma renovação que termine
  // depois disso já não pode repor tokens (ressuscitar a sessão).
  let generation = 0;
  const listeners = new Set<(event: SessionEvent) => void>();

  function emit(event: SessionEvent) {
    for (const listener of listeners) listener(event);
  }

  // Onde está a sessão guardada: primeiro o sessionStorage (sessão curta),
  // depois o localStorage (com migração da chave antiga).
  // undefined = armazenamento inacessível (modo privado, bloqueado): vale a memória.
  function readStorage(): { tokens: StoredTokens; remember: boolean } | null | undefined {
    if (!storage && !tabStorage) return undefined;
    try {
      const short = parseStoredTokens(tabStorage?.getItem(TOKENS_KEY) ?? null);
      if (short) return { tokens: short, remember: false };
      if (!storage) return null;
      const raw = storage.getItem(TOKENS_KEY);
      if (raw !== null) {
        const tokens = parseStoredTokens(raw);
        return tokens && { tokens, remember: true };
      }
      const legacy = storage.getItem(LEGACY_REFRESH_KEY);
      if (!legacy) return null;
      const migrated: StoredTokens = { accessToken: null, refreshToken: legacy };
      storage.setItem(TOKENS_KEY, JSON.stringify(migrated));
      storage.removeItem(LEGACY_REFRESH_KEY);
      return { tokens: migrated, remember: true };
    } catch {
      return undefined;
    }
  }

  function writeArea(area: KeyValueStorage | null, value: string | null) {
    if (!area) return;
    try {
      if (value !== null) area.setItem(TOKENS_KEY, value);
      else area.removeItem(TOKENS_KEY);
    } catch {
      // Sem armazenamento a sessão continua em memória neste separador.
    }
  }

  // Grava o par no armazenamento do modo e apaga-o do outro, para uma mudança
  // de modo entre logins não deixar um par antigo para trás. null apaga ambos.
  function writeStorage(tokens: StoredTokens | null, remember = remembered) {
    const value = tokens && JSON.stringify(tokens);
    writeArea(remember ? storage : tabStorage, value);
    writeArea(remember ? tabStorage : storage, null);
    try {
      storage?.removeItem(LEGACY_REFRESH_KEY);
    } catch {
      // Idem.
    }
  }

  function broadcast(message: SessionMessage) {
    try {
      channel?.postMessage(message);
    } catch {
      // Sem canal os outros separadores contam só com o evento storage.
    }
  }

  function isFresh(accessToken: string | null): accessToken is string {
    const expiry = decodeJwtExpiry(accessToken);
    return expiry !== null && expiry - now() > ACCESS_TOKEN_MARGIN_MS;
  }

  // Sessão de outra conta? Só se ambos os access tokens identificarem o utilizador.
  function isOtherUser(next: StoredTokens): boolean {
    const mine = decodeJwtSubject(current?.accessToken);
    const theirs = decodeJwtSubject(next.accessToken);
    return mine !== null && theirs !== null && mine !== theirs;
  }

  function end() {
    generation++;
    current = null;
    writeStorage(null);
    broadcast({ type: "ended" });
    emit("ended");
  }

  // Aceita um par que outro separador escreveu. Se for de outra conta, descarta
  // o estado local em vez de misturar dados de uma conta com tokens de outra.
  function adopt(next: StoredTokens, remember: boolean): StoredTokens {
    if (isOtherUser(next)) {
      current = null;
      emit("user-changed");
      throw new SessionEndedError();
    }
    const changed = current?.accessToken !== next.accessToken || current?.refreshToken !== next.refreshToken;
    current = next;
    remembered = remember;
    if (changed) emit("updated");
    return next;
  }

  // Corre com o bloqueio entre separadores (se existir).
  async function renew(rejectedAccessToken: string | null): Promise<StoredTokens> {
    const stored = readStorage();
    const found = stored === undefined ? current && { tokens: current, remember: remembered } : stored;
    if (!found) {
      // Outro separador terminou a sessão entretanto.
      if (current) end();
      throw new SessionEndedError();
    }

    const latest = found.tokens;

    // Outro separador já renovou (ou o token guardado ainda serve): sem pedido.
    if (latest.accessToken !== rejectedAccessToken && isFresh(latest.accessToken)) {
      return adopt(latest, found.remember);
    }
    if (isOtherUser(latest)) return adopt(latest, found.remember);

    const startedAt = generation;
    let response: RefreshResponse;
    try {
      response = await refreshRequest(latest.refreshToken);
    } catch {
      throw new SessionUnavailableError();
    }

    if (startedAt !== generation) throw new SessionEndedError();

    if (response.status === 400 || response.status === 401) {
      // Se outro separador já rodou o token, o 400 é esperado: adota o par mais novo.
      const newer = readStorage();
      if (newer && newer.tokens.refreshToken !== latest.refreshToken) return adopt(newer.tokens, newer.remember);
      end();
      throw new SessionEndedError();
    }
    if (!response.ok) throw new SessionUnavailableError();

    let body: unknown;
    try {
      body = await response.json();
    } catch {
      throw new SessionUnavailableError();
    }
    if (!isTokenPair(body)) throw new SessionUnavailableError();
    if (startedAt !== generation) throw new SessionEndedError();

    const tokens = { accessToken: body.accessToken, refreshToken: body.refreshToken };
    // O servidor mantém o modo do token consumido; o armazenamento acompanha.
    remembered = found.remember;
    writeStorage(tokens);
    current = tokens;
    broadcast({ type: "tokens", tokens, remember: remembered });
    emit("updated");
    return tokens;
  }

  // Renova o access token. Chamadas concorrentes partilham a mesma promessa,
  // limpa quando termina (com sucesso ou erro), e a seguinte tenta de novo.
  // rejectedAccessToken: o token que o servidor acabou de recusar com 401,
  // que nunca é reaproveitado mesmo que o "exp" diga que ainda é válido.
  function refresh(rejectedAccessToken: string | null = null): Promise<StoredTokens> {
    if (!inflight) {
      const run = () => renew(rejectedAccessToken);
      inflight = (locks ? locks.request(REFRESH_LOCK_NAME, run) : run()).finally(() => {
        inflight = null;
      });
    }
    return inflight;
  }

  // Aviso de outro separador no canal. O par é copiado para o armazenamento
  // deste separador (o sessionStorage não é partilhado), para um reload ou a
  // próxima renovação o encontrarem.
  function handleMessage(data: unknown) {
    const message = parseMessage(data);
    if (!message) return;
    if (message.type === "ended") {
      generation++;
      writeStorage(null);
      if (current) {
        current = null;
        emit("ended");
      }
      return;
    }
    writeStorage(message.tokens, message.remember);
    try {
      adopt(message.tokens, message.remember);
    } catch {
      // adopt já avisou (user-changed); o reload lê o par já guardado.
    }
  }

  channel?.addEventListener("message", (event) => handleMessage(event.data));

  return {
    getAccessToken: () => current?.accessToken ?? null,
    getRefreshToken: () => current?.refreshToken ?? null,
    hasSession: () => current !== null,
    refresh,

    // Arranque: lê o par guardado; com access token válido não faz pedido.
    // true = sessão ativa; false = sem sessão; lança SessionUnavailableError
    // em falha transitória (os tokens mantêm-se).
    async restore(): Promise<boolean> {
      const stored = readStorage();
      if (stored !== undefined) {
        current = stored?.tokens ?? null;
        if (stored) remembered = stored.remember;
      }
      if (!current) return false;
      if (isFresh(current.accessToken)) return true;
      try {
        await refresh();
        return true;
      } catch (err) {
        if (err instanceof SessionEndedError) return false;
        throw err;
      }
    },

    // Login ou registo neste separador. remember: "Manter sessão iniciada".
    setTokens(tokens: TokenPair, remember: boolean) {
      generation++;
      current = { accessToken: tokens.accessToken, refreshToken: tokens.refreshToken };
      remembered = remember;
      writeStorage(current);
      broadcast({ type: "tokens", tokens: current, remember });
    },

    // Logout neste separador; os outros recebem o aviso no canal (ou o
    // evento storage, sem canal) e saem também.
    clear() {
      generation++;
      current = null;
      writeStorage(null);
      broadcast({ type: "ended" });
    },

    // Evento storage de outro separador (key null = storage.clear()). Com
    // canal é ignorado: o canal já traz tudo, e seguir os dois ao mesmo tempo
    // podia ler como logout a limpeza do localStorage feita por um login curto.
    handleStorageChange(key: string | null, newValue: string | null) {
      if (channel) return;
      if (key !== null && key !== TOKENS_KEY) return;
      const next = parseStoredTokens(key === null ? null : newValue);
      if (!next) {
        generation++;
        if (current) {
          current = null;
          emit("ended");
        }
        return;
      }
      try {
        adopt(next, true);
      } catch {
        // adopt já avisou (user-changed).
      }
    },

    subscribe(listener: (event: SessionEvent) => void) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}

export type SessionManager = ReturnType<typeof createSessionManager>;
