import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { API_BASE, loadStoredRefreshToken, setTokens } from "../api/client";

interface AuthContextValue {
  isAuthenticated: boolean;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

async function requestTokens(path: "login" | "register", email: string, password: string) {
  const response = await fetch(`${API_BASE}/auth/${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.error ?? "Algo correu mal");
  }

  return response.json();
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);

  // Ao carregar a app, se houver um refresh token guardado, tenta trocá-lo
  // por um access token novo, para o utilizador não ter de fazer login outra
  // vez a cada refresh de página.
  useEffect(() => {
    const stored = loadStoredRefreshToken();
    if (!stored) {
      setLoading(false);
      return;
    }

    fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: stored }),
    })
      .then(async (res) => {
        if (!res.ok) {
          setTokens(null);
          return;
        }
        const data = await res.json();
        setTokens(data);
        setIsAuthenticated(true);
      })
      .finally(() => setLoading(false));
  }, []);

  async function login(email: string, password: string) {
    const data = await requestTokens("login", email, password);
    setTokens(data);
    setIsAuthenticated(true);
  }

  async function register(email: string, password: string) {
    const data = await requestTokens("register", email, password);
    setTokens(data);
    setIsAuthenticated(true);
  }

  function logout() {
    setTokens(null);
    setIsAuthenticated(false);
  }

  return (
    <AuthContext.Provider value={{ isAuthenticated, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth deve ser usado dentro de AuthProvider");
  return ctx;
}
