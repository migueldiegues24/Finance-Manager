import type { CSSProperties } from "react";

// Número de cores em --color-cat-1..N (index.css).
const CATEGORY_COLOR_COUNT = 8;

// Cor determinística por id: estável mesmo que a categoria seja renomeada.
// Quando o backend guardar uma cor por categoria, basta trocar esta função.
export function categoryColor(id: number): string {
  const index = (Math.abs(Math.trunc(id)) % CATEGORY_COLOR_COUNT) + 1;
  return `var(--color-cat-${index})`;
}

// Estilo inline que expõe a cor como --category-color, usada por .dot e .tag.
export function categoryStyle(id: number): CSSProperties {
  return { "--category-color": categoryColor(id) } as CSSProperties;
}
