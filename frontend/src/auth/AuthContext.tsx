import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { configureApi } from "../api/client";
import { authApi } from "../api/endpoints";
import type { Credentials } from "../api/types";
import { clearToken, loadSession, saveToken, storageKey } from "./storage";
import { readToken, type Session } from "./token";

interface AuthState {
  // null when nobody is logged in
  session: Session | null;
  // true after a logout forced by an expired or rejected token: the login page says why
  expired: boolean;
  login: (credentials: Credentials) => Promise<void>;
  // Creates the account, then logs in with it: register only returns the user, not a token
  register: (credentials: Credentials) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => loadSession());
  const [expired, setExpired] = useState(false);

  const endSession = useCallback((becauseExpired: boolean) => {
    clearToken();
    setSession(null);
    setExpired(becauseExpired);
  }, []);

  // Set before the first render of the pages, which start their requests in their own effects (run before
  // this component's effects). getToken reads the storage on each request: never a token already expired.
  // endSession never changes (no dependency), the one of the first render is fine
  useState(() =>
    configureApi({
      getToken: () => loadSession()?.token ?? null,
      onUnauthorized: () => endSession(true),
    }),
  );

  // Logged out at the expiry date of the token, not at the next request that gets a 401
  useEffect(() => {
    if (!session) {
      return;
    }
    // setTimeout takes at most 2^31 - 1 ms (about 24 days): a longer token is checked again at that point
    const delay = Math.min(session.expiresAt - Date.now(), 2 ** 31 - 1);
    const timer = setTimeout(() => {
      if (!loadSession()) {
        endSession(true);
      }
    }, delay);
    return () => clearTimeout(timer);
  }, [session, endSession]);

  // Logging in or out in another tab does the same here
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === storageKey || event.key === null) {
        setSession(loadSession());
        setExpired(false);
      }
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  const login = useCallback(async (credentials: Credentials) => {
    const { token } = await authApi.login(credentials);
    const next = readToken(token);
    if (!next) {
      throw new Error("Le serveur a renvoyé un jeton illisible.");
    }
    saveToken(token);
    setSession(next);
    setExpired(false);
  }, []);

  const register = useCallback(
    async (credentials: Credentials) => {
      await authApi.register(credentials);
      await login(credentials);
    },
    [login],
  );

  const logout = useCallback(() => endSession(false), [endSession]);

  const value = useMemo(
    () => ({ session, expired, login, register, logout }),
    [session, expired, login, register, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const auth = useContext(AuthContext);
  if (!auth) {
    throw new Error("useAuth outside of <AuthProvider>");
  }
  return auth;
}
