import { Link } from "react-router";

export function NotFoundPage() {
  return (
    <section>
      <h1>Page introuvable</h1>
      <p className="muted">Cette adresse ne correspond à aucune page de la boutique.</p>
      <Link to="/" className="button">
        Retour au catalogue
      </Link>
    </section>
  );
}
