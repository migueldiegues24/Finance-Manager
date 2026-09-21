// Lê a mensagem de erro do backend ({ "error": "..." }), se houver.
export async function readApiError(res: Response): Promise<string | undefined> {
  const body = await res.json().catch(() => ({}));
  return typeof body.error === "string" ? body.error : undefined;
}
