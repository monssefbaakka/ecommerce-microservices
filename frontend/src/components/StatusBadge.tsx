import type { OrderStatus, PaymentStatus } from "../api/types";
import { orderStatusLabels, paymentStatusLabels } from "../format";

// Colored by the status name (.status-PAID, .status-DECLINED... in styles.css)
export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`status status-${status}`}>{orderStatusLabels[status]}</span>;
}

export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  return <span className={`status status-${status}`}>{paymentStatusLabels[status]}</span>;
}
