import { COLOR_SWATCHES } from "./categoryPalette.ts";
import { isHexColor } from "./displayColor.ts";

// Escolha de cor no diálogo: automática (null), uma amostra, ou personalizada.
export type ColorChoice = { kind: "auto" } | { kind: "swatch"; hex: string } | { kind: "custom"; hex: string };

// Cor vinda da API: ausente, null ou inválida = automática.
export function choiceFromStored(color: unknown): ColorChoice {
  if (!isHexColor(color)) return { kind: "auto" };
  const upper = color.toUpperCase();
  return COLOR_SWATCHES.some((s) => s.hex === upper) ? { kind: "swatch", hex: upper } : { kind: "custom", hex: upper };
}

// Valor a enviar à API (o PUT substitui a categoria inteira).
export function storedFromChoice(choice: ColorChoice): string | null {
  return choice.kind === "auto" ? null : choice.hex.toUpperCase();
}
