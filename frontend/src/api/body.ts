// Um pedido só pode ser repetido (após renovar a sessão) se o corpo puder ser
// enviado outra vez. Uma stream é consumida no primeiro envio.
export function isReplayableBody(body: BodyInit | null | undefined): boolean {
  return (
    body === undefined ||
    body === null ||
    typeof body === "string" ||
    body instanceof FormData ||
    body instanceof Blob
  );
}
