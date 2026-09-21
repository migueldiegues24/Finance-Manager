/// <reference types="node" />
import assert from "node:assert/strict";
import { test } from "node:test";
import { categoryColor, categoryStyle } from "./categoryColor.ts";
import { displayCategoryColor } from "./displayColor.ts";

const value = (style: object) => (style as Record<string, string>)["--category-color"];

test("sem cor, com null ou com valor inválido usa a cor automática (id % 8)", () => {
  for (const stored of [undefined, null, "", "red", "url(x)", "#ff0000;x", "#12"]) {
    assert.equal(value(categoryStyle(10, stored, "light")), "var(--color-cat-3)", String(stored));
    assert.equal(value(categoryStyle(10, stored, "dark")), "var(--color-cat-3)", String(stored));
  }
  assert.equal(categoryColor(8), "var(--color-cat-1)");
});

test("com cor válida usa o hex ajustado ao tema", () => {
  assert.equal(value(categoryStyle(1, "#2f5d8a", "light")), "#2F5D8A");
  assert.equal(value(categoryStyle(1, "#1F5E6B", "dark")), displayCategoryColor("#1F5E6B", "dark")!.color);
  assert.match(value(categoryStyle(1, "#FFFF00", "light")), /^#[0-9A-F]{6}$/);
});
