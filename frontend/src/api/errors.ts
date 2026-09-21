// Erro para mostrar ao utilizador: título curto (frontend) e detalhe
// opcional (normalmente a mensagem do backend), mostrado em texto secundário.
export class AppError extends Error {
  detail?: string;

  constructor(title: string, detail?: string) {
    super(title);
    this.name = "AppError";
    this.detail = detail;
  }
}

export interface NoticeContent {
  title: string;
  detail?: string;
}

// Lê a mensagem de erro do backend ({ "error": "..." }), se houver.
export async function readApiError(res: Response): Promise<string | undefined> {
  const body = await res.json().catch(() => ({}));
  return typeof body.error === "string" ? body.error : undefined;
}

// Constrói um AppError a partir de uma resposta falhada. stripPrefix remove
// um prefixo que repetiria o título (ex.: "Não foi possível ler o ficheiro: ").
export async function apiFailure(res: Response, title: string, stripPrefix?: string): Promise<AppError> {
  let detail = await readApiError(res);
  if (detail && stripPrefix && detail.startsWith(stripPrefix)) {
    detail = detail.slice(stripPrefix.length);
  }
  return new AppError(title, detail);
}

// Converte qualquer erro apanhado num aviso; erros inesperados (rede, etc.)
// ficam com uma mensagem genérica.
export function toNotice(err: unknown): NoticeContent {
  if (err instanceof AppError) return { title: err.message, detail: err.detail };
  return { title: "Algo correu mal. Tenta de novo." };
}
