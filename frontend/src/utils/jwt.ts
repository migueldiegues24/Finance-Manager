// Lê o "sub" (email) do payload de um JWT, só para mostrar na interface.
// NÃO valida a assinatura nem a validade: não usar para decisões de acesso.
// O backend continua a ser a única fonte de verdade sobre a sessão.
export function decodeJwtSubject(token: string | null | undefined): string | null {
  if (!token) return null;
  const payload = token.split(".")[1];
  if (!payload) return null;
  try {
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(payload.length / 4) * 4, "=");
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const claims = JSON.parse(new TextDecoder().decode(bytes));
    return typeof claims.sub === "string" && claims.sub ? claims.sub : null;
  } catch {
    return null;
  }
}
