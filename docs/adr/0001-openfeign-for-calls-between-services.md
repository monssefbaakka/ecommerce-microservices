# ADR 0001: OpenFeign for the calls between services

- **Status:** accepted; retries and circuit breaker added by [ADR 0002](0002-circuit-breaker-and-retries-around-feign-calls.md)
- **Date:** 2026-11-05
- **Issue:** #48

## Context

Since phase 5, order-service calls product-service (prices, stock reservations) and payment-service calls
order-service (owner, status and total of an order, then `PAID`). Each call went through a `@LoadBalanced`
`RestClient` built by hand in the client class: base URL property, interceptor for the `SERVICE` token,
status handler, JSON parsing of the error body. The same 80 lines were repeated in both services, and a new
endpoint meant new plumbing.

## Decision

Use Spring Cloud OpenFeign for every call to the `/internal/**` endpoints of another service:

- One interface per remote service (`ProductServiceApi`, `OrderServiceApi`). The `@FeignClient` name is the
  Eureka name: the LoadBalancer resolves it on each call, no URL property is needed.
- A configuration class per client (not a `@Configuration`, so its beans stay on that client only) with:
  - `ServiceTokenInterceptor`: a fresh, short-lived token with the `SERVICE` role on each request, never the
    user's token;
  - `RemoteErrorDecoder`: **404** and **409** are passed on with the message of the other service, anything
    else (401 / 403 between services, 5xx, no instance) becomes `ServiceUnavailableException` (**503**).
- The existing client classes (`ProductClient`, `OrderClient`) stay as the only entry point of the business
  code. They turn the `FeignException` of a failed connection or a timeout into the same **503**.
- `@EnableFeignClients` lives in its own `FeignConfig`, not on the application class, so `@WebMvcTest` slices do
  not try to build the clients.
- Timeouts of 2 s (connect) and 5 s (read) instead of the Feign defaults of 10 s and 60 s; `BASIC` logs, which
  never contain the headers.

## Consequences

- The HTTP contract of each remote service is a short interface with Spring MVC annotations.
- The Feign clients are tested over real HTTP against WireMock, resolved through the LoadBalancer from a static
  instance list instead of Eureka. The integration tests keep mocking the client classes.
- No retry and no circuit breaker yet: a failed call is a **503** right away. Resilience4j is the next step
  of phase 6.
- The interceptor and the decoder are copied in each service that calls another one. A shared library would
  remove the copy, at the price of a common release cycle between services.
