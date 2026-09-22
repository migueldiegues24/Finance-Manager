import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { API_BASE, revokeRefreshToken, session } from "../api/client";
import type { TokenPair } from "../api/session";
import SessionOffline from "../components/SessionOffline";
import { decodeJwtSubject } from "../utils/jwt";

interface AuthContextValue {
  isAuthenticated: boolean;
  loading: boolean;
  // Email lido do access token, só para mostrar (ver utils/jwt.ts).
  email: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

// loading: a restaurar a sessão guardada; offline: falha transitória no
// arranque (tokens mantidos, ecrã "Sem ligação"); ready: estado conhecido.
type Status = "loading" | "offline" | "ready";

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

async function requestTokens(path: "login" | "register", email: string, password: string): Promise<TokenPair> {
  const response = await fetch(`${API_BASE}/auth/${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.error ?? "Algo correu mal. Tenta de novo.");
  }

  return response.json();
}

function isLocalStorage(area: Storage | null): boolean {
  try {
    return area === window.localStorage;
  } catch {
    return false;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>("loading");
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [email, setEmail] = useState<string | null>(null);
  const [retrying, setRetrying] = useState(false);

  // Restaura a sessão guardada: com access token válido não faz pedido; senão
  // renova (uma só vez, mesmo com o StrictMode a correr o efeito duas vezes,
  // porque as chamadas partilham a mesma renovação).
  const restore = useCallback(
    () =>
      session.restore().then(
        (active) => {
          setIsAuthenticated(active);
          setEmail(active ? decodeJwtSubject(session.getAccessToken()) : null);
          setStatus("ready");
        },
        () => setStatus("offline"),
      ),
    [],
  );

  useEffect(() => {
    void restore();
  }, [restore]);

  // Mudanças vindas de renovações ou de outros separadores.
  useEffect(() => {
    const unsubscribe = session.subscribe((event) => {
      if (event === "user-changed") {
        // Outro separador entrou com outra conta: descarta tudo o que está em
        // memória (dados da conta anterior) e recomeça com a sessão guardada.
        setIsAuthenticated(false);
        setEmail(null);
        window.location.reload();
        return;
      }
      if (event === "ended") {
        setIsAuthenticated(false);
        setEmail(null);
      } else {
        setIsAuthenticated(true);
        setEmail(decodeJwtSubject(session.getAccessToken()));
      }
      setStatus("ready");
    });

    function onStorage(event: StorageEvent) {
      if (event.storageArea && !isLocalStorage(event.storageArea)) return;
      session.handleStorageChange(event.key, event.newValue);
    }
    window.addEventListener("storage", onStorage);

    return () => {
      unsubscribe();
      window.removeEventListener("storage", onStorage);
    };
  }, []);

  // Sem ligação no arranque: tenta sozinho quando a rede voltar.
  useEffect(() => {
    if (status !== "offline") return;
    const onOnline = () => void restore();
    window.addEventListener("online", onOnline);
    return () => window.removeEventListener("online", onOnline);
  }, [status, restore]);

  async function retry() {
    setRetrying(true);
    try {
      await restore();
    } finally {
      setRetrying(false);
    }
  }

  async function login(email: string, password: string) {
    const data = await requestTokens("login", email, password);
    session.setTokens(data);
    setEmail(decodeJwtSubject(data.accessToken));
    setIsAuthenticated(true);
  }

  async function register(email: string, password: string) {
    const data = await requestTokens("register", email, password);
    session.setTokens(data);
    setEmail(decodeJwtSubject(data.accessToken));
    setIsAuthenticated(true);
  }

  // Tenta revogar o refresh token no servidor (até ~3 s) e limpa sempre a
  // sessão local, mesmo que o pedido falhe ou não haja rede. Os outros
  // separadores recebem o evento storage e saem também.
  async function logout() {
    const token = session.getRefreshToken();
    try {
      if (token) await revokeRefreshToken(token);
    } finally {
      session.clear();
      setEmail(null);
      setIsAuthenticated(false);
    }
  }

  if (status === "offline") {
    return <SessionOffline retrying={retrying} onRetry={retry} />;
  }

  return (
    <AuthContext.Provider
      value={{ isAuthenticated, loading: status === "loading", email, login, register, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth deve ser usado dentro de AuthProvider");
  return ctx;
}
