import { createContext, useContext } from "react";
import type { Theme, ThemeMode } from "../utils/theme";

export interface ThemeContextValue {
  // Escolha do utilizador (Sistema / Claro / Escuro).
  mode: ThemeMode;
  // Tema aplicado neste momento.
  theme: Theme;
  setMode: (mode: ThemeMode) => void;
}

export const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useTheme deve ser usado dentro de ThemeProvider");
  return ctx;
}
