import { apiFetch } from "./client";
import { AppError, apiFailure } from "./errors";
import type { TokenPair } from "./session";
import { filenameFromDisposition } from "../utils/download";
import type { AccountSession } from "../utils/sessions";

// Endpoints de /api/account. Uma password errada vem como 400 (não 401, que
// o apiFetch trataria como sessão expirada); o 429 traz a espera na mensagem.

// Devolve o par novo desta sessão: o servidor revoga todas as outras.
export async function changePassword(currentPassword: string, newPassword: string): Promise<TokenPair> {
  const res = await apiFetch("/account/password", {
    method: "PUT",
    body: JSON.stringify({ currentPassword, newPassword }),
  });
  if (!res.ok) throw await apiFailure(res, "Não foi possível mudar a password.");
  return res.json();
}

export async function listSessions(): Promise<AccountSession[]> {
  const res = await apiFetch("/account/sessions");
  if (!res.ok) throw await apiFailure(res, "Não foi possível carregar as sessões.");
  return res.json();
}

export async function revokeSession(id: number): Promise<void> {
  const res = await apiFetch(`/account/sessions/${id}`, { method: "DELETE" });
  if (!res.ok) throw await apiFailure(res, "Não foi possível terminar a sessão.");
}

// Devolve quantas sessões terminaram.
export async function revokeOtherSessions(): Promise<number> {
  const res = await apiFetch("/account/sessions/revoke-others", { method: "POST" });
  if (!res.ok) throw await apiFailure(res, "Não foi possível terminar as outras sessões.");
  const body: { revoked?: unknown } = await res.json();
  return typeof body.revoked === "number" ? body.revoked : 0;
}

export async function exportAccountData(): Promise<{ blob: Blob; filename: string }> {
  const res = await apiFetch("/account/export");
  if (!res.ok) throw await apiFailure(res, "Não foi possível exportar os dados.");
  try {
    const blob = await res.blob();
    return { blob, filename: filenameFromDisposition(res.headers.get("Content-Disposition"), "finance-manager.json") };
  } catch {
    throw new AppError("Não foi possível exportar os dados.", "A transferência foi interrompida. Tenta de novo.");
  }
}

export async function deleteAccount(password: string): Promise<void> {
  const res = await apiFetch("/account", { method: "DELETE", body: JSON.stringify({ password }) });
  if (!res.ok) throw await apiFailure(res, "Não foi possível apagar a conta.");
}
