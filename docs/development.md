# Development

How to run the stack on a laptop, run the tests, work on the frontend, and where the code lives. The API itself
is described in [api.md](api.md).

## Prerequisites

- Docker Desktop (running): enough to run the whole stack
- Java 21, to build or test a service outside Docker (no Maven needed: each service ships with the Maven wrapper)
- Node.js 22.12 or later, to work on the frontend

## Run the stack with Docker

```bash
docker compose -f infra/docker-compose.yml --profile app up -d --build --wait
```

| Container | URL | Role |
|---|---|---|
| `ecommerce-frontend` | `http://localhost:3000` | The web shop, in a browser |
| `ecommerce-api-gateway` | `http://localhost:8090` | Single entry point of the API: use this one |
| `ecommerce-auth-service` | `http://localhost:8081` | Register and login |
| `ecommerce-product-service` | `http://localhost:8082` | Categories and products |
| `ecommerce-order-service` | `http://localhost:8083` | Cart and orders |
| `ecommerce-payment-service` | `http://localhost:8084` | Payments |
| `ecommerce-discovery-server` | `http://localhost:8761` | Eureka dashboard |
| `ecommerce-postgres` | `localhost:5432` | PostgreSQL |
| `ecommerce-grafana` | `http://localhost:3001` | Dashboards (`admin` / `admin` in dev), see [observability.md](observability.md) |
| `ecommerce-prometheus` | `http://localhost:9090` | Metrics history and queries |
| `ecommerce-zipkin` | `http://localhost:9411` | Traces: one per request, across the services |

`--wait` returns once every container is `healthy`, i.e. answers on `/actuator/health/readiness` (`docker ps` shows the same status). The services wait for Eureka and PostgreSQL, the gateway starts last, once all of them are healthy: when the command returns, the first request through `:8090` gets an answer, not a 503 from a service still starting (about 1 min, plus the build the first time). Each one has its own database (`auth_db`, `product_db`, `order_db`, `payment_db`), created by `infra/postgres/init-databases.sql` the first time the volume starts. With a volume created before, the services stop on `database "auth_db" does not exist`: run `docker compose -f infra/docker-compose.yml down -v` once (it deletes the data, the databases are created on the next `up`), or keep the data and create them by hand with `docker exec ecommerce-postgres psql -U ecommerce -d ecommerce -c "CREATE DATABASE auth_db"` (same for `product_db`, `order_db`, `payment_db`). Without `--profile app`, Compose only starts PostgreSQL, for [a service run without Docker](#run-a-service-without-docker).

The containers do not share one network. Each joins only the ones it needs:

| Network | Containers | Why |
|---|---|---|
| `ecommerce-backend` | Eureka, the gateway and the four services, Prometheus and Zipkin | They find each other through Eureka and call each other (gateway routes, Feign clients); Prometheus reads their metrics, Zipkin receives their spans |
| `ecommerce-database` | PostgreSQL and the four services | Only the containers that own a database reach PostgreSQL |
| `ecommerce-edge` | the gateway and the frontend (and Caddy on the server) | The public side: the frontend reaches the gateway, nothing behind it |
| `ecommerce-monitoring` | Grafana, Prometheus and Zipkin | Grafana queries the other two, and reaches no service, Eureka or PostgreSQL |

The gateway, the public entry point, and Eureka cannot even resolve the `postgres` host name: `docker exec ecommerce-api-gateway wget -qO- -T 2 http://postgres:5432` answers `bad address`. The published ports (`8090`, `5432`...) are not affected: the host still reaches every container.

To create an ADMIN account or set your own JWT key, copy `infra/.env.example` to `infra/.env` and fill it in (the file is ignored by git). Every service reads it, so they share the same key. Without `JWT_SECRET` there, Compose passes a dev-only key written in `docker-compose.yml`: the services themselves have no default and stop at startup without one.

Each service has its own Dockerfile, built the same way (e.g. [services/order-service/Dockerfile](../services/order-service/Dockerfile)), in two stages: the JDK and Maven only exist in the build stage, the final image holds a JRE and the application, split into Spring Boot layers so that a code change only rebuilds the last one. It runs as a non-root user and sizes the heap from the container memory limit (`JAVA_TOOL_OPTIONS`).

In Compose, each Spring container is limited to 512 MB, so the JVM takes at most 384 MB of heap instead of 75% of all the memory given to Docker (a started service uses about 250 to 400 MB, `docker stats`). Its logs rotate at 10 MB, 3 files kept. These settings, the `app` profile and the restart policy are written once in the `x-spring-container` block of [infra/docker-compose.yml](../infra/docker-compose.yml) and merged into each service.

### Without building: the published images

The images built and tested by the CI are on GitHub Container Registry ([Published images](ci.md#published-images)). With the override [infra/docker-compose.ghcr.yml](../infra/docker-compose.ghcr.yml), Compose pulls them instead of building: no JDK and no Maven build on the machine, only Docker and this repository's `infra/` folder.

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.ghcr.yml --profile app up -d --wait
```

Only the image of each service changes: networks, health checks, memory limits and environment come from `docker-compose.yml`. The images are pulled again on every `up` (`latest` moves). `IMAGE_TAG` picks another version (`sha-<short sha>`, `1.2.0`, `1.2`), `IMAGE_REGISTRY` the images of a fork, both settable in `infra/.env`:

```bash
IMAGE_TAG=1.0.0 docker compose -f infra/docker-compose.yml -f infra/docker-compose.ghcr.yml --profile app up -d --wait
```

### Stop

```bash
docker compose -f infra/docker-compose.yml --profile app stop   # --profile app also stops the service containers
```

## Run a service without Docker

Start PostgreSQL alone (`docker compose -f infra/docker-compose.yml up -d`, the databases are created on the
first start), set a JWT key in the shell (the services have no default), then start each one in its own
terminal, in this order:

```bash
export JWT_SECRET=$(openssl rand -base64 32)                # the same value in every terminal
cd services/discovery-server && ./mvnw spring-boot:run   # wait for "Started", dashboard on :8761
cd services/auth-service && ./mvnw spring-boot:run       # on Windows: mvnw spring-boot:run
cd services/product-service && ./mvnw spring-boot:run
cd services/order-service && ./mvnw spring-boot:run
cd services/payment-service && ./mvnw spring-boot:run
cd services/api-gateway && ./mvnw spring-boot:run        # then call http://localhost:8090/api/...
```

Each service serves its Swagger UI on its own port (`/swagger-ui.html` on `:8081` to `:8084`): every endpoint
with its request and response bodies, the paging parameters and every error code. To call a protected
endpoint: run `POST /api/auth/login`, copy the `token`, click **Authorize** and paste it. Ready-to-run requests
for VS Code (REST Client) or IntelliJ: [requests.http](requests.http).

## Tests

Each service is a separate Maven project with its own tests, run from its folder:

```bash
cd services/order-service    # or discovery-server, auth-service, product-service, payment-service, api-gateway
./mvnw test      # unit and slice tests (*Test): fast, no database, no Docker
./mvnw verify    # + integration tests (*IT) on a real PostgreSQL, + coverage report
```

| Kind | Classes | What they check |
|---|---|---|
| Unit | `*ServiceTest`, `*MapperTest`, `CartTest`, `OrderTest`, ... | Business rules with Mockito, no Spring context |
| Slice | `*ControllerTest`, `SecurityConfigTest` | HTTP layer with MockMvc: status codes, JSON, validation, security rules |
| Integration | `*IT` (`AuthFlowIT`, `CartFlowIT`, `OrderFlowIT`, `StockIT`, ...) | The whole service on PostgreSQL 16: real JWTs, transactions, concurrent orders, Flyway scripts |

The integration tests start a `postgres:16-alpine` container with [Testcontainers](https://testcontainers.com/):
Docker must be running, nothing else to set up. Without Docker they are skipped, not failed (CI fails if any was
skipped). Each test starts from empty tables. The coverage report is written to `target/site/jacoco/index.html`.
The tests sign their tokens with their own key, from `src/test/resources/config/application.yml`.

| Service | What the tests check |
|---|---|
| discovery-server | The registry starts, accepts registrations, serves the dashboard; only `health` and `info` are exposed (no `metrics`: no authentication) |
| auth-service | JWT, register / login rules, `ErrorResponse` bodies, security chain with MockMvc; `*IT` on PostgreSQL with Testcontainers (register then login, Actuator with `metrics` for ADMIN only, OpenAPI) |
| product-service | JWT check, category and product rules, paging and search, `ErrorResponse` bodies, security chain (401 / 403) with MockMvc; `*IT` on PostgreSQL with Testcontainers (catalog flow, Actuator with `metrics` for ADMIN only, OpenAPI) |
| product-service (stock) | Reservations all or nothing, release, `/internal/**` only for the `SERVICE` role; `StockIT` on PostgreSQL (rollback, last unit under concurrency) |
| order-service | JWT check, cart and order rules (stock, per-line maximum, stock given back if the order cannot be saved), product-service Feign client against a fake product-service in WireMock, found through the LoadBalancer (`SERVICE` token sent, 404 / 409 passed on, 503 when it fails, rejects the token, drops the connection, answers after the read timeout or is not registered), retries (reads up to 3 times, a reservation never twice, not after a timeout, the live instance reached when the other one is down), circuit breaker (opens after 5 failed calls, ignores 409, closes again, read from `application.yml`), fallback (last known products when product-service fails or its circuit breaker is open, forgotten once deleted, lines never seen kept without price, never for an order), cancellation (stock given back, **409** once paid or cancelled, nothing changes when product-service is down), expiry job (only orders older than the timeout, a paid one left alone, stops while product-service is down), business counters (`orders.placed` only once saved, `orders.cancelled` by reason, `cart.fallback` on each fallback), error decoder, `/internal/orders` for the `SERVICE` role only, mappers, `ErrorResponse` bodies; `*IT` on PostgreSQL with Testcontainers and an in-memory product-service (cart and order flows, cancellation and expiry of unpaid orders, cart shown while product-service is down, concurrent orders, deleted products, Actuator with `metrics` for ADMIN only (circuit breaker, retries and business counters listed), OpenAPI) |
| payment-service | JWT check, simulated card, payment rules (`PAID` set in order-service on success, declined attempts kept, owner checked, 404 / 409 / 503), order-service Feign client against a fake order-service in WireMock (`SERVICE` token sent, 404 / 409 passed on, 503 when it fails, rejects the token, drops the connection, answers after the read timeout or is not registered), retries (reads up to 3 times, "mark paid" never twice, not after a timeout, the live instance reached when the other one is down), circuit breaker (opens after 5 failed calls, ignores 409, closes again, read from `application.yml`), `payments` counter by outcome (nothing counted when order-service refuses "mark paid"), error decoder, `ErrorResponse` bodies; `*IT` on PostgreSQL with Testcontainers and an in-memory order-service (payment flow, double payment stopped by the advisory lock, Actuator with `metrics` for ADMIN only, OpenAPI) |
| api-gateway | Routing to every service against stub services, 404 without route (no catch-all), 503 when a service is not registered or not answering, 504 when it answers after the response timeout, Actuator (no `metrics`: no JWT check in the gateway) |

## Frontend

A small web shop in [frontend/](../frontend/), on top of the gateway: React 19 and TypeScript, built by Vite, no UI library. It only calls `/api/**` on its own origin, so the gateway needs no CORS setting.

| Page | Path | Who |
|---|---|---|
| Catalog: search by name, filter by category, sort, pages (kept in the URL) | `/` | everyone |
| Product, add to cart (up to the stock left) | `/products/:id` | everyone, cart once logged in |
| Login, register (then logged in at once) | `/login`, `/register` | visitors |
| Cart: quantities, remove, empty, place the order | `/cart` | logged in |
| Order history, and one order with its payment form and a cancel button while `PENDING` | `/orders`, `/orders/:id` | logged in |
| Payment history, declined attempts included | `/payments` | logged in |
| Categories and products: create, edit, delete | `/admin` | `ADMIN` |

The JWT of auth-service is kept in `localStorage` and sent as `Authorization: Bearer` with each request. The app only reads its claims (email, role, expiry date) to show the right links; the services still check the signature and the role on every request. The user is logged out at the expiry date of the token, or as soon as a request sent with it gets a 401. The messages of the services (`ErrorResponse`) are shown as they come, under the right field for a 400. When product-service is down, the cart says that its prices are the last known ones and the order button waits for it to come back.

Payments are simulated by payment-service: a card number ending in `0002` is declined, any other one of 16 digits is accepted (`4242 4242 4242 4242`). The first `ADMIN` account comes from `ADMIN_EMAIL` and `ADMIN_PASSWORD` in `infra/.env`.

### Run it in development

The stack must be running (at least the gateway on `:8090`, see [Run the stack with Docker](#run-the-stack-with-docker)). Node.js 22.12 or later:

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, /api/** forwarded to http://localhost:8090
```

Pages reload on each save. `API_URL=https://shop.example.com npm run dev` points the dev server to another gateway.

| Command | What it does |
|---|---|
| `npm test` | Vitest unit tests (JWT reading, fetch client, query strings, formatting) |
| `npm run typecheck` | TypeScript check, strict mode |
| `npm run build` | Type check, then the static files in `dist/` |

### In a container

[frontend/Dockerfile](../frontend/Dockerfile) builds the app with Node.js, then keeps only the static files and nginx (unprivileged image: never root, port 8080, about 26 MB). [frontend/nginx/default.conf.template](../frontend/nginx/default.conf.template) answers `index.html` for every route of the app (a reload on `/orders/12` works), lets the browser keep the hashed assets for a year, and forwards `/api/**` to `API_GATEWAY_URL` (`http://api-gateway:8090` by default), looking up the address of the gateway again every 10 s so that recreating the gateway does not break it. On the server, Caddy sends `/api/**` to the gateway itself and only the pages to this container.

## Project structure

```
.
├── services/        one Maven project per service, each with its Dockerfile
│   ├── discovery-server/   Eureka registry
│   ├── auth-service/       register, login, JWT signing
│   ├── product-service/    categories, products and stock, ADMIN writes checked from the JWT
│   ├── order-service/      cart and orders of the logged-in user, stock reserved in product-service
│   ├── payment-service/    simulated card payments, sets the order to PAID
│   └── api-gateway/        single entry point, routes /api/** to the services
│   each service: src/main/java/com/monssef/<service>/
│       ├── <feature>/      entity, repository, service, controller, mapper, dto/ (e.g. cart/, order/, product/)
│       ├── auth/           JWT filter, SERVICE tokens for the calls to another service
│       ├── common/         ErrorResponse, GlobalExceptionHandler, business metrics
│       └── config/         security, Feign, OpenAPI
│   and src/main/resources/db/migration/   Flyway scripts V1__*.sql, V2__*.sql... (one per schema change)
├── frontend/        React + TypeScript web shop (Vite), with its Dockerfile and nginx config
│   └── src/
│       ├── api/         fetch client (JWT, ErrorResponse → ApiError), one function per endpoint, JSON types
│       ├── auth/        session read from the JWT, kept in localStorage
│       ├── cart/        cart count shown in the header
│       ├── pages/       catalog, product, cart, orders, payments, login, register, admin
│       └── admin/       ADMIN forms for categories and products
├── infra/           Docker Compose file (PostgreSQL; app profile: the services, the frontend, the monitoring),
│                    .env.example, docker-compose.ghcr.yml (same stack from the images published on ghcr.io),
│                    docker-compose.prod.yml and deploy.sh (the server)
│   ├── caddy/       Caddyfile: HTTPS entry point of the server
│   ├── grafana/     data sources (provisioning/) and dashboards (dashboards/), loaded at each start
│   ├── postgres/    init-databases.sql: one database per service
│   └── prometheus/  prometheus.yml: what Prometheus scrapes (services found through Eureka)
├── docs/            architecture, decisions (adr/), API, services, database, CI, deployment, observability
└── .github/         CI and deploy workflows (workflows/), issue and pull request templates
```

The code is organized by feature rather than by layer. Controllers only ever receive and return DTOs, never
entities. Each feature has a MapStruct mapper (`ProductMapper`, `OrderMapper`, ...) that converts between the
two; the implementation is generated by Maven at compile time in `target/generated-sources/annotations`. The
build fails if a DTO field is not mapped (`unmappedTargetPolicy=ERROR`), so adding a field to a DTO cannot
silently return `null`.

The project started as a monolith (phases 1 to 4), split into these services in phase 5. Once every route had
moved, the monolith was removed ([ADR 0007](adr/0007-retire-the-monolith.md)); its last version is under the git
tag [`monolith-final`](https://github.com/monssefbaakka/ecommerce-microservices/tree/monolith-final).
