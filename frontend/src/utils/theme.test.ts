/// <reference types="node" />
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
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

test("o hash do script inline do index.html está autorizado na CSP do vercel.json", () => {
  const frontend = join(import.meta.dirname, "..", "..");
  const html = readFileSync(join(frontend, "index.html"), "utf8");
  const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
  assert.ok(script !== undefined, "script inline do tema não encontrado no index.html");
  const expected = `'sha256-${createHash("sha256").update(script, "utf8").digest("base64")}'`;

  const vercel = JSON.parse(readFileSync(join(frontend, "vercel.json"), "utf8")) as {
    headers: { headers: { key: string; value: string }[] }[];
  };
  const csp = vercel.headers
    .flatMap((rule) => rule.headers)
    .find((h) => h.key === "Content-Security-Policy")?.value;
  assert.ok(csp, "vercel.json não define Content-Security-Policy");
  const scriptSrc = csp.split(";").map((d) => d.trim()).find((d) => d.startsWith("script-src "));
  assert.ok(scriptSrc, "a CSP do vercel.json não tem script-src");
  const hashes = scriptSrc.split(/\s+/).filter((t) => t.startsWith("'sha256-"));

  assert.ok(
    hashes.includes(expected),
    `O script inline do index.html mudou e o browser vai bloqueá-lo (o tema deixa de ser aplicado antes do render).\n` +
      `  Hash atual do script: ${expected}\n` +
      `  Hashes no script-src do vercel.json: ${hashes.join(" ") || "(nenhum)"}\n` +
      `  Correção: em frontend/vercel.json, no script-src da Content-Security-Policy, substitui o hash antigo por ${expected}`,
  );
});
