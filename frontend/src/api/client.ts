const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

let accessToken: string | null = null;
let refreshToken: string | null = null;

interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export function setTokens(tokens: TokenPair | null) {
  if (tokens) {
    accessToken = tokens.accessToken;
    refreshToken = tokens.refreshToken;
    localStorage.setItem("refreshToken", tokens.refreshToken);
  } else {
    accessToken = null;
    refreshToken = null;
    localStorage.removeItem("refreshToken");
  }
}

export function getRefreshToken(): string | null {
  return refreshToken;
}

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

export function loadStoredRefreshToken(): string | null {
  refreshToken = localStorage.getItem("refreshToken");
  return refreshToken;
}

async function refreshAccessToken(): Promise<boolean> {
  if (!refreshToken) return false;

  const response = await fetch(`${API_BASE}/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });

  if (!response.ok) {
    setTokens(null);
    return false;
  }

  const data = await response.json();
  setTokens(data);
  return true;
}

// Wrapper de fetch para chamadas autenticadas. Junta o access token, e se a
// resposta vier 401 (token expirado), tenta renovar uma vez com o refresh
// token antes de desistir.
export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const headers = new Headers(options.headers);
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  if (options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  let response = await fetch(`${API_BASE}${path}`, { ...options, headers });

  if (response.status === 401 && refreshToken) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      headers.set("Authorization", `Bearer ${accessToken}`);
      response = await fetch(`${API_BASE}${path}`, { ...options, headers });
    }
  }

  return response;
}

export { API_BASE };
