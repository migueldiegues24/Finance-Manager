import type { CSSProperties } from "react";
import { autoPaletteIndex } from "./categoryPalette.ts";
import { displayCategoryColor, type Theme } from "./displayColor.ts";

// Cor automática: determinística por id (--color-cat-1..8), estável mesmo
// que a categoria seja renomeada.
export function categoryColor(id: number): string {
  return `var(--color-cat-${autoPaletteIndex(id) + 1})`;
}

// Estilo inline que expõe a cor como --category-color, usada por .dot, .tag
// e pelas barras. Com uma cor guardada válida (#RRGGBB), usa a versão
// ajustada ao tema; sem cor, com null ou com um valor inválido, usa a
// automática. Nunca passa texto do utilizador para o CSS.
export function categoryStyle(id: number, stored?: unknown, theme: Theme = "light"): CSSProperties {
  const display = displayCategoryColor(stored, theme);
  return { "--category-color": display ? display.color : categoryColor(id) } as CSSProperties;
}
