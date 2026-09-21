// Lê o payload de um JWT sem validar a assinatura nem a validade.
function decodeJwtPayload(token: string | null | undefined): Record<string, unknown> | null {
  if (!token) return null;
  const payload = token.split(".")[1];
  if (!payload) return null;
  try {
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(payload.length / 4) * 4, "=");
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const claims = JSON.parse(new TextDecoder().decode(bytes));
    return claims && typeof claims === "object" ? claims : null;
  } catch {
    return null;
  }
}

// Lê o "sub" (email) do payload de um JWT, só para mostrar na interface.
// NÃO valida a assinatura nem a validade: não usar para decisões de acesso.
// O backend continua a ser a única fonte de verdade sobre a sessão.
export function decodeJwtSubject(token: string | null | undefined): string | null {
  const sub = decodeJwtPayload(token)?.sub;
  return typeof sub === "string" && sub ? sub : null;
}

// Lê o "exp" de um JWT, em milissegundos. Serve só para evitar renovações
// desnecessárias; se o servidor recusar o token, o 401 força a renovação.
export function decodeJwtExpiry(token: string | null | undefined): number | null {
  const exp = decodeJwtPayload(token)?.exp;
  return typeof exp === "number" && Number.isFinite(exp) ? exp * 1000 : null;
}
