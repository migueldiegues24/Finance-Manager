// Cor de apresentação de uma categoria: parte da cor guardada pelo
// utilizador e, se for preciso, ajusta só a luminosidade em OKLCH (mantém a
// matiz) até ser legível no tema atual:
//   texto >= 4,5:1 sobre o fundo, row-alt, row-hover e a própria tinta a 10%
//   (fundo das etiquetas); pontos e barras >= 3:1 sobre as superfícies.
// Sem dependências: conversões de Björn Ottosson (OKLab) e WCAG 2.x.

export type Theme = "light" | "dark";

// Mesma regra do backend (CategoryColors) e do CHECK da BD. Só uma cor que
// passe isto é aplicada ao CSS; nunca texto do utilizador.
export const HEX_COLOR = /^#[0-9A-Fa-f]{6}$/;

export function isHexColor(value: unknown): value is string {
  return typeof value === "string" && HEX_COLOR.test(value);
}

// Tem de coincidir com --color-bg e --color-ink de index.css (há um teste).
export const THEME_BASE: Record<Theme, { bg: string; ink: string }> = {
  light: { bg: "#EEEFEA", ink: "#1B2A4A" },
  dark: { bg: "#131C30", ink: "#E7E4DA" },
};

export const TEXT_MIN = 4.5;
export const GRAPHIC_MIN = 3;
const MAX_STEPS = 100; // passos de 0,01 em L: cobre todo o intervalo 0–1
const TINT = 0.1;

type RGB = [number, number, number]; // sRGB 0–1

function hexToRgb(hex: string): RGB {
  const n = Number.parseInt(hex.slice(1), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

function rgbToHex([r, g, b]: RGB): string {
  const to = (v: number) => Math.round(Math.min(1, Math.max(0, v)) * 255).toString(16).padStart(2, "0");
  return `#${to(r)}${to(g)}${to(b)}`.toUpperCase();
}

const toLinear = (v: number) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4);
const toGamma = (v: number) => (v <= 0.0031308 ? 12.92 * v : 1.055 * v ** (1 / 2.4) - 0.055);

// color-mix(in srgb, a p, b) mistura em sRGB com gama, como o CSS.
function mix(a: RGB, b: RGB, p: number): RGB {
  return [a[0] * p + b[0] * (1 - p), a[1] * p + b[1] * (1 - p), a[2] * p + b[2] * (1 - p)];
}

function luminance(rgb: RGB): number {
  const [r, g, b] = rgb.map(toLinear);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrastRatio(a: string, b: string): number {
  return contrast(hexToRgb(a), hexToRgb(b));
}

function contrast(a: RGB, b: RGB): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

interface Oklch {
  l: number;
  c: number;
  h: number; // graus
}

export function toOklch(hex: string): Oklch {
  const [r, g, b] = hexToRgb(hex).map(toLinear);
  const l_ = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m_ = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s_ = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  const L = 0.2104542553 * l_ + 0.793617785 * m_ - 0.0040720468 * s_;
  const A = 1.9779984951 * l_ - 2.428592205 * m_ + 0.4505937099 * s_;
  const B = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.808675766 * s_;
  const h = (Math.atan2(B, A) * 180) / Math.PI;
  return { l: L, c: Math.hypot(A, B), h: h < 0 ? h + 360 : h };
}

// OKLCH → sRGB linear (pode sair do gamut).
function oklchToLinear({ l, c, h }: Oklch): RGB {
  const A = c * Math.cos((h * Math.PI) / 180);
  const B = c * Math.sin((h * Math.PI) / 180);
  const l_ = (l + 0.3963377774 * A + 0.2158037573 * B) ** 3;
  const m_ = (l - 0.1055613458 * A - 0.0638541728 * B) ** 3;
  const s_ = (l - 0.0894841775 * A - 1.291485548 * B) ** 3;
  return [
    4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_,
    -1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_,
    -0.0041960863 * l_ - 0.7034186147 * m_ + 1.707614701 * s_,
  ];
}

const inGamut = (rgb: RGB) => rgb.every((v) => v >= -1e-4 && v <= 1 + 1e-4);

// Mantém L e h; se a cor sair do sRGB, reduz o croma (pesquisa binária).
function fromOklch(color: Oklch): RGB {
  let linear = oklchToLinear(color);
  if (!inGamut(linear)) {
    let lo = 0;
    let hi = color.c;
    for (let i = 0; i < 24; i++) {
      const mid = (lo + hi) / 2;
      if (inGamut(oklchToLinear({ ...color, c: mid }))) lo = mid;
      else hi = mid;
    }
    linear = oklchToLinear({ ...color, c: lo });
  }
  return linear.map((v) => toGamma(Math.min(1, Math.max(0, v)))) as RGB;
}

function surfaces(theme: Theme): RGB[] {
  const bg = hexToRgb(THEME_BASE[theme].bg);
  const ink = hexToRgb(THEME_BASE[theme].ink);
  return [bg, mix(ink, bg, 0.05), mix(ink, bg, 0.09)];
}

// A cor cumpre os mínimos de texto (e, por consequência, os de gráfico) no tema?
export function meetsContrast(hex: string, theme: Theme): boolean {
  const color = hexToRgb(hex);
  const bg = hexToRgb(THEME_BASE[theme].bg);
  const tint = mix(color, bg, TINT);
  return (
    surfaces(theme).every((s) => contrast(color, s) >= TEXT_MIN && contrast(color, s) >= GRAPHIC_MIN) &&
    contrast(color, tint) >= TEXT_MIN
  );
}

export interface DisplayColor {
  color: string; // #RRGGBB, sempre válido
  adjusted: boolean;
}

export function displayCategoryColor(stored: unknown, theme: Theme): DisplayColor | null {
  if (!isHexColor(stored)) return null;
  const original = stored.toUpperCase();
  if (meetsContrast(original, theme)) return { color: original, adjusted: false };

  // No claro escurece, no escuro clareia; a matiz mantém-se.
  const start = toOklch(original);
  const direction = theme === "light" ? -1 : 1;
  for (let step = 1; step <= MAX_STEPS; step++) {
    const l = Math.min(1, Math.max(0, start.l + direction * 0.01 * step));
    const candidate = rgbToHex(fromOklch({ ...start, l }));
    if (meetsContrast(candidate, theme)) return { color: candidate, adjusted: true };
    if (l === 0 || l === 1) break;
  }
  // Nunca deve acontecer, mas termina sempre numa cor válida e legível.
  return { color: theme === "light" ? "#000000" : "#FFFFFF", adjusted: true };
}
