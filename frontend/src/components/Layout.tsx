import { Link, NavLink, Outlet, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { useCart } from "../cart/CartContext";

// Header and footer around every page
export function Layout() {
  const { session, logout } = useAuth();
  const { count } = useCart();
  const navigate = useNavigate();

  const onLogout = () => {
    logout();
    navigate("/");
  };

  return (
    <div className="app">
      <header className="header">
        <div className="container header-inner">
          <Link to="/" className="brand">
            E-Commerce
          </Link>
          <nav className="nav" aria-label="Navigation principale">
            <NavLink to="/" end>
              Catalogue
            </NavLink>
            {session && <NavLink to="/orders">Commandes</NavLink>}
            {session && <NavLink to="/payments">Paiements</NavLink>}
            {session?.role === "ADMIN" && <NavLink to="/admin">Administration</NavLink>}
          </nav>
          <div className="header-actions">
            <NavLink to="/cart" className="cart-link" aria-label={`Panier, ${count} article(s)`}>
              Panier
              {count > 0 && <span className="badge">{count}</span>}
            </NavLink>
            {session ? (
              <>
                <span className="muted header-email" title={session.email}>
                  {session.email}
                </span>
                <button type="button" className="button button-small" onClick={onLogout}>
                  Déconnexion
                </button>
              </>
            ) : (
              <>
                <NavLink to="/login">Connexion</NavLink>
                <Link to="/register" className="button button-small button-primary">
                  Créer un compte
                </Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main className="container main">
        <Outlet />
      </main>
      <footer className="footer">
        <div className="container muted">
          Projet de démonstration : les paiements sont simulés, aucune carte réelle n'est débitée.
        </div>
      </footer>
    </div>
  );
}
