// Sessão ativa, como vem de GET /api/account/sessions. createdAt é a última
// renovação da sessão (o refresh token roda a cada renovação), não o login.
export interface AccountSession {
  id: number;
  createdAt: string;
  expiresAt: string;
  mode: "REMEMBERED" | "SHORT";
  current: boolean;
}

// A atual primeiro; as restantes da mais recente para a mais antiga.
export function sortSessions(sessions: AccountSession[]): AccountSession[] {
  return [...sessions].sort((a, b) => {
    if (a.current !== b.current) return a.current ? -1 : 1;
    return Date.parse(b.createdAt) - Date.parse(a.createdAt);
  });
}

export function sessionModeLabel(mode: AccountSession["mode"]): string {
  return mode === "REMEMBERED" ? "Sessão mantida" : "Sessão curta";
}

const dateTimeFormat = new Intl.DateTimeFormat("pt-PT", { dateStyle: "medium", timeStyle: "short" });

// Data e hora locais em pt-PT; texto original se não for uma data válida.
export function formatDateTime(iso: string): string {
  const time = Date.parse(iso);
  return Number.isNaN(time) ? iso : dateTimeFormat.format(time);
}
