/// <reference types="node" />
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import { AUTO_CATEGORY_COLORS, COLOR_SWATCHES } from "./categoryPalette.ts";
import { isHexColor } from "./displayColor.ts";

test("paleta automática coincide com --color-cat-1..8 de index.css nos dois temas", () => {
  const css = readFileSync(join(import.meta.dirname, "..", "index.css"), "utf8");
  const block = (selector: string) => css.slice(css.indexOf(selector), css.indexOf("}", css.indexOf(selector)));
  const tokens = (b: string) => [...b.matchAll(/--color-cat-(\d):\s*(#[0-9a-fA-F]{6})/g)].map((m) => m[2].toUpperCase());
  assert.deepEqual(tokens(block(":root {")), AUTO_CATEGORY_COLORS.light);
  assert.deepEqual(tokens(block(':root[data-theme="dark"] {')), AUTO_CATEGORY_COLORS.dark);
});

test("amostras: 14 cores válidas, em maiúsculas, sem repetições e com nome", () => {
  assert.equal(COLOR_SWATCHES.length, 14);
  assert.equal(new Set(COLOR_SWATCHES.map((s) => s.hex)).size, 14);
  for (const s of COLOR_SWATCHES) {
    assert.ok(isHexColor(s.hex) && s.hex === s.hex.toUpperCase(), s.hex);
    assert.ok(s.name.length > 0);
  }
});
