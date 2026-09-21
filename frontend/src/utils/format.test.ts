/// <reference types="node" />
import assert from "node:assert/strict";
import { test } from "node:test";
import { formatCurrency } from "./format.ts";

const NBSP = " ";

test("negativo usa o sinal − (U+2212) colado ao número", () => {
  assert.equal(formatCurrency(-1234.56), `−1${NBSP}234,56${NBSP}€`);
  assert.equal(formatCurrency(-0.5), `−0,50${NBSP}€`);
  assert.ok(!formatCurrency(-1234.56).includes("-"), "sem hífen ASCII");
});

test("positivo sem sinal, com milhares agrupados", () => {
  assert.equal(formatCurrency(12345.67), `12${NBSP}345,67${NBSP}€`);
  assert.equal(formatCurrency(1234.56), `1${NBSP}234,56${NBSP}€`);
});

test("zero sem sinal, incluindo -0 e valores que arredondam a zero", () => {
  assert.equal(formatCurrency(0), `0,00${NBSP}€`);
  assert.equal(formatCurrency(-0), `0,00${NBSP}€`);
  assert.equal(formatCurrency(-0.004), `0,00${NBSP}€`);
});

test("sem espaços que partam a linha", () => {
  for (const value of [-1234.56, 12345.67, 0]) {
    assert.ok(!formatCurrency(value).includes(" "), `espaço normal em ${formatCurrency(value)}`);
  }
});
