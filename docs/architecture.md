# Architecture

How the platform fits together once every phase is done: what runs where, who talks to whom, how a request
crosses the stack, how it is secured and how a commit reaches the server. The [README](../README.md) holds the
endpoints and the commands; the decisions behind the design are in [adr/](adr/README.md).

## System overview

A browser only ever talks to Caddy. Caddy sends `/api/**` to the gateway and every other path to the
frontend. The gateway finds the services through Eureka and forwards each request to the one owning the
route. Each service owns its own database in a single PostgreSQL instance.

```mermaid
flowchart LR
    browser([Browser])

    subgraph server["Linux server: Docker Compose"]
        caddy["Caddy :80 / :443<br/>HTTPS, Let's Encrypt"]

        subgraph edge["network: edge"]
            frontend["frontend :8080<br/>nginx + React build"]
        end

        subgraph backend["network: backend"]
            gateway["api-gateway :8090<br/>Spring Cloud Gateway"]
            eureka["discovery-server :8761<br/>Eureka"]
            auth["auth-service :8081"]
            product["product-service :8082"]
            order["order-service :8083"]
            payment["payment-service :8084"]
            monolith["monolith :8080"]
        end

        subgraph database["network: database"]
            postgres[("PostgreSQL 16<br/>auth_db, product_db, order_db,<br/>payment_db, ecommerce")]
        end
    end

    browser -- "HTTPS" --> caddy
    caddy -- "pages, assets" --> frontend
    caddy -- "/api/**" --> gateway
    gateway -- "/api/auth/**" --> auth
    gateway -- "/api/products/**, /api/categories/**" --> product
    gateway -- "/api/cart/**, /api/orders/**" --> order
    gateway -- "/api/payments/**" --> payment
    gateway -- "other /api/**" --> monolith
    gateway -. "where is each service?" .-> eureka
    order -- "Feign: /internal/products, /internal/stock/**" --> product
    payment -- "Feign: /internal/orders/**" --> order
    auth --> postgres
    product --> postgres
    order --> postgres
    payment --> postgres
    monolith --> postgres
```

Every service also registers itself in Eureka and sends it its health; those arrows are left out to keep the
picture readable.

### Containers

| Container | Image | Networks | Published on the server | Role |
|---|---|---|---|---|
| caddy | `caddy:2.11-alpine` | edge | 80, 443 (TCP and UDP) | Only public entry point: HTTPS certificate, compression, `/api/**` to the gateway, the rest to the frontend |
| frontend | `ecommerce-frontend` | edge | - | Static React build served by unprivileged nginx; `index.html` for every route of the app |
| api-gateway | `ecommerce-api-gateway` | edge, backend | - | Routes `/api/**` to the services by their Eureka name, uniform `ErrorResponse`, timeouts |
| discovery-server | `ecommerce-discovery-server` | backend | `127.0.0.1:8761` (through an SSH tunnel) | Eureka registry |
| auth-service | `ecommerce-auth-service` | backend, database | - | Accounts, login, JWT signing |
| product-service | `ecommerce-product-service` | backend, database | - | Categories, products, stock |
| order-service | `ecommerce-order-service` | backend, database | - | Carts and orders |
| payment-service | `ecommerce-payment-service` | backend, database | - | Simulated card payments |
| app (monolith) | `ecommerce-monolith` | backend, database | - | Phases 1 to 4; still answers the `/api/**` routes no service owns |
| postgres | `postgres:16-alpine` | database | - | One instance, one database per owner, created by `infra/postgres/init-databases.sql` |

### Networks

The three Docker networks split who can reach whom, independently of the published ports:

- **edge**: Caddy, the frontend and the gateway. From the public side, nothing behind the gateway can even be
  resolved: a bug in nginx or Caddy cannot reach a service, Eureka or PostgreSQL directly.
- **backend**: the gateway, Eureka, the services and the monolith. Calls between services stay here.
- **database**: PostgreSQL and the five applications that own a database. The gateway and Eureka are not on
  it.

On a laptop (`infra/docker-compose.yml` alone) each service also publishes its port on the host, to call it
directly or open its Swagger UI. The production override (`infra/docker-compose.prod.yml`) removes them all;
CI fails if any port other than Caddy's is published on every interface.

### Data ownership

| Database | Owner | Tables |
|---|---|---|
| `auth_db` | auth-service | `users` |
| `product_db` | product-service | `categories`, `products` |
| `order_db` | order-service | `carts`, `cart_items`, `orders`, `order_items` |
| `payment_db` | payment-service | `payments` |
| `ecommerce` | monolith | its own copy of every table above |

No service reads another one's tables and there is no foreign key between databases. A row keeps the
`productId`, `orderId` or user email it refers to, and the owner is asked over HTTP when the current value
matters (price, stock, order status).

Each owner also owns the **schema** of its database: its Flyway scripts (`src/main/resources/db/migration`) run
when it starts, before JPA, and are recorded in the `flyway_schema_history` table of that database. Hibernate
only validates the tables against the entities. A schema change is deployed with the service that needs it,
never as a separate step ([ADR 0005](adr/0005-flyway-migrations-instead-of-hibernate-ddl.md)).

## A request through the stack

Placing an order, then paying it, touches every layer. The JWT travels with each request from the browser;
the calls between services carry a short-lived `SERVICE` token instead.

```mermaid
sequenceDiagram
    autonumber
    actor U as Browser
    participant C as Caddy
    participant G as api-gateway
    participant O as order-service
    participant P as product-service
    participant Pay as payment-service

    U->>C: POST /api/orders (Bearer user JWT)
    C->>G: POST /api/orders
    G->>O: lb://order-service (found in Eureka)
    O->>O: check the JWT, lock the cart of its email
    O->>P: POST /internal/stock/reservations (SERVICE token)
    P->>P: SELECT ... FOR UPDATE on the products, take the units
    P-->>O: 200 reserved lines (name, price) or 409 / 404
    O->>O: save the PENDING order, empty the cart
    Note over O,P: if the save fails, POST /internal/stock/releases gives the units back
    O-->>U: 201 Location: /api/orders/{id}

    U->>C: POST /api/payments {orderId, cardNumber}
    C->>G: POST /api/payments
    G->>Pay: lb://payment-service
    Pay->>Pay: check the JWT, pg_advisory_xact_lock(orderId)
    Pay->>O: GET /internal/orders/{id} (SERVICE token)
    O-->>Pay: owner, status, total
    Pay->>Pay: simulate the card, save the payment
    Pay->>O: POST /internal/orders/{id}/paid
    O-->>Pay: order PAID
    Pay-->>U: 201 SUCCEEDED (or DECLINED, order stays PENDING)
```

What can go wrong on the way, and what the user gets:

| Failure | Where it is caught | Answer |
|---|---|---|
| Product short on stock | product-service, under the row lock | **409**, nothing reserved, cart untouched |
| Two clicks on "order" | order-service, cart lock | One order; the second request finds the cart empty (**400**) |
| Two clicks on "pay" | payment-service, advisory lock | One payment; the second finds the order `PAID` (**409**) |
| product-service or order-service down, slow or failing | Feign client, retries then circuit breaker | **503** at once while the breaker is open ([ADR 0002](adr/0002-circuit-breaker-and-retries-around-feign-calls.md)) |
| product-service down while showing the cart | order-service fallback | **200** with the last known names and prices, `productsUpToDate: false` ([ADR 0003](adr/0003-fallback-for-the-cart-only.md)) |
| Service not registered in Eureka | gateway | **503** |
| Service answers after 15 s | gateway | **504** |
| Crash between the stock reservation and the order save, or `PAID` call lost after an accepted card | nowhere yet | Not compensated: the card is simulated. A saga or an outbox would cover it |

## Security

| Layer | How it is protected |
|---|---|
| Transport | Caddy terminates HTTPS with a Let's Encrypt certificate it renews itself; HTTP is redirected |
| Exposure | Only Caddy publishes a port on the server; Eureka listens on the loopback, reached through an SSH tunnel |
| Identity | auth-service hashes passwords with BCrypt and signs a JWT (HS256, `sub` = email, `role`, 1 h) |
| Authorization | Each service checks the signature, expiry and role of the token itself, without a database call: the gateway forwards the `Authorization` header and checks nothing |
| Roles | `USER` for every account created by `/register`; `ADMIN` only for the account created at start-up from `ADMIN_EMAIL` / `ADMIN_PASSWORD`; `SERVICE` only for tokens a service signs for its own calls |
| Internal endpoints | `/internal/**` is not routed by the gateway and only accepts a `SERVICE` token |
| Ownership | The email of the token picks the cart, orders and payments: another user's order answers **404**, like a missing one |
| Secrets | `JWT_SECRET` and `POSTGRES_PASSWORD` live in `infra/.env` on the server; the production Compose file refuses to start without them. GitHub only holds the SSH key of the deploy user |
| Containers | Every image runs as a non-root user and has a `HEALTHCHECK`; CI fails an image that does not |
| Actuator | `health` and `info` are public, `metrics` needs an `ADMIN` token, every other endpoint is closed. The gateway and Eureka do not expose their metrics |

The same `JWT_SECRET` must be set in auth-service, product-service, order-service, payment-service and the
monolith: a token signed by one is accepted by all of them.

## From a commit to the server

```mermaid
flowchart LR
    pr["Pull request"]
    push["Push to main"]
    tag["Tag v1.2.0"]

    subgraph ci["CI workflow"]
        build["Build x7<br/>mvnw verify, Testcontainers ITs, JaCoCo"]
        fe["Build frontend<br/>Vitest, tsc, Vite"]
        compose["Validate Compose files,<br/>Caddyfile, deploy.sh"]
        docker["Docker images x8<br/>non-root + HEALTHCHECK checked"]
        build --> docker
        fe --> docker
    end

    pr --> ci
    push --> ci
    tag --> ci

    docker -- "main, tags only" --> ghcr[("ghcr.io<br/>latest, sha-#lt;commit#gt;, 1.2.0")]
    docker -- "pull request" --> stop(["built and checked, not pushed"])
    ghcr --> deploy["Deploy workflow<br/>after a green CI run on main"]
    deploy -- "SSH: copy infra/, run deploy.sh sha-#lt;commit#gt;" --> server["Server<br/>pull, wait until healthy,<br/>roll back if not"]
    server --> smoke["Call the public URL"]
```

- An image is only built from code whose tests passed, and only pushed from `main` or a release tag.
- The server always runs an exact commit (`sha-<commit>`), never `latest`. A roll back is a manual run of the
  *Deploy* workflow with an older tag.
- `deploy.sh` keeps the previous version and starts it again if a container of the new one never becomes
  healthy.

Details: [Continuous integration](../README.md#continuous-integration) in the README and the
[deployment guide](deployment.md).

## Architecture decisions

The choices that shaped this design, with the alternatives considered, are recorded as ADRs in
[adr/](adr/README.md): OpenFeign between services, the circuit breaker and retry rules, why only the cart has a
fallback, and why the stack runs on a single server with Docker Compose.
