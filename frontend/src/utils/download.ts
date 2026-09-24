// Nome do ficheiro no Content-Disposition (attachment; filename="x.json").
// Só aceita nomes simples: sem caminhos nem caracteres de controlo.
export function filenameFromDisposition(header: string | null, fallback: string): string {
  const match = header?.match(/filename="([^"]+)"/i) ?? header?.match(/filename=([^;\s]+)/i);
  const name = match?.[1]?.trim();
  if (!name || /[\\/\p{Cc}]/u.test(name)) return fallback;
  return name;
}

// Descarrega um Blob com o nome dado (link temporário com download).
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.hidden = true;
  document.body.append(link);
  link.click();
  link.remove();
  // Dá tempo ao browser de começar a transferência antes de libertar o URL.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
