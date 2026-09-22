import { AppError } from "./errors.ts";
import { decodeJwtExpiry, decodeJwtSubject } from "../utils/jwt.ts";

// Sessão do utilizador: par de tokens em memória e no localStorage, e a
// renovação do access token. O refresh token roda a cada uso e só pode ser
// usado uma vez, por isso nunca podem partir duas renovações com o mesmo
// token: dentro do separador há uma só promessa partilhada (single-flight) e
// entre separadores um bloqueio (navigator.locks), dentro do qual se relê o
// armazenamento para aproveitar um par que outro separador já tenha obtido.
//
// Sem dependências do browser: tudo o que é externo é injetado, para os
// testes poderem simular separadores, bloqueios e respostas do servidor.

export const TOKENS_KEY = "fm.auth.tokens";
// Chave usada antes desta versão (só o refresh token); migrada ao ler.
export const LEGACY_REFRESH_KEY = "refreshToken";
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
  storage: KeyValueStorage | null;
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

function isTokenPair(value: unknown): value is TokenPair {
  if (!value || typeof value !== "object") return false;
  const { accessToken, refreshToken } = value as Record<string, unknown>;
  return typeof accessToken === "string" && !!accessToken && typeof refreshToken === "string" && !!refreshToken;
}

export function createSessionManager(deps: SessionDeps) {
  const { storage, refreshRequest, locks = null, now = Date.now } = deps;
  let current: StoredTokens | null = null;
  let inflight: Promise<StoredTokens> | null = null;
  // Muda a cada login, logout ou fim de sessão: uma renovação que termine
  // depois disso já não pode repor tokens (ressuscitar a sessão).
  let generation = 0;
  const listeners = new Set<(event: SessionEvent) => void>();

  function emit(event: SessionEvent) {
    for (const listener of listeners) listener(event);
  }

  // undefined = armazenamento inacessível (modo privado, bloqueado): vale a memória.
  function readStorage(): StoredTokens | null | undefined {
    if (!storage) return undefined;
    try {
      const raw = storage.getItem(TOKENS_KEY);
      if (raw !== null) return parseStoredTokens(raw);
      const legacy = storage.getItem(LEGACY_REFRESH_KEY);
      if (!legacy) return null;
      const migrated: StoredTokens = { accessToken: null, refreshToken: legacy };
      storage.setItem(TOKENS_KEY, JSON.stringify(migrated));
      storage.removeItem(LEGACY_REFRESH_KEY);
      return migrated;
    } catch {
      return undefined;
    }
  }

  function writeStorage(tokens: StoredTokens | null) {
    if (!storage) return;
    try {
      if (tokens) storage.setItem(TOKENS_KEY, JSON.stringify(tokens));
      else storage.removeItem(TOKENS_KEY);
      storage.removeItem(LEGACY_REFRESH_KEY);
    } catch {
      // Sem armazenamento a sessão continua em memória neste separador.
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
    emit("ended");
  }

  // Aceita um par que outro separador escreveu. Se for de outra conta, descarta
  // o estado local em vez de misturar dados de uma conta com tokens de outra.
  function adopt(next: StoredTokens): StoredTokens {
    if (isOtherUser(next)) {
      current = null;
      emit("user-changed");
      throw new SessionEndedError();
    }
    const changed = current?.accessToken !== next.accessToken || current?.refreshToken !== next.refreshToken;
    current = next;
    if (changed) emit("updated");
    return next;
  }

  // Corre com o bloqueio entre separadores (se existir).
  async function renew(rejectedAccessToken: string | null): Promise<StoredTokens> {
    const stored = readStorage();
    const latest = stored === undefined ? current : stored;
    if (!latest) {
      // Outro separador terminou a sessão entretanto.
      if (current) end();
      throw new SessionEndedError();
    }

    // Outro separador já renovou (ou o token guardado ainda serve): sem pedido.
    if (latest.accessToken !== rejectedAccessToken && isFresh(latest.accessToken)) {
      return adopt(latest);
    }
    if (isOtherUser(latest)) return adopt(latest);

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
      if (newer && newer.refreshToken !== latest.refreshToken) return adopt(newer);
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
    writeStorage(tokens);
    current = tokens;
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
      if (stored !== undefined) current = stored;
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

    // Login ou registo neste separador.
    setTokens(tokens: TokenPair) {
      generation++;
      current = { accessToken: tokens.accessToken, refreshToken: tokens.refreshToken };
      writeStorage(current);
    },

    // Logout neste separador; os outros recebem o evento storage e saem também.
    clear() {
      generation++;
      current = null;
      writeStorage(null);
    },

    // Evento storage de outro separador (key null = storage.clear()).
    handleStorageChange(key: string | null, newValue: string | null) {
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
        adopt(next);
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
