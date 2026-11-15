import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { ApiError } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { Field } from "../components/Field";
import { ErrorMessage } from "../components/Status";
import { errorMessage } from "../hooks/useApi";
import { useRedirectTarget } from "./LoginPage";

export function RegisterPage() {
  const { session, register } = useAuth();
  const navigate = useNavigate();
  const target = useRedirectTarget();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [sending, setSending] = useState(false);

  if (session && !sending) {
    return <Navigate to={target} replace />;
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    // Only checked here: auth-service receives one password, the rules (8 to 72 characters) are its own
    if (password !== confirmation) {
      setFieldErrors({ confirmation: "Les deux mots de passe ne correspondent pas" });
      return;
    }
    setSending(true);
    setError(null);
    setFieldErrors({});
    try {
      await register({ email: email.trim(), password });
      navigate(target, { replace: true });
    } catch (cause) {
      // 400: one message per field under it; 409: email already used
      if (cause instanceof ApiError && Object.keys(cause.fieldErrors).length > 0) {
        setFieldErrors(cause.fieldErrors);
      } else {
        setError(errorMessage(cause));
      }
      setSending(false);
    }
  };

  return (
    <section className="card form-card">
      <h1>Créer un compte</h1>
      <ErrorMessage message={error} />
      <form className="form" onSubmit={onSubmit} noValidate>
        <Field
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          error={fieldErrors.email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <Field
          label="Mot de passe"
          type="password"
          autoComplete="new-password"
          required
          hint="Entre 8 et 72 caractères"
          value={password}
          error={fieldErrors.password}
          onChange={(event) => setPassword(event.target.value)}
        />
        <Field
          label="Confirmer le mot de passe"
          type="password"
          autoComplete="new-password"
          required
          value={confirmation}
          error={fieldErrors.confirmation}
          onChange={(event) => setConfirmation(event.target.value)}
        />
        <button type="submit" className="button button-primary" disabled={sending}>
          {sending ? "Création…" : "Créer mon compte"}
        </button>
      </form>
      <p className="muted">
        Déjà un compte ?{" "}
        <Link to="/login" state={{ from: target }}>
          Se connecter
        </Link>
      </p>
    </section>
  );
}
