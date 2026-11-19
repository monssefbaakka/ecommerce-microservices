import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import type { Role } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { ErrorMessage } from "./Status";

// Sends a visitor to the login page, which comes back here once logged in. With a role, the other
// accounts get a message: the service would answer 403 anyway, this only avoids a page full of errors
export function RequireAuth({ role, children }: { role?: Role; children: ReactNode }) {
  const { session } = useAuth();
  const location = useLocation();

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (role && session.role !== role) {
    return <ErrorMessage message="Cette page est réservée aux administrateurs." />;
  }
  return children;
}
