# Architecture decision records

Each file records one decision: the context, what was decided, the alternatives that were turned down and the
consequences. An ADR is never rewritten once accepted; a later decision that changes it gets a new number and
links back.

| ADR | Decision | Status | Issue |
|---|---|---|---|
| [0001](0001-openfeign-for-calls-between-services.md) | OpenFeign for the calls between services | Accepted, extended by 0002 | #48 |
| [0002](0002-circuit-breaker-and-retries-around-feign-calls.md) | Circuit breaker and retries around the Feign calls | Accepted | #49, #50 |
| [0003](0003-fallback-for-the-cart-only.md) | A fallback for showing the cart only | Accepted | #51 |
| [0004](0004-deploy-on-a-single-server-with-docker-compose.md) | Deploy on a single server with Docker Compose | Accepted | #59 |
| [0005](0005-flyway-migrations-instead-of-hibernate-ddl.md) | Flyway migrations instead of Hibernate DDL | Accepted | #114, #115, #116 |
| [0006](0006-zipkin-prometheus-and-grafana-for-observability.md) | Zipkin, Prometheus and Grafana for observability | Accepted | #118, #119, #120 |
| [0007](0007-retire-the-monolith.md) | Retire the monolith | Accepted | #124 |

To add one, copy the layout of the last file (title, status, date, issue, then *Context*, *Decision*,
*Alternatives not chosen* when there were some, *Consequences*), take the next number and add a row here. See
[architecture.md](../architecture.md) for how these decisions fit together.
