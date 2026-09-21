/// <reference types="node" />
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import {
  GRAPHIC_MIN,
  HEX_COLOR,
  TEXT_MIN,
  THEME_BASE,
  contrastRatio,
  displayCategoryColor,
  isHexColor,
  meetsContrast,
  toOklch,
  type Theme,
} from "./displayColor.ts";

const THEMES: Theme[] = ["light", "dark"];

function mix(a: string, b: string, p: number): string {
  const ch = (h: string, i: number) => Number.parseInt(h.slice(1 + i * 2, 3 + i * 2), 16);
  const v = [0, 1, 2].map((i) => Math.round(ch(a, i) * p + ch(b, i) * (1 - p)));
  return "#" + v.map((x) => x.toString(16).padStart(2, "0")).join("");
}

// Verificação independente dos mínimos pedidos, sobre as três superfícies e a tinta.
function assertReadable(color: string, theme: Theme) {
  const { bg, ink } = THEME_BASE[theme];
  const surfaces = [bg, mix(ink, bg, 0.05), mix(ink, bg, 0.09)];
  for (const s of surfaces) {
    assert.ok(contrastRatio(color, s) >= TEXT_MIN, `${color} texto sobre ${s} (${theme}): ${contrastRatio(color, s).toFixed(2)}`);
    assert.ok(contrastRatio(color, s) >= GRAPHIC_MIN);
  }
  assert.ok(contrastRatio(color, mix(color, bg, 0.1)) >= TEXT_MIN, `${color} sobre a própria tinta (${theme})`);
}

function hueDistance(a: number, b: number) {
  const d = Math.abs(a - b) % 360;
  return d > 180 ? 360 - d : d;
}

test("isHexColor usa a mesma regra do backend", () => {
  assert.equal(HEX_COLOR.source, "^#[0-9A-Fa-f]{6}$");
  for (const ok of ["#1F5E6B", "#abcdef"]) assert.ok(isHexColor(ok), ok);
  for (const bad of ["red", "#12", "#GGGGGG", "url(x)", "#ff0000;x", "", " #FF0000", "#FF0000\n", null, 42]) {
    assert.ok(!isHexColor(bad), String(bad));
    assert.equal(displayCategoryColor(bad, "light"), null);
  }
});

test("cor que já cumpre não é alterada", () => {
  assert.deepEqual(displayCategoryColor("#2f5d8a", "light"), { color: "#2F5D8A", adjusted: false });
  assert.deepEqual(displayCategoryColor("#8FB3E0", "dark"), { color: "#8FB3E0", adjusted: false });
});

test("cor pouco legível é ajustada, mantendo a matiz", () => {
  for (const [input, theme] of [["#FFFF00", "light"], ["#1F5E6B", "dark"], ["#101010", "dark"], ["#E07A5F", "light"]] as const) {
    const result = displayCategoryColor(input, theme)!;
    assert.equal(result.adjusted, true, `${input} ${theme}`);
    assertReadable(result.color, theme);
    const before = toOklch(input);
    const after = toOklch(result.color);
    if (before.c > 0.02 && after.c > 0.02) assert.ok(hueDistance(before.h, after.h) <= 2, `${input} → ${result.color} matiz`);
    assert.ok(theme === "light" ? after.l < before.l : after.l > before.l, "luminosidade no sentido certo");
  }
});

test("casos extremos: fundo de cada tema, preto e branco, nos dois temas", () => {
  const extremes = [THEME_BASE.light.bg, THEME_BASE.dark.bg, "#000000", "#FFFFFF", "#808080"];
  for (const theme of THEMES) {
    for (const input of extremes) {
      const result = displayCategoryColor(input, theme);
      assert.ok(result, `${input} ${theme}`);
      assert.ok(isHexColor(result.color), `${input} ${theme} → ${result.color}`);
      assertReadable(result.color, theme);
    }
  }
});

test("200 cores aleatórias (determinísticas) terminam sempre numa cor válida e legível", () => {
  let seed = 12345;
  const next = () => (seed = (seed * 1103515245 + 12345) % 2 ** 31) / 2 ** 31;
  for (let i = 0; i < 200; i++) {
    const hex = "#" + Math.floor(next() * 0xffffff).toString(16).padStart(6, "0").toUpperCase();
    for (const theme of THEMES) {
      const result = displayCategoryColor(hex, theme)!;
      assert.ok(isHexColor(result.color));
      assert.ok(meetsContrast(result.color, theme), `${hex} ${theme} → ${result.color}`);
      const before = toOklch(hex);
      const after = toOklch(result.color);
      if (result.adjusted && before.c > 0.03 && after.c > 0.03) {
        assert.ok(hueDistance(before.h, after.h) <= 2, `${hex} ${theme} → ${result.color}: matiz ${before.h.toFixed(1)}→${after.h.toFixed(1)}`);
      }
    }
  }
});

test("superfícies coincidem com os tokens de index.css", () => {
  const css = readFileSync(join(import.meta.dirname, "..", "index.css"), "utf8");
  const root = css.slice(css.indexOf(":root {"), css.indexOf("}", css.indexOf(":root {")));
  const dark = css.slice(css.indexOf(':root[data-theme="dark"] {'), css.indexOf("}", css.indexOf(':root[data-theme="dark"] {')));
  const token = (block: string, name: string) => block.match(new RegExp(`--color-${name}:\\s*(#[0-9a-fA-F]{6})`))?.[1]?.toUpperCase();
  assert.equal(token(root, "bg"), THEME_BASE.light.bg);
  assert.equal(token(root, "ink"), THEME_BASE.light.ink);
  assert.equal(token(dark, "bg"), THEME_BASE.dark.bg);
  assert.equal(token(dark, "ink"), THEME_BASE.dark.ink);
});
