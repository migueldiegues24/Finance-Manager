// Euros em pt-PT: "1 234,56 €". useGrouping "always" agrupa também os
// valores de 4 dígitos (pt-PT por omissão só agrupa a partir de 5), para
// todas as linhas de uma coluna terem o mesmo formato. Os espaços são
// não separáveis (U+00A0), por isso o valor nunca parte a linha.
// O sinal (+/−) é acrescentado por quem chama, com o valor absoluto.
const currencyFormat = new Intl.NumberFormat("pt-PT", {
  style: "currency",
  currency: "EUR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  useGrouping: "always",
});

export function formatCurrency(value: number): string {
  return currencyFormat.format(value);
}
