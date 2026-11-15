import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router";
import { cartApi, catalogApi } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { useCart } from "../cart/CartContext";
import { StockLabel } from "../components/StockLabel";
import { ErrorMessage, Loading, Notice } from "../components/Status";
import { formatPrice } from "../format";
import { errorMessage, useApi } from "../hooks/useApi";

// Same limit as order-service (CartService.MAX_QUANTITY): more is refused with a 400
const MAX_QUANTITY = 100;

export function ProductPage() {
  const id = Number(useParams().id);
  const { session } = useAuth();
  const cart = useCart();
  const product = useApi(() => catalogApi.product(id), [id]);
  const categories = useApi(() => catalogApi.categories(), []);
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);
  const [added, setAdded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!Number.isInteger(id) || id <= 0) {
    return <ErrorMessage message="Introuvable." />;
  }
  if (product.loading && !product.data) {
    return <Loading />;
  }
  if (!product.data) {
    return (
      <>
        <ErrorMessage message={product.error} onRetry={product.reload} />
        <Link to="/">← Retour au catalogue</Link>
      </>
    );
  }

  const { name, description, price, stock, categoryId } = product.data;
  const category = categories.data?.find((candidate) => candidate.id === categoryId);
  const max = Math.min(stock, MAX_QUANTITY);

  const onAdd = async (event: FormEvent) => {
    event.preventDefault();
    setAdding(true);
    setAdded(false);
    setError(null);
    try {
      // order-service checks the stock again: 409 when the cart would hold more than what is left
      cart.update(await cartApi.add(id, quantity));
      setAdded(true);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setAdding(false);
    }
  };

  return (
    <section>
      <p>
        <Link to="/">← Retour au catalogue</Link>
      </p>
      <div className="detail">
        <div>
          {category && (
            <Link to={`/?category=${category.id}`} className="muted">
              {category.name}
            </Link>
          )}
          <h1>{name}</h1>
          <p className="pre-line">{description ?? <span className="muted">Pas de description.</span>}</p>
        </div>
        <aside className="card">
          <p className="price">{formatPrice(price)}</p>
          <p>
            <StockLabel stock={stock} />
          </p>
          {added && (
            <Notice tone="success">
              Ajouté au panier. <Link to="/cart">Voir le panier</Link>
            </Notice>
          )}
          <ErrorMessage message={error} />
          {!session ? (
            <p>
              <Link to="/login" state={{ from: `/products/${id}` }}>
                Connecte-toi
              </Link>{" "}
              pour ajouter ce produit au panier.
            </p>
          ) : (
            stock > 0 && (
              <form className="actions" onSubmit={onAdd}>
                <input
                  className="quantity"
                  type="number"
                  aria-label="Quantité"
                  min={1}
                  max={max}
                  required
                  value={quantity}
                  onChange={(event) => setQuantity(Math.max(1, Math.min(max, Number(event.target.value) || 1)))}
                />
                <button type="submit" className="button button-primary" disabled={adding}>
                  {adding ? "Ajout…" : "Ajouter au panier"}
                </button>
              </form>
            )
          )}
        </aside>
      </div>
    </section>
  );
}
