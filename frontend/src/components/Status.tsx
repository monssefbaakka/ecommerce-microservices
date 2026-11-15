import type { ReactNode } from "react";

export function Loading() {
  return (
    <p className="muted" role="status">
      Chargement…
    </p>
  );
}

// Read out by screen readers as soon as it appears. Renders nothing without a message
export function ErrorMessage({ message, onRetry }: { message: string | null; onRetry?: () => void }) {
  if (!message) {
    return null;
  }
  return (
    <div className="alert alert-error" role="alert">
      <span>{message}</span>
      {onRetry && (
        <button type="button" className="button button-small" onClick={onRetry}>
          Réessayer
        </button>
      )}
    </div>
  );
}

export function Notice({ children, tone = "info" }: { children: ReactNode; tone?: "info" | "success" | "warning" }) {
  return (
    <div className={`alert alert-${tone}`} role="status">
      {children}
    </div>
  );
}
