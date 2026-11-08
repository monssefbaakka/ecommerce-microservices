# ADR 0003: A fallback for showing the cart only

- **Status:** accepted
- **Date:** 2026-11-08
- **Issue:** #51

## Context

Since [ADR 0002](0002-circuit-breaker-and-retries-around-feign-calls.md), a call to another service that fails
after its retries, or that its open circuit breaker stops, is a **503**. Two services call another one:

- order-service reads names, prices and stock from product-service to show the cart, to check the stock when
  a product is added to it, and to price an order; it reserves and releases the stock;
- payment-service reads the owner, status and total of an order from order-service and marks it paid.

With product-service down, a user cannot even look at their cart, although the cart itself lives in
order-service. A fallback gives another answer instead of the error. A wrong answer is worse than a 503
wherever something is decided from it.

## Decision

**Only the display of the cart falls back.** Every other call keeps its 503.

- `ProductClient.findAll` remembers every answer of product-service in `LastKnownProducts`, a map in memory
  by product id. A product asked for but missing from the answer was deleted: it is forgotten.
- `ProductClient.findAllOrLastKnown` returns a `ProductSnapshot`: the answer of product-service, or, on a
  `ServiceUnavailableException` (down, failing, not registered, circuit breaker open), the last known products
  with `upToDate = false`. A 404 or any other error is not hidden.
- The cart (`GET /api/cart`, and the cart returned after a change) uses it. The answer stays **200** and says
  `"productsUpToDate": false`. A line whose product was never seen by this instance keeps its quantity, with no
  name, price nor subtotal, and the total is `null`: a total without some lines would be wrong.
- No fallback where the answer decides something:
  - adding to the cart or changing a quantity checks the stock: an old stock would let the user add units that
    are gone;
  - placing an order copies the prices: an old price would be charged;
  - reserving or releasing the stock, marking an order paid: writes have no other answer than doing them;
  - a payment reads the status and total of the order: an old `PENDING` would let an order be paid twice.

## Alternatives not chosen

- **A cache that answers first (Spring Cache, with a lifetime):** the cart would show old prices even when
  product-service is up. Here product-service is always asked first, and the last answer is only a spare.
- **An empty cart, or lines with the quantity only, without any memory:** says nothing wrong but shows
  nothing useful; the last name and price are what the user saw a moment ago.
- **A fallback in the gateway (`fallbackUri` of the Spring Cloud CircuitBreaker filter):** it only knows that
  a whole service is down, not which call can be answered from older data; it already answers 503 / 504 in the
  common format.
- **A `FallbackFactory` on the Feign client:** needs `spring.cloud.openfeign.circuitbreaker.enabled`, set
  aside in ADR 0002, and would apply to every caller of the method, the order included.

## Consequences

- With product-service down, a user can still see and empty their cart, and remove lines; the answer says the
  names and prices may be old.
- The memory is per instance and lost on restart: an instance that never saw a product shows the line without
  name nor price. It grows with the number of products asked for, at most the catalog.
- Product data only: names, prices and stock are the same for every user, nothing personal is kept.
- A client must read `productsUpToDate` and the possible `null` values of `total`, `productName`, `unitPrice`
  and `subtotal`.
