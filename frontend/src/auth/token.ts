import type { Role } from "../api/types";

// What the app needs from the JWT of auth-service: sub = email, role, exp (seconds since the epoch).
// Only read, never checked: the signature is the job of each service, which rejects a forged token
export interface Session {
  token: string;
  email: string;
  role: Role;
  // Milliseconds since the epoch, like Date.now()
  expiresAt: number;
}

interface Claims {
  sub?: unknown;
  role?: unknown;
  exp?: unknown;
}

// null for anything that is not a JWT with those three claims
export function readToken(token: string): Session | null {
  const payload = token.split(".")[1];
  if (!payload) {
    return null;
  }
  let claims: Claims;
  try {
    claims = JSON.parse(decodeBase64Url(payload)) as Claims;
  } catch {
    return null;
  }
  const { sub, role, exp } = claims;
  if (typeof sub !== "string" || (role !== "USER" && role !== "ADMIN") || typeof exp !== "number") {
    return null;
  }
  return { token, email: sub, role, expiresAt: exp * 1000 };
}

export function isExpired(session: Session, now = Date.now()): boolean {
  return session.expiresAt <= now;
}

// base64url without padding (RFC 7515) to UTF-8 text: atob alone only knows base64 and Latin-1
function decodeBase64Url(value: string): string {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const bytes = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}
