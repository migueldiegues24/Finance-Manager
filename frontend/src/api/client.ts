import { isReplayableBody } from "./body";
import {
  createSessionManager,
  openSessionChannel,
  SessionEndedError,
  type KeyValueStorage,
  type LockManagerLike,
} from "./session";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

const REFRESH_TIMEOUT_MS = 10_000;

function browserStorage(area: "localStorage" | "sessionStorage"): KeyValueStorage | null {
  try {
    return window[area];
  } catch {
    return null;
  }
}

function browserLocks(): LockManagerLike | null {
  return typeof navigator !== "undefined" && navigator.locks ? navigator.locks : null;
}

// Sessão partilhada por toda a app (ver api/session.ts).
export const session = createSessionManager({
  storage: browserStorage("localStorage"),
  tabStorage: browserStorage("sessionStorage"),
  // Sem BroadcastChannel a sessão funciona na mesma; só perde a sincronização
  // entre separadores no modo curto (ver api/session.ts).
  channel: openSessionChannel(typeof BroadcastChannel === "undefined" ? undefined : BroadcastChannel),
  locks: browserLocks(),
  refreshRequest: (refreshToken) =>
    fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
      signal: AbortSignal.timeout(REFRESH_TIMEOUT_MS),
    }),
});

// Revoga o refresh token no servidor (POST /api/auth/logout, público, com o
// token no corpo). Não usa o access token, por isso não importa se já
// expirou. Timeout de 3 s; qualquer falha (offline, erro) é ignorada: quem
// chama limpa sempre a sessão local.
export async function revokeRefreshToken(token: string, timeoutMs = 3000): Promise<void> {
  try {
    await fetch(`${API_BASE}/auth/logout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: token }),
      signal: AbortSignal.timeout(timeoutMs),
      keepalive: true,
    });
  } catch {
    // Sem rede ou timeout: o token expira sozinho no servidor.
  }
}

function send(path: string, options: RequestInit, accessToken: string | null): Promise<Response> {
  const headers = new Headers(options.headers);
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  if (options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  return fetch(`${API_BASE}${path}`, { ...options, headers });
}

// Wrapper de fetch para chamadas autenticadas. Junta o access token, e se a
// resposta vier 401 renova a sessão uma vez (renovação partilhada com os
// outros pedidos e separadores) e repete o pedido uma vez. Nunca em ciclo.
// Só repete se o corpo puder ser reenviado; com uma stream devolve o 401.
// Se a renovação falhar por rede, timeout ou 5xx, lança SessionUnavailableError
// e os tokens mantêm-se; se o servidor recusar o refresh token, a sessão
// termina (o AuthProvider leva ao login) e devolve o 401 original.
export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const sentToken = session.getAccessToken();
  const response = await send(path, options, sentToken);

  if (response.status !== 401 || !session.hasSession() || !isReplayableBody(options.body)) {
    return response;
  }

  try {
    await session.refresh(sentToken);
  } catch (err) {
    if (err instanceof SessionEndedError) return response;
    throw err;
  }
  return send(path, options, session.getAccessToken());
}

export { API_BASE };
