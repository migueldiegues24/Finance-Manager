import { useId, type CSSProperties } from "react";
import { useTheme } from "../theme/themeContext";
import { AUTO_CATEGORY_COLORS, COLOR_SWATCHES, autoPaletteIndex } from "../utils/categoryPalette";
import type { ColorChoice } from "../utils/colorChoice";
import { THEME_BASE, displayCategoryColor, isHexColor, type Theme } from "../utils/displayColor";
import "./ColorPicker.css";

interface ColorPickerProps {
  value: ColorChoice;
  onChange: (choice: ColorChoice) => void;
  // Para a pré-visualização: nome e id (a cor automática depende do id).
  previewName: string;
  categoryId: number | null;
}

const DEFAULT_CUSTOM = "#2F5D8A";

// Grupo de rádios nativos (setas mudam a escolha, Tab entra e sai do grupo),
// campo de cor personalizada, botão para voltar à cor automática e
// pré-visualização da etiqueta e do ponto nos dois temas.
export default function ColorPicker({ value, onChange, previewName, categoryId }: ColorPickerProps) {
  const { theme } = useTheme();
  const group = useId();
  const customInputId = useId();
  const hintId = useId();
  const selectedHex = value.kind === "auto" ? null : value.hex;

  // Cor mostrada em cada tema (hex sempre validado; nunca texto do utilizador).
  const shown = (t: Theme): { color: string; adjusted: boolean } => {
    if (selectedHex) return displayCategoryColor(selectedHex, t) ?? { color: THEME_BASE[t].ink, adjusted: false };
    if (categoryId !== null) return { color: AUTO_CATEGORY_COLORS[t][autoPaletteIndex(categoryId)], adjusted: false };
    return { color: THEME_BASE[t].ink, adjusted: false };
  };
  const light = shown("light");
  const dark = shown("dark");
  const hint =
    light.adjusted && dark.adjusted
      ? "Tom ajustado para ser legível nos dois temas."
      : light.adjusted
        ? "Tom ajustado para ser legível no tema claro."
        : dark.adjusted
          ? "Tom ajustado para ser legível no tema escuro."
          : "";

  return (
    <fieldset className="color-picker" aria-describedby={hint ? hintId : undefined}>
      <legend className="field-label">Cor</legend>

      <div className="color-picker__swatches">
        {COLOR_SWATCHES.map((swatch) => {
          const checked = value.kind === "swatch" && value.hex === swatch.hex;
          const chip = displayCategoryColor(swatch.hex, theme)!.color;
          return (
            <label key={swatch.hex} className="color-swatch" title={swatch.name} style={{ "--swatch": chip } as CSSProperties}>
              <input
                type="radio"
                name={group}
                className="visually-hidden"
                checked={checked}
                onChange={() => onChange({ kind: "swatch", hex: swatch.hex })}
              />
              <span className="color-swatch__chip" aria-hidden="true">
                {checked ? "✓" : ""}
              </span>
              <span className="visually-hidden">{swatch.name}</span>
            </label>
          );
        })}
        <label className="color-swatch color-swatch--custom">
          <input
            type="radio"
            name={group}
            className="visually-hidden"
            checked={value.kind === "custom"}
            onChange={() => onChange({ kind: "custom", hex: selectedHex ?? DEFAULT_CUSTOM })}
          />
          <span className="color-swatch__label">Personalizada</span>
        </label>
      </div>

      {value.kind === "custom" && (
        <div className="color-picker__custom">
          <label className="field-label" htmlFor={customInputId}>
            Cor personalizada
          </label>
          <input
            id={customInputId}
            type="color"
            className="color-picker__input"
            value={value.hex.toLowerCase()}
            onChange={(event) => {
              const hex = event.target.value;
              if (isHexColor(hex)) onChange({ kind: "custom", hex: hex.toUpperCase() });
            }}
          />
          <span className="color-picker__hex">{value.hex}</span>
        </div>
      )}

      <div className="color-picker__actions">
        <button
          type="button"
          className="btn btn--link"
          onClick={() => onChange({ kind: "auto" })}
          disabled={value.kind === "auto"}
        >
          Usar cor automática
        </button>
      </div>

      <div className="color-picker__preview" aria-hidden="true">
        {(["light", "dark"] as const).map((t) => {
          const base = THEME_BASE[t];
          const c = t === "light" ? light.color : dark.color;
          return (
            <div
              key={t}
              className="color-picker__preview-box"
              style={{ "--color-bg": base.bg, "--color-ink": base.ink, "--category-color": c } as CSSProperties}
            >
              <span className="color-picker__preview-theme">{t === "light" ? "Claro" : "Escuro"}</span>
              <span className="tag">{previewName || "Categoria"}</span>
              <span className="label-with-dot">
                <span className="dot" />
                {previewName || "Categoria"}
              </span>
            </div>
          );
        })}
      </div>

      <p className="status__detail color-picker__hint" id={hintId} aria-live="polite">
        {hint}
      </p>
    </fieldset>
  );
}
