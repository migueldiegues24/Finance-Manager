/// <reference types="node" />
import assert from "node:assert/strict";
import { test } from "node:test";
import { initialsFromEmail } from "./initials.ts";
import { decodeJwtSubject } from "./jwt.ts";

function jwt(payload: object): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "HS512" })}.${encode(payload)}.assinatura`;
}

test("decodeJwtSubject lê o sub, incluindo caracteres não ASCII", () => {
  assert.equal(decodeJwtSubject(jwt({ sub: "miguel@teste.com", exp: 1 })), "miguel@teste.com");
  assert.equal(decodeJwtSubject(jwt({ sub: "joão@exemplo.pt" })), "joão@exemplo.pt");
});

test("decodeJwtSubject devolve null para tokens inválidos ou sem sub", () => {
  assert.equal(decodeJwtSubject(null), null);
  assert.equal(decodeJwtSubject(""), null);
  assert.equal(decodeJwtSubject("sem-pontos"), null);
  assert.equal(decodeJwtSubject("a.%%%.c"), null);
  assert.equal(decodeJwtSubject(jwt({ exp: 1 })), null);
  assert.equal(decodeJwtSubject(jwt({ sub: 42 })), null);
});

test("initialsFromEmail", () => {
  assert.equal(initialsFromEmail("miguel.diegues@teste.com"), "MD");
  assert.equal(initialsFromEmail("ana_rita-silva@teste.com"), "AR");
  assert.equal(initialsFromEmail("migueldiegues24@gmail.com"), "MI");
  assert.equal(initialsFromEmail("élia@teste.com"), "ÉL");
  assert.equal(initialsFromEmail("123@teste.com"), "?");
  assert.equal(initialsFromEmail(null), "?");
});
