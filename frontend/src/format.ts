import type { OrderStatus, PaymentStatus } from "./api/types";

// Amounts come as JSON numbers from BigDecimal: the services have no currency, the shop shows euros
const priceFormat = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" });

const dateFormat = new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium", timeStyle: "short" });

// null when product-service was never reached for that cart line: no price known yet
export function formatPrice(amount: number | null): string {
  return amount === null ? "—" : priceFormat.format(amount);
}

// An ISO-8601 Instant, shown in the browser's time zone
export function formatDate(instant: string): string {
  return dateFormat.format(new Date(instant));
}

export const orderStatusLabels: Record<OrderStatus, string> = {
  PENDING: "En attente de paiement",
  PAID: "Payée",
  CANCELLED: "Annulée",
};

export const paymentStatusLabels: Record<PaymentStatus, string> = {
  SUCCEEDED: "Accepté",
  DECLINED: "Refusé",
};

// "1 article", "3 articles"
export function plural(count: number, word: string): string {
  return `${count} ${word}${count > 1 ? "s" : ""}`;
}
