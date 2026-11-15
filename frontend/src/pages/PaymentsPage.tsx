import { Link, useSearchParams } from "react-router";
import { paymentApi } from "../api/endpoints";
import { Pagination } from "../components/Pagination";
import { PaymentStatusBadge } from "../components/StatusBadge";
import { ErrorMessage, Loading } from "../components/Status";
import { formatDate, formatPrice } from "../format";
import { useApi } from "../hooks/useApi";

const PAGE_SIZE = 10;

// Every attempt of the account, declined ones included, newest first. Only the last 4 digits of the card
// are known: payment-service never stores the full number
export function PaymentsPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Math.floor(Number(params.get("page")) || 0));
  const payments = useApi(() => paymentApi.list(page, PAGE_SIZE), [page]);

  return (
    <section>
      <h1>Mes paiements</h1>
      <ErrorMessage message={payments.error} onRetry={payments.reload} />
      {payments.loading && !payments.data && <Loading />}
      {payments.data &&
        (payments.data.content.length === 0 ? (
          <p className="muted">Aucun paiement pour l'instant.</p>
        ) : (
          <>
            <div className="table-wrapper">
              <table aria-busy={payments.loading}>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Commande</th>
                    <th>Carte</th>
                    <th className="number">Montant</th>
                    <th>Statut</th>
                  </tr>
                </thead>
                <tbody>
                  {payments.data.content.map((payment) => (
                    <tr key={payment.id}>
                      <td>{formatDate(payment.createdAt)}</td>
                      <td>
                        <Link to={`/orders/${payment.orderId}`}>n°{payment.orderId}</Link>
                      </td>
                      <td>•••• {payment.cardLast4}</td>
                      <td className="number">{formatPrice(payment.amount)}</td>
                      <td>
                        <PaymentStatusBadge status={payment.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={payments.data.page.number}
              totalPages={payments.data.page.totalPages}
              onChange={(next) => setParams(next > 0 ? { page: String(next) } : {})}
            />
          </>
        ))}
    </section>
  );
}
