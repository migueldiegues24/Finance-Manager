import { useCallback, useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from "react";
import {
  DARK_QUERY,
  applyTheme,
  readStoredMode,
  resolveTheme,
  storeMode,
  type ThemeMode,
} from "../utils/theme";
import { ThemeContext } from "./themeContext";

function safeStorage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

// Preferência do sistema, atualizada quando muda (ex.: modo noturno automático).
function subscribeToSystem(onChange: () => void) {
  const query = window.matchMedia(DARK_QUERY);
  query.addEventListener("change", onChange);
  return () => query.removeEventListener("change", onChange);
}

function systemPrefersDark() {
  return window.matchMedia(DARK_QUERY).matches;
}

export default function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(() => readStoredMode(safeStorage()));
  const prefersDark = useSyncExternalStore(subscribeToSystem, systemPrefersDark);
  const theme = resolveTheme(mode, prefersDark);

  // Mantém o <html> em sincronia (o script do index.html já aplicou o
  // valor inicial, por isso aqui não há flash).
  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  const setMode = useCallback((next: ThemeMode) => {
    setModeState(next);
    storeMode(safeStorage(), next);
  }, []);

  const value = useMemo(() => ({ mode, theme, setMode }), [mode, theme, setMode]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
