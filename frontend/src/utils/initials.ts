// Iniciais para o botão do menu de utilizador, a partir da parte local do
// email: "miguel.diegues@…" → "MD"; "migueldiegues24@…" → "MI".
export function initialsFromEmail(email: string | null | undefined): string {
  const local = email?.split("@")[0]?.replace(/\d+/g, "") ?? "";
  const parts = local.split(/[._\-+]+/).filter(Boolean);
  const letters = parts.length >= 2 ? parts[0][0] + parts[1][0] : local.slice(0, 2);
  return letters ? letters.toLocaleUpperCase("pt-PT") : "?";
}
