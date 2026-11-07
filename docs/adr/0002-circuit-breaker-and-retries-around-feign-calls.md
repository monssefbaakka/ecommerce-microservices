# ADR 0002: Circuit breaker and retries around the Feign calls

- **Status:** accepted
- **Date:** 2026-11-07
- **Issues:** #49, #50

## Context

Since [ADR 0001](0001-openfeign-for-calls-between-services.md), order-service calls product-service and
payment-service calls order-service through OpenFeign clients, with timeouts of 2 s (connect) and 5 s (read).
Any failed call was a **503** right away:

- a single dropped connection or a restart of the other service failed the request of the user, although
  asking again 200 ms later would have worked;
- a service that hangs cost every request a full read timeout (5 s), and each of them held a thread and a
  connection while waiting;
- the gateway had no timeout at all: a service that never answers held the request forever.

Some of the calls change data: reserve or release the stock, mark an order paid. Sending one of them twice
reserves the stock twice, or answers **409** to a payment that went through.

## Decision

Use Resilience4j, called from code in a small `RemoteCalls` class of each service, not through annotations.
`ProductClient` and `OrderClient` send every call through it: `calls.read(...)` for a `GET`,
`calls.write(...)` for a `POST`.

- **Order: circuit breaker around the retry.** A call counts once for the circuit breaker, after its retries.
  The other order (the default of the Resilience4j annotations) counts every attempt: with one instance down
  out of two, half the attempts failed and the circuit breaker opened although every call succeeded on the
  other instance. A test with one stopped instance in the LoadBalancer showed it.
- **One circuit breaker per service called** (`product-service`, `order-service`), set in `application.yml`:
  last 10 calls, 5 at least, opens at 50 % failed or slower than 3 s, stays open 10 s, then lets 3 calls
  through. Recorded: `FeignException` (refused, dropped, timeout) and `ServiceUnavailableException` (5xx, not in
  Eureka). A **404** or **409** is an answer and counts as a success: a user short of stock must not cut the
  others off.
- **Two retries per service called**, 3 attempts at most, 200 ms then 400 ms apart:
  - reads (`RetryableReadFailure`): refused or dropped connection, 5xx, no instance yet. Never a timeout: a
    hanging service would hang again, and three attempts would cost 15 s instead of 5 s;
  - writes (`RequestNeverSent`): only a refused connection or an unknown host, when the request surely never
    left. Never after a timeout, a dropped connection or a 5xx, where the other side may have done the work.
- **No resend by the JDK.** The `HttpURLConnection` under Feign sends a `POST` without body ("mark paid")
  again by itself when the connection drops. `sun.net.http.retryPost=false` is set in `main` and for the
  tests. A WireMock test that drops the connection showed the second request.
- **Gateway timeouts:** 2 s to connect, 15 s for the answer, a **504** past it. 15 s is longer than two calls
  between services in a row (5 s each), so the **503** of the service comes first. The gateway does not retry:
  retries at both levels would multiply the requests (3 × 3).

## Alternatives not chosen

- **`spring.cloud.openfeign.circuitbreaker.enabled`:** wraps each Feign method in a Spring Cloud circuit
  breaker named after the method. Without a fallback, every error comes back wrapped in a
  `NoFallbackAvailableException`, so the 404 / 409 pass-through of the decoder would need unwrapping. No retry
  either, and one breaker per method instead of one per service.
- **`@CircuitBreaker` / `@Retry` annotations:** need Spring AOP, put the retry outside the circuit breaker by
  default, and are skipped on calls inside the same bean (`ProductClient.find` calls `findAll`).
- **The Feign `Retryer`:** retries any `RetryableException`, timeouts and `POST` included, with no way to tell a
  read from a write.

## Consequences

- A short failure (restart, dropped connection, one instance down) no longer reaches the user on reads.
- When a service is down, after 5 failed calls the others get a **503** at once for 10 s instead of waiting.
- Worst case for a read: 3 quick failures and 0.6 s of waits, or one read timeout (5 s).
- A write that may have been received and failed is still a **503**: whether it was done is not known.
  Making the internal writes idempotent (a request id) would allow resending them.
- No fallback yet: an open circuit breaker gives a **503**, not a cached answer (#51).
- `RemoteCalls` and the two predicates are copied in each calling service, like the decoder of ADR 0001.
