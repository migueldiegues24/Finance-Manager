/// <reference types="node" />
import assert from "node:assert/strict";
import { test } from "node:test";
import { choiceFromStored, storedFromChoice } from "./colorChoice.ts";

test("resposta sem color, com null ou com valor inválido = cor automática", () => {
  for (const value of [undefined, null, "", "red", "url(x)", 42]) {
    assert.deepEqual(choiceFromStored(value), { kind: "auto" }, String(value));
  }
  assert.equal(storedFromChoice({ kind: "auto" }), null);
});

test("cor de uma amostra ou personalizada, sempre em maiúsculas", () => {
  assert.deepEqual(choiceFromStored("#2f5d8a"), { kind: "swatch", hex: "#2F5D8A" });
  assert.deepEqual(choiceFromStored("#123456"), { kind: "custom", hex: "#123456" });
  assert.equal(storedFromChoice({ kind: "custom", hex: "#abcdef" }), "#ABCDEF");
});
