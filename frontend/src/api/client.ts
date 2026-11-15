import type { ErrorResponse } from "./types";

// An answer other than 2xx, with the message of the service (its ErrorResponse) when there is one
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

interface ApiConfig {
  // The JWT to send, or null when nobody is logged in
  getToken: () => string | null;
  // Called when a request sent with a token gets a 401: the token expired or its key changed
  onUnauthorized: () => void;
}

let config: ApiConfig = {
  getToken: () => null,
  onUnauthorized: () => {},
};

export function configureApi(next: ApiConfig): void {
  config = next;
}

// Every call goes to /api/** on the page's own origin: the Vite dev server, nginx or Caddy forward it
// to the API gateway
export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = config.getToken();
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "Le serveur ne répond pas. Vérifie ta connexion et réessaie.");
  }

  if (response.status === 401 && token) {
    config.onUnauthorized();
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function toApiError(response: Response): Promise<ApiError> {
  const body = await response.json().catch(() => null) as Partial<ErrorResponse> | null;
  const fieldErrors: Record<string, string> = {};
  for (const error of body?.errors ?? []) {
    fieldErrors[error.field] = error.message;
  }
  const message = body?.message ?? defaultMessage(response.status);
  return new ApiError(response.status, message, fieldErrors);
}

function defaultMessage(status: number): string {
  if (status === 401) return "Connecte-toi pour continuer.";
  if (status === 403) return "Tu n'as pas accès à cette action.";
  if (status === 404) return "Introuvable.";
  if (status >= 500) return "Le service est indisponible pour le moment. Réessaie dans quelques instants.";
  return `Erreur ${status}`;
}
