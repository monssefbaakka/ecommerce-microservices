import { describe, expect, it } from "vitest";
import { isExpired, readToken } from "./token";

// Same shape as a token of auth-service; the signature is never checked by the app
function jwt(claims: object): string {
  const encode = (part: object) => Buffer.from(JSON.stringify(part)).toString("base64url");
  return `${encode({ alg: "HS256" })}.${encode(claims)}.signature`;
}

describe("readToken", () => {
  it("reads the email, the role and the expiry date", () => {
    const token = jwt({ sub: "jane@example.com", role: "ADMIN", exp: 1_900_000_000 });

    expect(readToken(token)).toEqual({
      token,
      email: "jane@example.com",
      role: "ADMIN",
      expiresAt: 1_900_000_000_000,
    });
  });

  it("decodes the UTF-8 characters of the payload", () => {
    const session = readToken(jwt({ sub: "élodie@exemple.fr", role: "USER", exp: 1 }));

    expect(session?.email).toBe("élodie@exemple.fr");
  });

  it("rejects a token without the claims of auth-service", () => {
    expect(readToken(jwt({ sub: "jane@example.com", exp: 1 }))).toBeNull();
    expect(readToken(jwt({ sub: "jane@example.com", role: "ROOT", exp: 1 }))).toBeNull();
    expect(readToken(jwt({ role: "USER", exp: 1 }))).toBeNull();
  });

  it("rejects something that is not a JWT", () => {
    expect(readToken("not-a-token")).toBeNull();
    expect(readToken("a.%%%.c")).toBeNull();
  });
});

describe("isExpired", () => {
  const session = { token: "t", email: "e", role: "USER" as const, expiresAt: 1_000 };

  it("is expired from the expiry date on", () => {
    expect(isExpired(session, 999)).toBe(false);
    expect(isExpired(session, 1_000)).toBe(true);
  });
});
