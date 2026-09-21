// Euros em pt-PT: "1 234,56 €". useGrouping "always" agrupa também os
// valores de 4 dígitos (pt-PT por omissão só agrupa a partir de 5), para
// todas as linhas de uma coluna terem o mesmo formato. Os espaços são
// não separáveis (U+00A0), por isso o valor nunca parte a linha.
// signDisplay "negative": zero (incluindo -0 e valores que arredondam a
// zero) aparece sem sinal.
const currencyFormat = new Intl.NumberFormat("pt-PT", {
  style: "currency",
  currency: "EUR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  useGrouping: "always",
  signDisplay: "negative",
});

// Sinal de menos tipográfico, o mesmo que a app usa noutros sítios. Fica
// colado ao número (sem espaço), e U+2212 antes de um algarismo não é ponto
// de quebra de linha (UAX #14, LB25).
export const MINUS = "−";

export function formatCurrency(value: number): string {
  return currencyFormat
    .formatToParts(value)
    .map((part) => (part.type === "minusSign" ? MINUS : part.value))
    .join("");
}
