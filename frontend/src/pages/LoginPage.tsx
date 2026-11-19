import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Field } from "../components/Field";
import { ErrorMessage, Notice } from "../components/Status";
import { errorMessage } from "../hooks/useApi";

// The page a protected route came from (RequireAuth), or the catalog
export function useRedirectTarget(): string {
  const state = useLocation().state as { from?: unknown } | null;
  return typeof state?.from === "string" ? state.from : "/";
}

export function LoginPage() {
  const { session, expired, login } = useAuth();
  const navigate = useNavigate();
  const target = useRedirectTarget();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  // Already logged in (another tab, or the back button after a login)
  if (session && !sending) {
    return <Navigate to={target} replace />;
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSending(true);
    setError(null);
    try {
      await login({ email: email.trim(), password });
      navigate(target, { replace: true });
    } catch (cause) {
      // auth-service gives the same message for an unknown email and a wrong password
      setError(errorMessage(cause));
      setSending(false);
    }
  };

  return (
    <section className="card form-card">
      <h1>Connexion</h1>
      {expired && <Notice tone="warning">Ta session a expiré : reconnecte-toi pour continuer.</Notice>}
      <ErrorMessage message={error} />
      <form className="form" onSubmit={onSubmit}>
        <Field
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <Field
          label="Mot de passe"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        <button type="submit" className="button button-primary" disabled={sending}>
          {sending ? "Connexion…" : "Se connecter"}
        </button>
      </form>
      <p className="muted">
        Pas encore de compte ?{" "}
        <Link to="/register" state={{ from: target }}>
          Créer un compte
        </Link>
      </p>
    </section>
  );
}
