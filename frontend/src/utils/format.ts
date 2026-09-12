export function formatCurrency(value: number): string {
  return new Intl.NumberFormat("pt-PT", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}
