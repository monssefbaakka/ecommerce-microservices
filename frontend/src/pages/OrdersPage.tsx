import { Link, useSearchParams } from "react-router";
import { orderApi } from "../api/endpoints";
import { Pagination } from "../components/Pagination";
import { OrderStatusBadge } from "../components/StatusBadge";
import { ErrorMessage, Loading } from "../components/Status";
import { formatDate, formatPrice, plural } from "../format";
import { useApi } from "../hooks/useApi";

const PAGE_SIZE = 10;

// The orders of the logged in account, newest first (order-service sorts them)
export function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Math.floor(Number(params.get("page")) || 0));
  const orders = useApi(() => orderApi.list(page, PAGE_SIZE), [page]);

  return (
    <section>
      <h1>Mes commandes</h1>
      <ErrorMessage message={orders.error} onRetry={orders.reload} />
      {orders.loading && !orders.data && <Loading />}
      {orders.data &&
        (orders.data.content.length === 0 ? (
          <p>
            Aucune commande pour l'instant. <Link to="/">Parcourir le catalogue</Link>
          </p>
        ) : (
          <>
            <div className="table-wrapper">
              <table aria-busy={orders.loading}>
                <thead>
                  <tr>
                    <th>Commande</th>
                    <th>Date</th>
                    <th>Articles</th>
                    <th className="number">Total</th>
                    <th>Statut</th>
                  </tr>
                </thead>
                <tbody>
                  {orders.data.content.map((order) => (
                    <tr key={order.id}>
                      <td>
                        <Link to={`/orders/${order.id}`}>n°{order.id}</Link>
                      </td>
                      <td>{formatDate(order.createdAt)}</td>
                      <td>{plural(order.totalQuantity, "article")}</td>
                      <td className="number">{formatPrice(order.total)}</td>
                      <td>
                        <OrderStatusBadge status={order.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={orders.data.page.number}
              totalPages={orders.data.page.totalPages}
              onChange={(next) => setParams(next > 0 ? { page: String(next) } : {})}
            />
          </>
        ))}
    </section>
  );
}
