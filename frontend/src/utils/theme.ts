// Tema da app: modo escolhido (Sistema / Claro / Escuro) e tema efetivo.
// O script inline do index.html replica a parte mínima desta lógica para
// aplicar o tema antes do primeiro render; utils/theme.test.ts verifica
// que a chave e o atributo coincidem.

export type ThemeMode = "system" | "light" | "dark";
export type Theme = "light" | "dark";

export const THEME_MODES: readonly ThemeMode[] = ["system", "light", "dark"];
export const THEME_STORAGE_KEY = "theme";
export const THEME_ATTRIBUTE = "data-theme";
export const DARK_QUERY = "(prefers-color-scheme: dark)";

// Qualquer valor desconhecido (ou ausente) é tratado como "system".
export function parseThemeMode(value: unknown): ThemeMode {
  return value === "light" || value === "dark" || value === "system" ? value : "system";
}

export function resolveTheme(mode: ThemeMode, systemPrefersDark: boolean): Theme {
  if (mode === "system") return systemPrefersDark ? "dark" : "light";
  return mode;
}

type ReadableStorage = Pick<Storage, "getItem">;
type WritableStorage = Pick<Storage, "setItem">;

// O localStorage pode não existir ou lançar (modo privado, cookies
// bloqueados); nesse caso usa-se o modo por omissão.
export function readStoredMode(storage: ReadableStorage | null | undefined): ThemeMode {
  try {
    return parseThemeMode(storage?.getItem(THEME_STORAGE_KEY));
  } catch {
    return "system";
  }
}

export function storeMode(storage: WritableStorage | null | undefined, mode: ThemeMode): void {
  try {
    storage?.setItem(THEME_STORAGE_KEY, mode);
  } catch {
    // Sem persistência: o tema continua a funcionar nesta sessão.
  }
}

export function applyTheme(theme: Theme, root: HTMLElement = document.documentElement): void {
  root.setAttribute(THEME_ATTRIBUTE, theme);
  root.style.colorScheme = theme;
}
