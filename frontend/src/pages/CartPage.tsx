import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router";
import { cartApi, orderApi } from "../api/endpoints";
import type { Cart, CartItem } from "../api/types";
import { useCart } from "../cart/CartContext";
import { ErrorMessage, Loading, Notice } from "../components/Status";
import { formatPrice, plural } from "../format";
import { errorMessage, useApi } from "../hooks/useApi";

const MAX_QUANTITY = 100;

export function CartPage() {
  const { update } = useCart();
  const navigate = useNavigate();
  const cart = useApi(() => cartApi.get(), []);
  // A single change at a time: the cart returned by the last one is the truth
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // The header follows what this page shows, also when the first load finds another count
  useEffect(() => {
    if (cart.data) {
      update(cart.data);
    }
  }, [cart.data, update]);

  const run = async (change: () => Promise<Cart | void>) => {
    setBusy(true);
    setError(null);
    try {
      const next = await change();
      if (next) {
        cart.setData(next);
      } else {
        cart.reload();
      }
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  const placeOrder = async () => {
    setBusy(true);
    setError(null);
    try {
      // The cart becomes a PENDING order and is emptied: next step, the payment on the order page
      const order = await orderApi.place();
      update(null);
      navigate(`/orders/${order.id}`, { state: { placed: true } });
    } catch (cause) {
      // 409: not enough stock any more for a line, the cart is unchanged
      setError(errorMessage(cause));
      setBusy(false);
    }
  };

  if (cart.loading && !cart.data) {
    return <Loading />;
  }
  if (!cart.data) {
    return <ErrorMessage message={cart.error} onRetry={cart.reload} />;
  }

  const { items, totalQuantity, total, productsUpToDate } = cart.data;

  return (
    <section>
      <h1>Panier</h1>
      {!productsUpToDate && (
        <Notice tone="warning">
          Le catalogue ne répond pas : les noms et prix affichés sont les derniers connus. La commande sera possible
          dès son retour.
        </Notice>
      )}
      <ErrorMessage message={error} />
      {items.length === 0 ? (
        <p>
          Ton panier est vide. <Link to="/">Parcourir le catalogue</Link>
        </p>
      ) : (
        <>
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Produit</th>
                  <th className="number">Prix unitaire</th>
                  <th>Quantité</th>
                  <th className="number">Sous-total</th>
                  <th>
                    <span className="visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <CartLine
                    key={item.productId}
                    item={item}
                    disabled={busy}
                    onQuantity={(quantity) => run(() => cartApi.update(item.productId, quantity))}
                    onRemove={() => run(() => cartApi.remove(item.productId))}
                  />
                ))}
              </tbody>
            </table>
          </div>
          <div className="summary">
            <button type="button" className="button button-danger" disabled={busy} onClick={() => run(cartApi.clear)}>
              Vider le panier
            </button>
            <div className="actions">
              <span className="price">
                Total ({plural(totalQuantity, "article")}) : {formatPrice(total)}
              </span>
              <button
                type="button"
                className="button button-primary"
                disabled={busy || !productsUpToDate}
                onClick={placeOrder}
              >
                Commander
              </button>
            </div>
          </div>
        </>
      )}
    </section>
  );
}

interface LineProps {
  item: CartItem;
  disabled: boolean;
  onQuantity: (quantity: number) => void;
  onRemove: () => void;
}

function CartLine({ item, disabled, onQuantity, onRemove }: LineProps) {
  // Typed freely, sent when the field loses the focus or on Enter: not one request per key
  const [value, setValue] = useState(String(item.quantity));
  useEffect(() => setValue(String(item.quantity)), [item.quantity]);

  const commit = () => {
    const quantity = Number(value);
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > MAX_QUANTITY) {
      setValue(String(item.quantity));
      return;
    }
    if (quantity !== item.quantity) {
      onQuantity(quantity);
    }
  };

  return (
    <tr>
      <td>
        {item.productName ? (
          <Link to={`/products/${item.productId}`}>{item.productName}</Link>
        ) : (
          <span className="muted">Produit n°{item.productId}</span>
        )}
      </td>
      <td className="number">{formatPrice(item.unitPrice)}</td>
      <td>
        <input
          className="quantity"
          type="number"
          aria-label={`Quantité de ${item.productName ?? `produit n°${item.productId}`}`}
          min={1}
          max={MAX_QUANTITY}
          disabled={disabled}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          onBlur={commit}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              commit();
            }
          }}
        />
      </td>
      <td className="number">{formatPrice(item.subtotal)}</td>
      <td>
        <button type="button" className="button button-small button-danger" disabled={disabled} onClick={onRemove}>
          Retirer
        </button>
      </td>
    </tr>
  );
}
