/// <reference types="node" />
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import {
  THEME_ATTRIBUTE,
  THEME_STORAGE_KEY,
  parseThemeMode,
  readStoredMode,
  resolveTheme,
  storeMode,
} from "./theme.ts";

test("parseThemeMode aceita os três modos e trata o resto como system", () => {
  assert.equal(parseThemeMode("light"), "light");
  assert.equal(parseThemeMode("dark"), "dark");
  assert.equal(parseThemeMode("system"), "system");
  assert.equal(parseThemeMode(null), "system");
  assert.equal(parseThemeMode("DARK"), "system");
  assert.equal(parseThemeMode(42), "system");
});

test("resolveTheme: system segue a preferência do sistema; os outros ignoram-na", () => {
  assert.equal(resolveTheme("system", true), "dark");
  assert.equal(resolveTheme("system", false), "light");
  assert.equal(resolveTheme("light", true), "light");
  assert.equal(resolveTheme("dark", false), "dark");
});

test("readStoredMode e storeMode toleram storage ausente ou a lançar", () => {
  const throwing = {
    getItem: () => {
      throw new Error("SecurityError");
    },
    setItem: () => {
      throw new Error("QuotaExceededError");
    },
  };
  assert.equal(readStoredMode(throwing), "system");
  assert.equal(readStoredMode(null), "system");
  assert.doesNotThrow(() => storeMode(throwing, "dark"));
  assert.doesNotThrow(() => storeMode(undefined, "dark"));

  const values = new Map<string, string>();
  const memory = { getItem: (k: string) => values.get(k) ?? null, setItem: (k: string, v: string) => void values.set(k, v) };
  storeMode(memory, "dark");
  assert.equal(values.get(THEME_STORAGE_KEY), "dark");
  assert.equal(readStoredMode(memory), "dark");
});

test("o script inline do index.html usa a mesma chave e o mesmo atributo", () => {
  const html = readFileSync(join(import.meta.dirname, "..", "..", "index.html"), "utf8");
  const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
  assert.ok(script, "script inline do tema não encontrado");
  assert.ok(script.includes(`localStorage.getItem("${THEME_STORAGE_KEY}")`), "chave do localStorage diferente");
  assert.ok(script.includes(`setAttribute("${THEME_ATTRIBUTE}"`), "atributo do tema diferente");
  assert.ok(script.includes("prefers-color-scheme: dark"), "não consulta a preferência do sistema");
  assert.ok(script.includes("colorScheme"), "não define color-scheme");
});
