import { useState } from "react";
import { Link, useLocation, useParams } from "react-router";
import { orderApi } from "../api/endpoints";
import type { Payment } from "../api/types";
import { PaymentForm } from "../components/PaymentForm";
import { OrderStatusBadge } from "../components/StatusBadge";
import { ErrorMessage, Loading, Notice } from "../components/Status";
import { formatDate, formatPrice } from "../format";
import { errorMessage, useApi } from "../hooks/useApi";

export function OrderPage() {
  const id = Number(useParams().id);
  // Set by the cart page right after "Commander"
  const placed = (useLocation().state as { placed?: boolean } | null)?.placed === true;
  const order = useApi(() => orderApi.get(id), [id]);
  const [lastPayment, setLastPayment] = useState<Payment | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [cancelled, setCancelled] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const onPayment = (payment: Payment) => {
    setLastPayment(payment);
    // SUCCEEDED: payment-service has already set the order to PAID in order-service
    if (payment.status === "SUCCEEDED") {
      order.reload();
    }
  };

  // The units go back to the stock. 409 once paid (or already cancelled), 503 when the stock cannot be
  // given back yet: the order then stays as it was
  const onCancel = async () => {
    if (!window.confirm("Annuler cette commande ? Les articles seront remis en stock.")) {
      return;
    }
    setCancelling(true);
    setCancelError(null);
    try {
      await orderApi.cancel(id);
      setCancelled(true);
    } catch (cause) {
      setCancelError(errorMessage(cause));
    } finally {
      setCancelling(false);
      order.reload();
    }
  };

  if (order.loading && !order.data) {
    return <Loading />;
  }
  if (!order.data) {
    return (
      <>
        <ErrorMessage message={order.error} onRetry={order.reload} />
        <Link to="/orders">← Mes commandes</Link>
      </>
    );
  }

  const { status, items, total, totalQuantity, createdAt } = order.data;

  return (
    <section>
      <p>
        <Link to="/orders">← Mes commandes</Link>
      </p>
      <h1>
        Commande n°{id} <OrderStatusBadge status={status} />
      </h1>
      <p className="muted">Passée le {formatDate(createdAt)}</p>

      {placed && status === "PENDING" && !lastPayment && (
        <Notice tone="success">Commande enregistrée, les articles sont réservés. Il ne reste qu'à la payer.</Notice>
      )}
      {cancelled && status === "CANCELLED" && (
        <Notice tone="success">Commande annulée, les articles sont remis en stock.</Notice>
      )}
      {lastPayment?.status === "SUCCEEDED" && (
        <Notice tone="success">Paiement accepté pour la carte se terminant par {lastPayment.cardLast4}. Merci !</Notice>
      )}
      {lastPayment?.status === "DECLINED" && (
        <Notice tone="warning">
          Paiement refusé pour la carte se terminant par {lastPayment.cardLast4}. La commande attend toujours son
          paiement : essaie une autre carte.
        </Notice>
      )}

      <div className="detail">
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Produit</th>
                <th className="number">Prix unitaire</th>
                <th className="number">Quantité</th>
                <th className="number">Sous-total</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item, index) => (
                // productId is null once the product is deleted from the catalog: the line keeps its name
                <tr key={item.productId ?? `deleted-${index}`}>
                  <td>
                    {item.productId ? <Link to={`/products/${item.productId}`}>{item.productName}</Link> : item.productName}
                  </td>
                  <td className="number">{formatPrice(item.unitPrice)}</td>
                  <td className="number">{item.quantity}</td>
                  <td className="number">{formatPrice(item.subtotal)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th colSpan={2}>Total</th>
                <th className="number">{totalQuantity}</th>
                <th className="number price">{formatPrice(total)}</th>
              </tr>
            </tfoot>
          </table>
        </div>
        {status === "PENDING" && (
          <aside className="card">
            <h2>Paiement</h2>
            <p className="price">{formatPrice(total)}</p>
            <PaymentForm orderId={id} onPayment={onPayment} />
            <div className="order-cancel">
              <ErrorMessage message={cancelError} />
              <button type="button" className="button button-danger" disabled={cancelling} onClick={onCancel}>
                {cancelling ? "Annulation…" : "Annuler la commande"}
              </button>
              {/* orders.pending-expiry.timeout of order-service, 30 minutes unless changed */}
              <p className="muted">Sans paiement, la commande est annulée d'elle-même au bout de 30 minutes.</p>
            </div>
          </aside>
        )}
      </div>
    </section>
  );
}
