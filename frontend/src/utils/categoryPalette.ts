import type { Theme } from "./displayColor";

// Paleta automática (id % 8), igual a --color-cat-1..8 de index.css nos dois
// temas; categoryPalette.test.ts verifica que coincidem. Serve para
// pré-visualizar a cor automática no tema que não está ativo.
export const AUTO_CATEGORY_COLORS: Record<Theme, readonly string[]> = {
  light: ["#2F5D8A", "#1F6B64", "#6B4E9B", "#8A4619", "#63591A", "#8E3B6B", "#535A70", "#7B5B3A"],
  dark: ["#8FB3E0", "#72C2B6", "#B8A2E3", "#E4A077", "#C9BD6A", "#E59AC4", "#A9B1C7", "#CDAE8A"],
};

export function autoPaletteIndex(id: number): number {
  return Math.abs(Math.trunc(id)) % AUTO_CATEGORY_COLORS.light.length;
}

// Amostras do diálogo: tons sóbrios (os 8 da paleta automática e mais 6).
// Os valores são os guardados; a apresentação ajusta-os ao tema.
export const COLOR_SWATCHES: readonly { hex: string; name: string }[] = [
  { hex: "#2F5D8A", name: "Cobalto" },
  { hex: "#1F4E79", name: "Azul-marinho" },
  { hex: "#4B3F72", name: "Índigo" },
  { hex: "#6B4E9B", name: "Violeta" },
  { hex: "#8E3B6B", name: "Magenta" },
  { hex: "#6E3F5A", name: "Ameixa" },
  { hex: "#8A4619", name: "Terracota" },
  { hex: "#A0522D", name: "Siena" },
  { hex: "#8B6F1E", name: "Mostarda" },
  { hex: "#63591A", name: "Azeitona" },
  { hex: "#3E6E3A", name: "Musgo" },
  { hex: "#1F6B64", name: "Verde-azulado" },
  { hex: "#7B5B3A", name: "Sépia" },
  { hex: "#535A70", name: "Ardósia" },
];
