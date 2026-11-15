import { useState, type FormEvent } from "react";
import { ApiError } from "../api/client";
import { paymentApi } from "../api/endpoints";
import type { Payment } from "../api/types";
import { errorMessage } from "../hooks/useApi";
import { Field } from "./Field";
import { ErrorMessage } from "./Status";

interface Props {
  orderId: number;
  // Called with every attempt, declined ones included: payment-service keeps them all
  onPayment: (payment: Payment) => void;
}

// "4242 4242 4242 4242" as typed, 16 digits as sent: payment-service accepts exactly \d{16}
export function toCardNumber(value: string): string {
  return value.replace(/[\s-]/g, "");
}

// Groups of 4 digits while typing, like on the card
export function formatCardNumber(value: string): string {
  return value
    .replace(/\D/g, "")
    .slice(0, 16)
    .replace(/(\d{4})(?=\d)/g, "$1 ");
}

export function PaymentForm({ orderId, onPayment }: Props) {
  const [card, setCard] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | undefined>();

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSending(true);
    setError(null);
    setFieldError(undefined);
    try {
      const payment = await paymentApi.pay(orderId, toCardNumber(card));
      onPayment(payment);
      if (payment.status === "SUCCEEDED") {
        setCard("");
      }
    } catch (cause) {
      // 400 on the card number, 409 for an order no longer PENDING, 503 when order-service is down
      if (cause instanceof ApiError && cause.fieldErrors.cardNumber) {
        setFieldError(cause.fieldErrors.cardNumber);
      } else {
        setError(errorMessage(cause));
      }
    } finally {
      setSending(false);
    }
  };

  return (
    <form className="form" onSubmit={onSubmit} noValidate>
      <ErrorMessage message={error} />
      <Field
        label="Numéro de carte"
        inputMode="numeric"
        autoComplete="cc-number"
        placeholder="4242 4242 4242 4242"
        required
        value={card}
        error={fieldError}
        hint="Paiement simulé : une carte finissant par 0002 est refusée, toute autre est acceptée."
        onChange={(event) => setCard(formatCardNumber(event.target.value))}
      />
      <button type="submit" className="button button-primary" disabled={sending}>
        {sending ? "Paiement…" : "Payer"}
      </button>
    </form>
  );
}
