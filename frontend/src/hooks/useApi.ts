import { useCallback, useEffect, useState, type DependencyList } from "react";

export interface ApiState<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
  // Sends the request again, e.g. after a change made on the page
  reload: () => void;
  // Replaces the data with what a write request returned, without asking the server again
  setData: (data: T) => void;
}

// Runs load() on mount and whenever deps change. The answer of a request overtaken by a newer one
// (typing in the search box, changing page quickly) is dropped: the page never shows an older result
export function useApi<T>(load: () => Promise<T>, deps: DependencyList): ApiState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let current = true;
    setLoading(true);
    setError(null);
    load()
      .then((result) => {
        if (current) {
          setData(result);
        }
      })
      .catch((cause: unknown) => {
        if (current) {
          setError(errorMessage(cause));
        }
      })
      .finally(() => {
        if (current) {
          setLoading(false);
        }
      });
    return () => {
      current = false;
    };
    // load is a new function on each render: deps say when it really changes
  }, [...deps, attempt]);

  const reload = useCallback(() => setAttempt((n) => n + 1), []);
  return { data, error, loading, reload, setData };
}

// The message of the service for an ApiError (an Error too), a generic one for anything else
export function errorMessage(cause: unknown): string {
  return cause instanceof Error ? cause.message : "Une erreur inattendue est survenue.";
}
