import { isExpired, readToken, type Session } from "./token";

// The JWT survives a reload and is shared by the tabs of the shop. localStorage can throw (private
// browsing, storage disabled): the user then stays logged in until the tab is closed or reloaded
const KEY = "ecommerce.token";

let memoryToken: string | null = null;
// false once localStorage refused a write: from then on the token only lives in memoryToken
let persisted = true;

export function loadSession(now = Date.now()): Session | null {
  const token = read();
  const session = token ? readToken(token) : null;
  if (!session || isExpired(session, now)) {
    if (token) {
      clearToken();
    }
    return null;
  }
  return session;
}

export function saveToken(token: string): void {
  memoryToken = token;
  try {
    localStorage.setItem(KEY, token);
    persisted = true;
  } catch {
    persisted = false;
  }
}

export function clearToken(): void {
  memoryToken = null;
  try {
    localStorage.removeItem(KEY);
  } catch {
    // nothing stored
  }
}

function read(): string | null {
  if (!persisted) {
    return memoryToken;
  }
  try {
    // Not "?? memoryToken": after a logout in another tab, null is the right answer
    return localStorage.getItem(KEY);
  } catch {
    return memoryToken;
  }
}

// Set by another tab: logging in or out there does the same here
export const storageKey = KEY;
