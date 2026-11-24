# ADR 0007: Retire the monolith

- **Status:** accepted
- **Date:** 2026-11-24
- **Issue:** #124

## Context

The project started as a monolith (phases 1 to 4: catalog, JWT security, cart, orders, tests, Docker). Phase 5
extracted it piece by piece, strangler style: each service took over a group of routes, and the gateway kept a
catch-all route to the monolith for whatever had not moved yet.

That migration is over. auth-service, product-service, order-service and payment-service own every route the
monolith had (`/api/auth`, `/api/products`, `/api/categories`, `/api/cart`, `/api/orders`), and more (payments,
stock reservation, the cart fallback). The catch-all route never matched a request any more, yet the monolith
still ran:

- one more JVM, with 512 MB of the 4 GB of the server (ADR 0004), its own database, image and CI jobs;
- a second copy of every table and every rule, kept in sync by hand, that no request used;
- a stack harder to explain: two ways to place an order, only one of them reachable.

## Decision

**The monolith is removed from the repository and from the stack.**

- Docker Compose (local, published images, production), the gateway, Prometheus, Grafana and Zipkin no longer
  know it. The gateway has no catch-all route: a path no service owns gets a 404 from the gateway itself.
- CI no longer builds, tests or publishes it.
- The `monolith/` folder is deleted. Its last version stays in the history, under the git tag
  [`monolith-final`](https://github.com/monssefbaakka/ecommerce-microservices/tree/monolith-final), with its
  tests, Dockerfile and Flyway scripts.

## Alternatives not chosen

- **Keep it running behind the catch-all route.** Costs memory and maintenance for no traffic, and hides the
  fact that the migration is done.
- **Keep the folder, out of the stack.** Code that is neither run nor deployed drifts from the services and
  still has to be read and built. The tag keeps it just as reachable, without the cost.

## Consequences

- The stack is six JVMs instead of seven: the gateway, Eureka and the four services.
- The `ecommerce` database is no longer used by any application. It stays: it is the default database of the
  PostgreSQL user, the one the health check connects to. On a new install it is empty; on the server it still
  holds the monolith's old rows, which nothing reads.
- The `ecommerce-monolith` images already published stay on GitHub Container Registry until deleted there; no
  new one is pushed.
- The ADRs before this one still mention the monolith: they describe the stack as it was when they were
  accepted.
- Reading the phases 1 to 4 code means checking out `monolith-final`, or browsing it on GitHub.
