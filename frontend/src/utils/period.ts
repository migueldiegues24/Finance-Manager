import { currentMonth } from "./date";

// Períodos no formato do URL. Por agora só existe o mês ("YYYY-MM"); a
// vista anual vai acrescentar ?year=YYYY com o mesmo tipo de validação.

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

export function isValidMonth(value: string | null): value is string {
  return value !== null && MONTH_PATTERN.test(value);
}

// "YYYY-MM" compara bem como texto.
export function isFutureMonth(month: string, today: string = currentMonth()): boolean {
  return month > today;
}

// Valor do parâmetro ?month, ou null se faltar, for inválido ou for futuro.
export function parseMonthParam(value: string | null): string | null {
  return isValidMonth(value) && !isFutureMonth(value) ? value : null;
}

export function monthYear(month: string): number {
  return Number(month.slice(0, 4));
}

// index: 0 = janeiro.
export function monthOf(year: number, index: number): string {
  return `${year}-${String(index + 1).padStart(2, "0")}`;
}

export function monthIndex(month: string): number {
  return Number(month.slice(5, 7)) - 1;
}
