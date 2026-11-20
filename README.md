# E-Commerce Platform

[![CI](https://github.com/monssefbaakka/ecommerce-microservices/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/monssefbaakka/ecommerce-microservices/actions/workflows/ci.yml?query=branch%3Amain+event%3Apush)
[![Java](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fmonssefbaakka%2Fecommerce-microservices%2Fmain%2Fmonolith%2Fpom.xml&query=%2F%2F%2A%5Blocal-name%28%29%3D%27java.version%27%5D&label=Java&logo=openjdk&logoColor=white&color=ED8B00)](monolith/pom.xml)
[![Spring Boot](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fmonssefbaakka%2Fecommerce-microservices%2Fmain%2Fmonolith%2Fpom.xml&query=%2F%2A%5Blocal-name%28%29%3D%27project%27%5D%2F%2A%5Blocal-name%28%29%3D%27parent%27%5D%2F%2A%5Blocal-name%28%29%3D%27version%27%5D&label=Spring%20Boot&logo=springboot&logoColor=white&color=6DB33F)](monolith/pom.xml)
[![Spring Cloud](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fmonssefbaakka%2Fecommerce-microservices%2Fmain%2Fservices%2Fapi-gateway%2Fpom.xml&query=%2F%2F%2A%5Blocal-name%28%29%3D%27spring-cloud.version%27%5D&label=Spring%20Cloud&logo=spring&logoColor=white&color=6DB33F)](services/api-gateway/pom.xml)
[![Docker images](https://img.shields.io/badge/images-ghcr.io-2496ED?logo=docker&logoColor=white)](#published-images)
[![License](https://img.shields.io/github/license/monssefbaakka/ecommerce-microservices)](LICENSE)

A Spring Boot e-commerce backend, built step by step from a simple monolith to a production-style microservices architecture with Docker and CI/CD, with a small React web shop on top.

> **Status:** Phases 1 to 3 done (catalog, JWT security, cart and orders). Phase 4 done: unit and integration tests, Docker image and health checks. Phase 5 done: Eureka, API gateway, auth-service, product-service, order-service and payment-service run next to the monolith, each with its own database. Phase 6 done: the services call each other through OpenFeign clients, behind a circuit breaker and retries, the cart is still shown when product-service is down, and the gateway has timeouts. Each application publishes its metrics (HTTP, JVM, connection pool, circuit breakers, orders, payments) for an ADMIN token. Docker Compose runs the whole stack on three networks (PostgreSQL only reachable by the services that own a database, the public side only by the gateway), with health checks, memory limits and log rotation. Phase 7 done: GitHub Actions builds and tests every project, keeps the jars and builds the Docker images on each pull request, then publishes the images to GitHub Container Registry from `main` and release tags. Phase 8 in progress: each green run on `main` is deployed to a single server with Docker Compose, behind Caddy and HTTPS, and a React [frontend](#frontend) (catalog, cart, orders, payments, catalog admin) is served next to the API. See the [roadmap](#roadmap).

## Tech stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Spring Framework 7, Jackson 3, Hibernate 7), Spring Web MVC, Spring Data JPA, Bean Validation |
| Database | PostgreSQL 16, Flyway (versioned SQL migrations, schema validated by Hibernate at startup) |
| Build | Maven (wrapper included) |
| Mapping | MapStruct (entity ↔ DTO, generated at compile time) |
| Containers | Docker, Docker Compose (health checks, three networks, memory limits, log rotation) |
| API docs | OpenAPI 3 / Swagger UI (springdoc 3) |
| Security | Spring Security, JWT (jjwt) |
| Microservices | Spring Cloud 2025.1: Netflix Eureka (service registry), Spring Cloud Gateway, LoadBalancer, OpenFeign (calls between services) |
| Monitoring | Spring Boot Actuator (health, liveness / readiness probes, info, metrics), Micrometer (business counters) |
| Tests | JUnit 5, Mockito, MockMvc, Testcontainers 2, WireMock (fake services for the Feign clients), JaCoCo |
| Resilience | Resilience4j 2.4 (resilience4j-spring-boot4): circuit breaker and retries around the Feign calls, last known products as fallback for the cart |
| CI | GitHub Actions: build, tests and Docker images of every project on each pull request, images published to GitHub Container Registry (ghcr.io) |
| Frontend | React 19, TypeScript, Vite, React Router, Vitest; built into static files served by nginx |
| Deployment | Docker Compose on a single Linux server, Caddy (HTTPS, Let's Encrypt), deployed over SSH by GitHub Actions after each green CI run on `main` |

## Getting started

### Prerequisites

- Java 21
- Docker Desktop (running)

Maven is not required, the project ships with the Maven wrapper.

### Run locally

```bash
# 1. Start PostgreSQL
docker compose -f infra/docker-compose.yml up -d

# 2. Start the application
cd monolith
./mvnw spring-boot:run        # on Windows: mvnw spring-boot:run
```

The API is available at `http://localhost:8080`.

Swagger UI is available at `http://localhost:8080/swagger-ui.html`  
OpenAPI definition at `http://localhost:8080/v3/api-docs`

Swagger UI documents every endpoint with its request and response bodies (with examples), the paging parameters (`page`, `size`, `sort`) and every error code with the common `ErrorResponse` format.
To call a protected endpoint: run `POST /api/auth/login`, copy the `token`, click **Authorize** and paste it. The token is kept after a page reload.

To try the endpoints, open `docs/requests.http` in VS Code (REST Client extension) or IntelliJ.

### Run with Docker

The whole stack (PostgreSQL, the monolith, the [microservices](#microservices) and the [frontend](#frontend)) can also run in containers, without Java or Node.js on the machine:

```bash
docker compose -f infra/docker-compose.yml --profile app up -d --build --wait
```

| Container | URL | Role |
|---|---|---|
| `ecommerce-frontend` | `http://localhost:3000` | The web shop, in a browser |
| `ecommerce-api-gateway` | `http://localhost:8090` | Single entry point of the API: use this one |
| `ecommerce-app` | `http://localhost:8080` | The monolith, still reachable directly |
| `ecommerce-auth-service` | `http://localhost:8081` | Register and login |
| `ecommerce-product-service` | `http://localhost:8082` | Categories and products |
| `ecommerce-order-service` | `http://localhost:8083` | Cart and orders |
| `ecommerce-payment-service` | `http://localhost:8084` | Payments |
| `ecommerce-discovery-server` | `http://localhost:8761` | Eureka dashboard |
| `ecommerce-postgres` | `localhost:5432` | PostgreSQL |

`--wait` returns once every container is `healthy`, i.e. answers on `/actuator/health/readiness` (`docker ps` shows the same status). The services wait for Eureka and PostgreSQL, the gateway starts last, once all of them are healthy: when the command returns, the first request through `:8090` gets an answer, not a 503 from a service still starting (about 1 min, plus the build the first time). Each one has its own database (`auth_db`, `product_db`, `order_db`, `payment_db`, next to the monolith's `ecommerce`), created by `infra/postgres/init-databases.sql` the first time the volume starts. With a volume created before, the services stop on `database "auth_db" does not exist`: run `docker compose -f infra/docker-compose.yml down -v` once (it deletes the data, the databases are created on the next `up`), or keep the data and create them by hand with `docker exec ecommerce-postgres psql -U ecommerce -d ecommerce -c "CREATE DATABASE auth_db"` (same for `product_db`, `order_db`, `payment_db`). Without `--profile app`, Compose only starts PostgreSQL, for `./mvnw spring-boot:run`.

The containers do not share one network. Each joins only the ones it needs:

| Network | Containers | Why |
|---|---|---|
| `ecommerce-backend` | the monolith, Eureka, the gateway and the four services | They find each other through Eureka and call each other (gateway routes, Feign clients) |
| `ecommerce-database` | PostgreSQL, the monolith and the four services | Only the containers that own a database reach PostgreSQL |
| `ecommerce-edge` | the gateway and the frontend (and Caddy on the server) | The public side: the frontend reaches the gateway, nothing behind it |

The gateway, the public entry point, and Eureka cannot even resolve the `postgres` host name: `docker exec ecommerce-api-gateway wget -qO- -T 2 http://postgres:5432` answers `bad address`. The published ports (`8090`, `5432`...) are not affected: the host still reaches every container.

To set the JWT secret or create an ADMIN account, copy `infra/.env.example` to `infra/.env` and fill it in (the file is ignored by git). The monolith and every service read it, so they share the same secret.

Each service has its own Dockerfile, built the same way. The monolith's image is built by [monolith/Dockerfile](monolith/Dockerfile) in two stages: the JDK and Maven only exist in the build stage, the final image holds a JRE and the application, split into Spring Boot layers so that a code change only rebuilds the last one. It runs as a non-root user and sizes the heap from the container memory limit (`JAVA_TOOL_OPTIONS`).

In Compose, each Spring container is limited to 512 MB, so the JVM takes at most 384 MB of heap instead of 75% of all the memory given to Docker (a started service uses about 220 to 330 MB, `docker stats`). Its logs rotate at 10 MB, 3 files kept. These settings, the `app` profile and the restart policy are written once in the `x-spring-container` block of [infra/docker-compose.yml](infra/docker-compose.yml) and merged into each service.

#### Without building: the published images

The images built and tested by the CI are on GitHub Container Registry ([Published images](#published-images)). With the override [infra/docker-compose.ghcr.yml](infra/docker-compose.ghcr.yml), Compose pulls them instead of building: no JDK and no Maven build on the machine, only Docker and this repository's `infra/` folder.

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.ghcr.yml --profile app up -d --wait
```

Only the image of each service changes: networks, health checks, memory limits and environment come from `docker-compose.yml`. The images are pulled again on every `up` (`latest` moves). `IMAGE_TAG` picks another version (`sha-<short sha>`, `1.2.0`, `1.2`), `IMAGE_REGISTRY` the images of a fork, both settable in `infra/.env`:

```bash
IMAGE_TAG=1.0.0 docker compose -f infra/docker-compose.yml -f infra/docker-compose.ghcr.yml --profile app up -d --wait
```

To build and run the image alone:

```bash
docker build -t ecommerce-monolith monolith
docker run -p 8080:8080 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/ecommerce ecommerce-monolith
```

### Run tests

```bash
cd monolith
./mvnw test      # unit and slice tests (*Test): fast, no database, no Docker
./mvnw verify    # + integration tests (*IT) on a real PostgreSQL, + coverage report
```

| Kind | Classes | What they check |
|---|---|---|
| Unit | `*ServiceTest`, `*MapperTest`, `CartTest`, `OrderTest`, ... | Business rules with Mockito, no Spring context |
| Slice | `*ControllerTest`, `SecurityConfigTest` | HTTP layer with MockMvc: status codes, JSON, validation, security rules |
| Integration | `*IT` (`AuthFlowIT`, `CatalogIT`, `CartFlowIT`, `OrderFlowIT`, ...) | The whole application on PostgreSQL 16: real JWTs, transactions, foreign keys, concurrent orders |

The integration tests start a `postgres:16-alpine` container with [Testcontainers](https://testcontainers.com/): Docker must be running, nothing else to set up. Without Docker they are skipped, not failed.
Each test starts from empty tables. The coverage report of both runs is written to `monolith/target/site/jacoco/index.html`.

Each microservice is a separate Maven project with its own tests, run the same way from its folder:

```bash
cd services/auth-service    # or discovery-server, product-service, order-service, payment-service, api-gateway
./mvnw verify
```

| Service | What the tests check |
|---|---|
| discovery-server | The registry starts, accepts registrations, serves the dashboard; only `health` and `info` are exposed (no `metrics`: no authentication) |
| auth-service | JWT, register / login rules, `ErrorResponse` bodies, security chain with MockMvc; `*IT` on PostgreSQL with Testcontainers (register then login, Actuator with `metrics` for ADMIN only, OpenAPI) |
| product-service | JWT check, category and product rules, paging and search, `ErrorResponse` bodies, security chain (401 / 403) with MockMvc; `*IT` on PostgreSQL with Testcontainers (catalog flow, Actuator with `metrics` for ADMIN only, OpenAPI) |
| product-service (stock) | Reservations all or nothing, release, `/internal/**` only for the `SERVICE` role; `StockIT` on PostgreSQL (rollback, last unit under concurrency) |
| order-service | JWT check, cart and order rules (stock, per-line maximum, stock given back if the order cannot be saved), product-service Feign client against a fake product-service in WireMock, found through the LoadBalancer (`SERVICE` token sent, 404 / 409 passed on, 503 when it fails, rejects the token, drops the connection, answers after the read timeout or is not registered), retries (reads up to 3 times, a reservation never twice, not after a timeout, the live instance reached when the other one is down), circuit breaker (opens after 5 failed calls, ignores 409, closes again, read from `application.yml`), fallback (last known products when product-service fails or its circuit breaker is open, forgotten once deleted, lines never seen kept without price, never for an order), business counters (`orders.placed` only once saved, `cart.fallback` on each fallback), error decoder, `/internal/orders` for the `SERVICE` role only, mappers, `ErrorResponse` bodies; `*IT` on PostgreSQL with Testcontainers and an in-memory product-service (cart and order flows, cart shown while product-service is down, concurrent orders, deleted products, Actuator with `metrics` for ADMIN only (circuit breaker, retries and business counters listed), OpenAPI) |
| payment-service | JWT check, simulated card, payment rules (`PAID` set in order-service on success, declined attempts kept, owner checked, 404 / 409 / 503), order-service Feign client against a fake order-service in WireMock (`SERVICE` token sent, 404 / 409 passed on, 503 when it fails, rejects the token, drops the connection, answers after the read timeout or is not registered), retries (reads up to 3 times, "mark paid" never twice, not after a timeout, the live instance reached when the other one is down), circuit breaker (opens after 5 failed calls, ignores 409, closes again, read from `application.yml`), `payments` counter by outcome (nothing counted when order-service refuses "mark paid"), error decoder, `ErrorResponse` bodies; `*IT` on PostgreSQL with Testcontainers and an in-memory order-service (payment flow, double payment stopped by the advisory lock, Actuator with `metrics` for ADMIN only, OpenAPI) |
| api-gateway | Routing to every service and the monolith against stub services, 404 without route, 503 when a service is not registered or not answering, 504 when it answers after the response timeout, Actuator (no `metrics`: no JWT check in the gateway) |

### Stop

```bash
docker compose -f infra/docker-compose.yml --profile app stop   # --profile app also stops the app and service containers
```

## Continuous integration

Every pull request and every push to `main` runs the [CI workflow](.github/workflows/ci.yml) on GitHub Actions. Nothing to set up: the runners already have Java and Docker.

The **CI** badge at the top of this page shows the last run on `main` (pushes only: a failing pull request does not turn it red) and links to its runs. The Java, Spring Boot and Spring Cloud badges read their version from the `pom.xml` of `main` (the monolith's, and the gateway's for Spring Cloud), so they follow an upgrade without editing this file.

### Build job

One job per Maven project (the monolith and the six services), run in parallel. The migrations and the frontend have a job of their own ([below](#migrations-job)). A failing project does not stop the others, so every broken project shows up in the same run.

| Step | What it does |
|---|---|
| Build, test and package | `./mvnw -B verify`: compile, unit and slice tests, executable jar, integration tests on PostgreSQL with Testcontainers, coverage report. Each integration test starts the application on an empty database: the Flyway migrations run, then Hibernate validates the tables against the entities, so a broken migration or a forgotten one fails the build |
| No integration test skipped | Fails the job if any `*IT` was skipped. On a laptop without Docker they are skipped on purpose, but in CI a green run must mean they ran |
| Job summary | Number of unit and integration tests, line coverage and jar size, shown on the run page |
| Artifacts | The tested jar (`jar-<project>`) and the JaCoCo report (`coverage-<project>`), kept 7 days. When a job fails, the Surefire / Failsafe reports too (`test-reports-<project>`) |

The Maven dependencies are cached per project, keyed on its `pom.xml`: they are downloaded again only when that file changes. A new push on a pull request cancels the run still in progress for it.

### Migrations job

On a pull request only. Fails if the branch edits, renames or deletes a Flyway script that is already on `main` (`*/db/migration/V*.sql`): that script has run on the server, Flyway stored its checksum, and the next deploy would refuse to start. A schema change is always a new script ([Database schema](#database-schema)).

### Frontend job

`npm ci` (exactly the versions of `package-lock.json`), the Vitest unit tests, then `npm run build`: the strict TypeScript check of the whole app and the Vite build. The size of each built file goes into the job summary and the `dist/` folder is kept as the `frontend-dist` artifact (7 days). The npm cache is keyed on `package-lock.json`.

### Docker job

Once every build job and the frontend job have passed, the Docker image of each project is built from its own `Dockerfile`, as `ghcr.io/monssefbaakka/ecommerce-<project>`. An image is never built from code that fails its tests. The image is then checked: it must not run as root and must have a `HEALTHCHECK` (needed by Docker Compose to wait for it). Its user, health check and size go into the job summary.

On `main` and on a release tag, the checked image is then pushed to GitHub Container Registry (see [Published images](#published-images)). On a pull request it is only built and checked: the run never logs in to the registry.

The image layers are cached between runs, one cache per image: when only the code changed, the layer holding the Maven dependencies is reused. The push reuses the same cache, so nothing is compiled twice.

A separate job validates `infra/docker-compose.yml` with every service of the `app` profile (`docker compose config`), so that a broken Compose file fails the pull request rather than the next `docker compose up`. It does the same with `infra/docker-compose.ghcr.yml` merged over it, and fails if any service image is still a local build instead of a `ghcr.io` one. Then with the production override on top ([Deployment](#deployment)): the job fails if any port other than Caddy's is published on every interface, and it also validates the Caddyfile and runs ShellCheck on `infra/deploy.sh`.

### Published images

The eight images are on GitHub Container Registry, under the [packages of the repository](https://github.com/monssefbaakka?tab=packages&repo_name=ecommerce-microservices): `ghcr.io/monssefbaakka/ecommerce-<project>`, with `<project>` one of `monolith`, `discovery-server`, `auth-service`, `product-service`, `order-service`, `payment-service`, `api-gateway`, `frontend`.

| Tag | Published by | Points to |
|---|---|---|
| `latest` | each push to `main` | The last image of `main` that passed the tests. Moves with every merge |
| `sha-<short sha>` | each push to `main`, each release tag | The image of that exact commit. Never moves |
| `1.2.0`, `1.2` | the git tag `v1.2.0` | A release. `1.2` moves to the last patch of 1.2 |

A release is a git tag pushed to GitHub, the workflow does the rest:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The run token (`GITHUB_TOKEN`) pushes the images: no personal token or secret to create, and only the Docker job may write packages. Each image carries the OCI labels linking it to this repository and commit, an SBOM (every package and library inside it) and its build provenance (`docker buildx imagetools inspect ghcr.io/monssefbaakka/ecommerce-monolith:latest`). The tags and the digest published by a run are listed in its job summary.

A package created by its first push is **private**, even for a public repository. To let anyone pull without logging in, open each package once, then *Package settings* → *Change visibility* → *Public*. While a package is private, pulling it needs `docker login ghcr.io` with a personal access token that has the `read:packages` scope.

## Deployment

The stack runs on a single Linux server with Docker, from the images published on ghcr.io: nothing is built there. Each merge to `main` is deployed automatically once its CI run has passed. Step-by-step setup, roll back and backups: [docs/deployment.md](docs/deployment.md). Why one server rather than a PaaS or Kubernetes: [ADR 0004](docs/adr/0004-deploy-on-a-single-server-with-docker-compose.md).

| Piece | Role |
|---|---|
| [infra/docker-compose.prod.yml](infra/docker-compose.prod.yml) | Merged over `docker-compose.yml` and `docker-compose.ghcr.yml`: no port of the stack published, `JWT_SECRET` and `POSTGRES_PASSWORD` required, Caddy in front of the gateway and the frontend |
| [infra/caddy/Caddyfile](infra/caddy/Caddyfile) | The only public entry point: HTTPS for `DOMAIN` with a Let's Encrypt certificate renewed by Caddy, `/api/**` sent to the gateway, the other paths to the frontend |
| [infra/deploy.sh](infra/deploy.sh) | On the server: pulls one image version, waits until every container is healthy, starts the previous version again if one is not |
| [Deploy workflow](.github/workflows/deploy.yml) | After CI passed on `main`: copies `infra/` to the server over SSH and runs `deploy.sh` with the image of the tested commit (`sha-<commit>`, never `latest`), then calls the public URL |

The secrets stay in `infra/.env` on the server; GitHub only holds the SSH key of the deploy user (secret `DEPLOY_SSH_KEY`) and the address of the server (variables `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_KNOWN_HOSTS`, `DEPLOY_URL`). Without `DEPLOY_HOST` the workflow is skipped. A roll back is a manual run of the workflow (*Actions* → *Deploy* → *Run workflow*) with an older tag.

## Documentation

| Document | What is in it |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Architecture diagram, containers and networks, data ownership, an order and its payment across the services, security model, pipeline from a commit to the server |
| [docs/adr/](docs/adr/README.md) | Architecture decision records: why OpenFeign, the circuit breaker and retry rules, the cart-only fallback, the single-server deployment |
| [docs/deployment.md](docs/deployment.md) | Setting up the server, the first deploy, roll back and backups |
| [docs/requests.http](docs/requests.http) | Ready-to-run requests for the monolith and, through the gateway, the microservices |
| Swagger UI | `/swagger-ui.html` on the monolith (`:8080`) and on each service (`:8081` to `:8084`) |

## Microservices

Phase 5 extracted the monolith into the services in [services/](services/). The diagram below shows the routes and the calls between services; the full picture, with Caddy, the frontend and the Docker networks, is in [docs/architecture.md](docs/architecture.md).

```mermaid
flowchart LR
    client([Client]) --> gateway["api-gateway :8090"]
    gateway -- "/api/auth/**" --> auth["auth-service :8081"]
    gateway -- "/api/products/**<br/>/api/categories/**" --> product["product-service :8082"]
    gateway -- "/api/cart/**<br/>/api/orders/**" --> order["order-service :8083"]
    gateway -- "/api/payments/**" --> payment["payment-service :8084"]
    gateway -- "other /api/**" --> monolith["monolith :8080"]
    auth -. registers .-> eureka["discovery-server :8761<br/>Eureka"]
    product -. registers .-> eureka
    order -. registers .-> eureka
    payment -. registers .-> eureka
    gateway -. registers, finds the services .-> eureka
    order -- "Feign + circuit breaker: /internal/products<br/>/internal/stock/**" --> product
    payment -- "Feign + circuit breaker: /internal/orders/**" --> order
    auth --> authdb[(auth_db)]
    product --> productdb[(product_db)]
    order --> orderdb[(order_db)]
    payment --> paymentdb[(payment_db)]
    monolith --> monolithdb[(ecommerce)]
```

| Service | Port | Role |
|---|---|---|
| [discovery-server](services/discovery-server) | 8761 | Eureka registry: each service registers under its name, the gateway asks it where they are |
| [auth-service](services/auth-service) | 8081 | `POST /api/auth/register` and `POST /api/auth/login`, extracted from the monolith with the same rules, errors and Swagger UI |
| [product-service](services/product-service) | 8082 | Catalog: CRUD on `/api/categories` and `/api/products` (paging, name search, category filter), extracted from the monolith with the same rules, errors, roles and Swagger UI |
| [order-service](services/order-service) | 8083 | Cart and orders: `/api/cart` and `/api/orders`, extracted from the monolith with the same rules (stock checks, price copied at purchase time), errors and Swagger UI. The stock is reserved in product-service |
| [payment-service](services/payment-service) | 8084 | New: `/api/payments` pays a `PENDING` order with a simulated card; a success sets the order to `PAID` in order-service |
| [api-gateway](services/api-gateway) | 8090 | Single entry point: `/api/auth/**` to auth-service, `/api/products/**` and `/api/categories/**` to product-service, `/api/cart/**` and `/api/orders/**` to order-service, `/api/payments/**` to payment-service (`lb://...`, found through Eureka), every other `/api/**` to the monolith |

- **Same tokens everywhere:** auth-service signs the JWTs with the same key and claims as the monolith, so a token from `/api/auth/login` works on every service. product-service, order-service and payment-service check them the same way, without a database call: only an `ADMIN` token can write the catalog, and the email of the token picks the cart, orders and payments. Set the same `JWT_SECRET` everywhere outside local dev.
- **A database per service:** auth-service owns `users` in `auth_db`, product-service `categories` and `products` in `product_db`, order-service `carts` and `orders` in `order_db`, payment-service `payments` in `payment_db`. No service reads the tables of another one, and there is no foreign key between databases: a cart line or an order keeps the `productId`, a cart, an order or a payment keeps the email of the token. The monolith keeps its own `ecommerce` database: an account created through auth-service does not exist there.
- **Calls between services:** what used to be a join is now an HTTP call, through an OpenFeign client: an interface (`ProductServiceApi`, `OrderServiceApi`) whose `@FeignClient` name is the Eureka name of the other service, resolved by the LoadBalancer on each call (no host or port to configure). order-service reads names, prices and stock from product-service and reserves the stock when an order is placed (all lines or none, **409** if one is short); payment-service reads the owner, status and total of an order and sets it `PAID` in order-service. These endpoints are under `/internal/**`: the gateway does not route them, and they only accept a short-lived token with the `SERVICE` role, which the calling service signs itself with the shared JWT key (no account ever gets this role). A **404** or **409** of the other service is passed on with its message (a Feign `ErrorDecoder`); if it is down, not registered, rejects the token or answers too late, the answer is **503**.
- **Circuit breaker:** each call to another service goes through a Resilience4j circuit breaker named after it (`product-service`, `order-service`), counted once per call after its retries. When half of the last 10 calls failed or took more than 3 s (5 calls at least), it opens: for 10 s the calls answer **503** at once, without any request or timeout, then 3 calls are let through to test the service again. A **404** or **409** is an answer, it counts as a success. Each change of state is logged (`Circuit breaker of product-service: State transition from CLOSED to OPEN`).
- **Fallback, for showing the cart only:** with product-service unavailable (down, failing or its circuit breaker open), the cart is still shown, **200**, with the names and prices order-service saw last and `"productsUpToDate": false`; a line whose product it never saw keeps its quantity, without name nor price, and the total is `null`. Adding to the cart, changing a quantity, placing an order and paying keep their **503**: they decide something from the stock, the prices or the status of the order, which must be current. See [ADR 0003](docs/adr/0003-fallback-for-the-cart-only.md).
- **Retries:** at most 3 attempts, 200 ms then 400 ms apart. A read (`GET`) is sent again after a refused or dropped connection, a 5xx or no instance in Eureka yet, never after a timeout: a hanging service would hang again. A write (`POST`: reserve or release the stock, mark an order paid) is sent again only when it never left (connection refused, unknown host), so the other service never does it twice. With an instance down out of two, the LoadBalancer sends the next attempt to the other one and the user sees nothing. The JDK client under Feign never resends a `POST` by itself either (`sun.net.http.retryPost=false`).
- **Timeouts and logs of the Feign clients:** 2 s to connect and 5 s to read (`FEIGN_CONNECT_TIMEOUT`, `FEIGN_READ_TIMEOUT`, in ms) instead of the Feign defaults of 10 s and 60 s, so a hanging service gives a quick **503**. Each call and its answer is logged at `DEBUG` (method, URL, status, time), never the headers, so never the `SERVICE` token.
- **No distributed transaction:** if the order cannot be saved after the stock was reserved, order-service gives the units back. A crash between the two, or a failure of the `PAID` call after the card was accepted, is not compensated yet (the card is simulated). A saga or an outbox would cover these cases.
- **Errors from the gateway** use the same `ErrorResponse` format: **404** when no route matches, **503** when the target service is not registered in Eureka or does not answer, **504** when it answers after 15 s.
- **Timeouts of the gateway:** 2 s to connect and 15 s for the answer on every route (`GATEWAY_CONNECT_TIMEOUT` in ms, `GATEWAY_RESPONSE_TIMEOUT`), instead of no limit at all. 15 s is longer than two calls between services in a row (5 s each), so the **503** of a service comes first.
- **Health:** each service sends its Actuator status to Eureka. If a service loses PostgreSQL it becomes `DOWN` there and the gateway stops sending it traffic. The gateway does not expose its `gateway` Actuator endpoint (it lists and changes the routes).

To run them without Docker, start PostgreSQL and create the databases once (`psql -U ecommerce -d ecommerce -f infra/postgres/init-databases.sql`), then in this order, each in its own terminal:

```bash
cd services/discovery-server && ./mvnw spring-boot:run   # wait for "Started", dashboard on :8761
cd monolith && ./mvnw spring-boot:run                    # the other /api/** routes of the gateway
cd services/auth-service && ./mvnw spring-boot:run
cd services/product-service && ./mvnw spring-boot:run
cd services/order-service && ./mvnw spring-boot:run
cd services/payment-service && ./mvnw spring-boot:run
cd services/api-gateway && ./mvnw spring-boot:run        # then call http://localhost:8090/api/...
```

The requests in `docs/requests.http` (section *MICROSERVICES*) go through the gateway.

## API endpoints

### Auth

| Method | URL | Description | Codes |
|---|---|---|---|
| POST | /api/auth/register | Create an account (role USER) | 201, 400, 409 |
| POST | /api/auth/login | Log in and get a JWT | 200, 400, 401 |

Request body: `{ "email": "john@example.com", "password": "secret123" }`.
The password must be 8 to 72 characters long. It is stored hashed (BCrypt) and never returned.

Login takes the same body and returns:

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "expiresIn": 3600 }
```

The token is signed with HMAC-SHA (HS256 with a 256-bit key). It holds the email (`sub`) and the role (`role`), and expires after 1 h.
An unknown email and a wrong password return the same 401, so the API does not reveal which accounts exist.

| Variable | Role | Default |
|---|---|---|
| `JWT_SECRET` | Signing key, Base64, 256 bits at least (`openssl rand -base64 32`) | dev key in `application.yml` |
| `JWT_EXPIRATION` | Lifetime of the token (`30m`, `1h`, `1d`...) | `1h` |

> The default key is for local use only. Outside dev, always set `JWT_SECRET`.

To authenticate, send the token in the header of each request:

    Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

A filter (`JwtAuthenticationFilter`) checks the signature and the expiry date, then authenticates the request with the email and role of the token, without a database call.
The API is stateless: no session, no cookie. A missing, invalid or expired token leaves the request anonymous.

### Roles and access

| Endpoints | Access |
|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | Public |
| `GET /api/categories/**`, `GET /api/products/**` | Public |
| `POST`, `PUT`, `DELETE` on `/api/categories/**` and `/api/products/**` | `ADMIN` |
| Swagger UI, `/v3/api-docs` | Public |
| `/api/cart/**`, `/api/orders/**`, `/api/payments/**` and everything else | Logged in (`USER` or `ADMIN`) |

Without a valid token, a protected endpoint returns **401**. With a valid token but the wrong role, it returns **403**.
Both answers use the same JSON format as the other errors (`status`, `error`, `message`, `path`).

Registering always creates a `USER` account. To get an `ADMIN` account, start the application with:

| Variable | Role |
|---|---|
| `ADMIN_EMAIL` | Email of the admin account created at start-up |
| `ADMIN_PASSWORD` | Its password (8 to 72 characters) |

```bash
ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=admin-pass ./mvnw spring-boot:run
```

The account is created only if both variables are set and the email is free. An existing account is never promoted to ADMIN.

In Swagger UI, click **Authorize** and paste the token (without `Bearer `) to call the protected endpoints.

### Categories

> Through the gateway (port 8090), categories and products are served by [product-service](#microservices), with the same URLs, rules, roles and codes as the monolith. Its Swagger UI is on `http://localhost:8082/swagger-ui.html`.

| Method | URL | Description | Codes |
|---|---|---|---|
| GET | /api/categories | List the categories | 200 |
| GET | /api/categories/{id} | One category | 200, 404 |
| POST | /api/categories | Create a category | 201, 400, 409 |
| PUT | /api/categories/{id} | Update a category | 200, 404, 409 |
| DELETE | /api/categories/{id} | Delete a category | 204, 404, 409 |

### Products

| Method | URL | Description | Codes |
|---|---|---|---|
| GET | /api/products | Paged list, with search and filter | 200 |
| GET | /api/products/{id} | One product | 200, 404 |
| POST | /api/products | Create a product | 201, 400, 404 |
| PUT | /api/products/{id} | Update a product | 200, 400, 404 |
| DELETE | /api/products/{id} | Delete a product | 204, 404 |

Body of `POST` and `PUT`:

```json
{ "name": "iPhone 15", "description": "128 GB", "price": 999.99, "categoryId": 1, "stock": 25 }
```

`stock` is the number of units available. It is required and cannot be negative (400 otherwise). It is returned with each product.
Only an ADMIN changes it, with `PUT`. Placing an order lowers it automatically.

#### Parameters of `GET /api/products`

| Parameter | Type | Description | Default |
|---|---|---|---|
| name | string | Partial match on the name (case-insensitive) | - |
| categoryId | number | Filter by category | - |
| page | number | Page number (starts at 0) | 0 |
| size | number | Items per page | 10 |
| sort | string | Sort, for example `price,desc` | id |

Example:

    GET /api/products?name=phone&categoryId=1&page=0&size=5&sort=price,desc

### Cart

> Through the gateway (port 8090), the cart and the orders are served by [order-service](#microservices), with the same URLs, rules and codes as the monolith. Its Swagger UI is on `http://localhost:8083/swagger-ui.html`.

Each logged-in user has a single cart, created on the first addition. The email of the token picks the cart: a user can only see or change their own.

| Method | URL | Description | Codes |
|---|---|---|---|
| GET | /api/cart | My cart (empty if it does not exist yet) | 200, 401 |
| POST | /api/cart/items | Add a product, or raise its quantity if it is already there | 200, 400, 401, 404, 409 |
| PUT | /api/cart/items/{productId} | Change the quantity of a product | 200, 400, 401, 404, 409 |
| DELETE | /api/cart/items/{productId} | Remove a product | 200, 401, 404 |
| DELETE | /api/cart | Empty the cart | 204, 401 |

Body of `POST /api/cart/items`: `{ "productId": 1, "quantity": 2 }`. For `PUT`, only `{ "quantity": 5 }`.
The quantity goes from 1 to 100 per product, without going over the stock of the product (**409** otherwise). Each answer returns the whole cart:

```json
{
  "items": [
    { "productId": 1, "productName": "iPhone 15", "unitPrice": 999.99, "quantity": 2, "subtotal": 1999.98 }
  ],
  "totalQuantity": 2,
  "total": 1999.98
}
```

The cart always shows the current price of the product. If an ADMIN deletes a product, it disappears from the carts.

Through order-service, the answer also holds `productsUpToDate`: usually `true`. If product-service is unavailable, the cart is still shown (**200**) with the last known names and prices and `productsUpToDate: false`; a line whose product was never seen keeps its quantity, without name nor price, and `total` is `null`. Adding a product or changing a quantity then answers **503**: the stock must be current.

### Orders

Placing an order turns the cart of the logged-in user into a `PENDING` order, then empties the cart. A user can only see their own orders.

| Method | URL | Description | Codes |
|---|---|---|---|
| POST | /api/orders | Place an order with the content of my cart (no body) | 201, 400, 401, 409 |
| GET | /api/orders | History of my orders (paged) | 200, 401 |
| GET | /api/orders/{id} | One of my orders | 200, 401, 404 |

`GET /api/orders` only returns my orders, most recent first, 10 per page (`?page=0&size=10`, `sort` can be changed as for products). Each item of `content` has the same format as an order below.

`POST` returns **400** if the cart is empty, **409** if a product does not have enough stock left, and **201** with the header `Location: /api/orders/{id}` otherwise.
The order takes the units out of the stock. The stock is checked again at that moment, since it may have dropped since the product was added to the cart: if a single product is short, nothing changes (neither the stock nor the cart).
Another user's order returns **404**, like an order that does not exist.

```json
{
  "id": 1,
  "status": "PENDING",
  "items": [
    { "productId": 1, "productName": "iPhone 15", "unitPrice": 999.99, "quantity": 2, "subtotal": 1999.98 }
  ],
  "totalQuantity": 2,
  "total": 1999.98,
  "createdAt": "2026-10-27T10:00:00Z"
}
```

The name and price are copied when the order is placed: changing or deleting the product later does not change the order (in the monolith, `productId` becomes `null` if the product is deleted; in order-service, it is kept). A deleted product disappears from the cart and is not ordered.
Two orders placed at the same moment are handled one after the other: the products are locked (`SELECT ... FOR UPDATE`, in product-service for order-service) while the stock is taken. The second one sees the stock left by the first: two customers cannot take the same last unit (**409** for the second). A double click on "Order" creates a single order: the cart is locked, and the second request finds it already empty (**400**). Through order-service, **503** if product-service does not answer: nothing changes.

### Payments

Served by [payment-service](#microservices) through the gateway (Swagger UI on `http://localhost:8084/swagger-ui.html`). Paying one of my `PENDING` orders charges its total; an accepted payment sets it to `PAID`. The payment is **simulated**: no bank is called.

| Method | URL | Description | Codes |
|---|---|---|---|
| POST | /api/payments | Pay one of my orders | 201, 400, 401, 404, 409 |
| GET | /api/payments | History of my payments, declined ones included (paged) | 200, 401 |
| GET | /api/payments/{id} | One of my payments | 200, 401, 404 |

Body of `POST`: `{ "orderId": 1, "cardNumber": "4242424242424242" }` (16 digits). The amount is not sent: it is the total saved with the order.

| Card | Result |
|---|---|
| Ends with `0002` (e.g. `4000000000000002`) | **201**, `status: DECLINED`: the order stays `PENDING`, another attempt is possible |
| Any other card | **201**, `status: SUCCEEDED`: the order becomes `PAID` |

```json
{ "id": 1, "orderId": 1, "amount": 1999.98, "status": "SUCCEEDED", "cardLast4": "4242", "createdAt": "2026-11-03T10:00:00Z" }
```

Only the last 4 digits of the card are kept. An order already `PAID` or `CANCELLED` returns **409**, another user's order **404**.
Payments of the same order go one after the other (PostgreSQL lock `pg_advisory_xact_lock` on the order id): a double click on "Pay" charges only once, the second request finds the order already `PAID` (**409**). **503** if order-service does not answer: nothing is charged.

### Errors

Every error has the same JSON format, whether it comes from a service, from Spring MVC or from Spring Security. The messages themselves are in French, as returned by the API:

```json
{
  "status": 400,
  "error": "Validation failed",
  "message": "Un ou plusieurs champs sont invalides",
  "path": "/api/products",
  "errors": [
    { "field": "price", "message": "Le prix doit être supérieur à 0" }
  ]
}
```

`errors` lists the invalid fields of a `@Valid` body; it is empty for the other errors.

| Code | When |
|---|---|
| 400 | Invalid field, malformed JSON, wrong type in the URL (`/api/products/abc`), missing required parameter, unknown sort (`?sort=foo`), business rule (empty cart...) |
| 401 / 403 | Missing or invalid token / role not allowed |
| 404 | Resource not found or unknown URL |
| 405 | Method not supported (`Allow` header with the possible methods) |
| 409 | Conflict: name already taken, not enough stock, database constraint |
| 415 | `Content-Type` not supported (JSON is expected) |
| 500 | Unexpected error |
| 503 | Called service stopped, not registered in Eureka, failing, too slow, or its circuit breaker open. Except `GET /api/cart` through order-service: **200** with `productsUpToDate: false` |
| 504 | The gateway did not get the answer of the service within 15 s |

Internal details (SQL message, stack trace) are never sent to the client: they go to the application logs.

### Health (Actuator)

| Method | URL | Access | Description | Code |
|---|---|---|---|---|
| GET | `/actuator/health` | Public | Overall status: `{"status":"UP"}`; with an ADMIN token, the details of each component (`db`, `diskSpace`...) | 200 / 503 |
| GET | `/actuator/health/liveness` | Public | The process is running. **Never** checks the database: a PostgreSQL outage must not restart the application | 200 / 503 |
| GET | `/actuator/health/readiness` | Public | The application can serve requests: checks the database | 200 / 503 |
| GET | `/actuator/info` | Public | Name, version and date of the build, Java version | 200 |
| GET | `/actuator/metrics` | ADMIN | Names of the metrics (HTTP requests, JVM, connection pool...) | 200 / 401 / 403 |
| GET | `/actuator/metrics/{name}` | ADMIN | Value of a metric, filtered by tag: `?tag=status:200` | 200 / 401 / 403 / 404 |
| GET | `/actuator/flyway` | ADMIN | Migrations of the database: version, description, state and date each script ran | 200 / 401 / 403 |

Only `health`, `info`, `metrics` and `flyway` are exposed over HTTP: `env`, `beans`, `heapdump`... stay closed (**404**, even for an ADMIN). Any `/actuator/**` URL other than `health` and `info` needs the ADMIN role (**401** without a token, **403** with a USER token).

`/actuator/flyway` exists on the monolith and the four services that own a database. It tells which version of the schema a running instance has, without opening `psql`:

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8083/actuator/flyway \
  | jq '.contexts[].flywayBeans.flyway.migrations[] | {version, description, state}'
```

The Docker image has a `HEALTHCHECK` on the readiness probe: `docker ps` shows `healthy` when the application answers and sees the database, `unhealthy` if PostgreSQL goes down.

#### Metrics

The monolith and the four services publish their metrics with Micrometer, on `/actuator/metrics`, for an ADMIN token. Each metric carries the `application` tag (`order-service`, `ecommerce-monolith`...): once the metrics of every service are gathered in one place, the origin of each one is known.

| Metric | Where | What it measures |
|---|---|---|
| `http.server.requests` | Everywhere | Requests received: count and duration, per URL, method and status |
| `jvm.memory.used`, `jvm.threads.live`, `process.cpu.usage`... | Everywhere | Memory, threads and CPU of the JVM |
| `hikaricp.connections.active`, `hikaricp.connections.pending` | Everywhere | PostgreSQL connections in use, and requests waiting for a free connection |
| `resilience4j.circuitbreaker.state`, `resilience4j.retry.calls` | order-service, payment-service | State of the circuit breaker (`state` tag) and calls retried or not, for each Feign client |
| `orders.placed` | order-service | Orders placed: stock reserved and order saved |
| `cart.fallback` | order-service | Carts shown with the last known products because product-service was unavailable |
| `payments` | payment-service | Payments saved, with the `status` tag (`succeeded` / `declined`) |

The business counters exist from start-up, at 0: a missing counter would mean "not measured", not "nothing happened".

```bash
# With an ADMIN token (POST /api/auth/login):
curl -H "Authorization: Bearer $TOKEN" http://localhost:8083/actuator/metrics/orders.placed
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8084/actuator/metrics/payments?tag=status:declined"
```

The gateway and Eureka check no token: their metrics would be public, so they are not exposed (**404**). The gateway does not route `/actuator/**`: from `:8090`, the metrics of the services stay out of reach.

## Frontend

A small web shop in [frontend/](frontend/), on top of the gateway: React 19 and TypeScript, built by Vite, no UI library. It only calls `/api/**` on its own origin, so the gateway needs no CORS setting.

| Page | Path | Who |
|---|---|---|
| Catalog: search by name, filter by category, sort, pages (kept in the URL) | `/` | everyone |
| Product, add to cart (up to the stock left) | `/products/:id` | everyone, cart once logged in |
| Login, register (then logged in at once) | `/login`, `/register` | visitors |
| Cart: quantities, remove, empty, place the order | `/cart` | logged in |
| Order history, and one order with its payment form while `PENDING` | `/orders`, `/orders/:id` | logged in |
| Payment history, declined attempts included | `/payments` | logged in |
| Categories and products: create, edit, delete | `/admin` | `ADMIN` |

The JWT of auth-service is kept in `localStorage` and sent as `Authorization: Bearer` with each request. The app only reads its claims (email, role, expiry date) to show the right links; the services still check the signature and the role on every request. The user is logged out at the expiry date of the token, or as soon as a request sent with it gets a 401. The messages of the services (`ErrorResponse`) are shown as they come, under the right field for a 400. When product-service is down, the cart says that its prices are the last known ones and the order button waits for it to come back.

Payments are simulated by payment-service: a card number ending in `0002` is declined, any other one of 16 digits is accepted (`4242 4242 4242 4242`). The first `ADMIN` account comes from `ADMIN_EMAIL` and `ADMIN_PASSWORD` in `infra/.env`.

### Run it in development

The stack must be running (at least the gateway on `:8090`, see [Run with Docker](#run-with-docker)). Node.js 22.12 or later:

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

[frontend/Dockerfile](frontend/Dockerfile) builds the app with Node.js, then keeps only the static files and nginx (unprivileged image: never root, port 8080, about 26 MB). [frontend/nginx/default.conf.template](frontend/nginx/default.conf.template) answers `index.html` for every route of the app (a reload on `/orders/12` works), lets the browser keep the hashed assets for a year, and forwards `/api/**` to `API_GATEWAY_URL` (`http://api-gateway:8090` by default), looking up the address of the gateway again every 10 s so that recreating the gateway does not break it. On the server, Caddy sends `/api/**` to the gateway itself and only the pages to this container.

## Database schema

```mermaid
erDiagram
    CATEGORIES ||--o{ PRODUCTS : contains
    USERS ||--o| CARTS : owns
    CARTS ||--o{ CART_ITEMS : contains
    PRODUCTS ||--o{ CART_ITEMS : "added as"
    USERS ||--o{ ORDERS : places
    ORDERS ||--|{ ORDER_ITEMS : contains
    PRODUCTS |o--o{ ORDER_ITEMS : "ordered as"
    ORDERS ||--o{ PAYMENTS : "paid by"
    CATEGORIES {
        bigint id PK
        varchar name UK
        varchar description
    }
    PRODUCTS {
        bigint id PK
        varchar name
        varchar description
        numeric price
        int stock
        bigint category_id FK
        timestamp created_at
    }
    USERS {
        bigint id PK
        varchar email UK
        varchar password
        varchar role
        timestamp created_at
    }
    CARTS {
        bigint id PK
        bigint user_id FK,UK
    }
    CART_ITEMS {
        bigint id PK
        bigint cart_id FK
        bigint product_id FK
        int quantity
    }
    ORDERS {
        bigint id PK
        bigint user_id FK
        varchar status
        numeric total
        timestamp created_at
    }
    ORDER_ITEMS {
        bigint id PK
        bigint order_id FK
        bigint product_id FK "null if the product was deleted"
        varchar product_name
        numeric unit_price
        int quantity
    }
    PAYMENTS {
        bigint id PK
        bigint order_id FK
        numeric amount
        varchar status
        varchar card_last4
        timestamp created_at
    }
```

This is the schema of the monolith (database `ecommerce`). The microservices use the same tables, each in its own database: `users` in `auth_db`, `categories` and `products` in `product_db`, `carts`, `cart_items`, `orders` and `order_items` in `order_db`, `payments` in `payment_db`. There is no foreign key between two databases: `user_id` becomes `user_email`, and `product_id` / `order_id` are plain columns.

An order keeps a copy of the name and price of each product at purchase time: it does not change if the product is updated later.
If an ADMIN deletes a product, `order_items.product_id` becomes `NULL` (`ON DELETE SET NULL`) and the line stays in the history.
`payments` keeps each payment attempt (accepted or declined); it is created by payment-service, which also sets `orders.status` to `PAID`.

### Migrations

The tables are created by [Flyway](https://documentation.red-gate.com/flyway), not by Hibernate. Each application that owns a database has its scripts in `src/main/resources/db/migration`, run in order at startup, before JPA:

| Application | Database | Scripts |
|---|---|---|
| monolith | `ecommerce` | `V1` users, `V2` categories, `V3` products, `V4` carts, `V5` orders, `V6` indexes on the foreign keys, `V7` index on `upper(email)` |
| auth-service | `auth_db` | `V1` users, `V2` index on `upper(email)` |
| product-service | `product_db` | `V1` categories, `V2` products, `V3` index on `products.category_id` |
| order-service | `order_db` | `V1` carts, `V2` orders, `V3` indexes on `orders.user_email` and `order_items.order_id` |
| payment-service | `payment_db` | `V1` payments, `V2` index on `payments.user_email` |

Flyway records each script it ran, with a checksum, in the `flyway_schema_history` table of that database, and never runs it again. Hibernate only checks the result (`ddl-auto: validate`): if an entity has a column that no script created, the application stops at startup instead of failing on the first query.

**Changing the schema:** add a new script with the next version, for example `V8__add_product_image_url.sql` in the monolith, next to the entity change. Never edit a script that is already on `main`: it has run on the server, its checksum would no longer match and the application would refuse to start (the CI [rejects it](#migrations-job)). The integration tests run every script on an empty PostgreSQL and validate the entities against the result, so `./mvnw verify` catches a script that fails or one that was forgotten.

**Databases created before Flyway:** until #114 and #115, Hibernate created the tables (`ddl-auto: update`). The first scripts create exactly the same tables, with the same constraint names. On a database that already has them and no `flyway_schema_history`, Flyway does not fail: it records the existing schema as the last of those scripts (`baseline-version`: 5 for the monolith, 1 for auth-service and payment-service, 2 for product-service and order-service) and only runs the scripts after it, the indexes. `FlywayMigrationIT` in each project loads the DDL that Hibernate used to run, baselines it, and checks that it ends with the same columns, constraints and indexes as a database migrated from `V1`.

To see what ran on a database: `SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;` in `psql`, or [`/actuator/flyway`](#health-actuator) with an ADMIN token.

## Project structure

```
.
├── monolith/        Spring Boot application (Phases 1 to 4), with its Dockerfile
│   └── src/main/java/com/monssef/ecommerce/
│       ├── category/
│       │   ├── entity, repository, service, controller, mapper
│       │   └── dto/       CategoryDTO, CategoryRequest
│       ├── product/
│       │   ├── entity, repository, service, controller, mapper
│       │   └── dto/       ProductDTO, ProductRequest
│       ├── common/
│       │   ├── exceptions/  ResourceNotFoundException, GlobalExceptionHandler, ErrorResponse
│       │   └── shared classes
│       └── test/          Unit, MockMvc and Testcontainers integration tests (support/ holds the IT base)
│   └── src/main/resources/db/migration/   Flyway scripts V1__*.sql, V2__*.sql... (one per schema change)
├── services/        microservices (Phase 5 and later), each a Maven project with its Dockerfile
│                    (and its own db/migration folder for the four that own a database)
│   ├── discovery-server/   Eureka registry
│   ├── auth-service/       register, login, JWT
│   ├── product-service/    categories and products, ADMIN writes checked from the JWT
│   ├── order-service/      cart and orders of the logged-in user, stock reserved in product-service
│   ├── payment-service/    simulated card payments, sets the order to PAID
│   └── api-gateway/        single entry point, routes to the services and the monolith
├── frontend/        React + TypeScript web shop (Vite), with its Dockerfile and nginx config
│   └── src/
│       ├── api/         fetch client (JWT, ErrorResponse → ApiError), one function per endpoint, JSON types
│       ├── auth/        session read from the JWT, kept in localStorage
│       ├── cart/        cart count shown in the header
│       ├── pages/       catalog, product, cart, orders, payments, login, register, admin
│       └── admin/       ADMIN forms for categories and products
├── infra/           Docker Compose file (PostgreSQL; app profile: monolith and microservices), .env.example,
│                    docker-compose.ghcr.yml (same stack from the images published on ghcr.io),
│                    docker-compose.prod.yml and deploy.sh (the server)
│   ├── caddy/       Caddyfile: HTTPS entry point of the server
│   └── postgres/    init-databases.sql: one database per service
├── docs/            architecture overview, architecture decisions (adr/), deployment guide, HTTP request examples
└── .github/         CI and deploy workflows (workflows/), issue and pull request templates
```

The code is organized by feature rather than by layer, which makes the later split into microservices straightforward.

Controllers only ever receive and return DTOs, never entities. Each feature has a MapStruct mapper (`ProductMapper`, `OrderMapper`, ...) that converts between the two; the implementation is generated by Maven at compile time in `target/generated-sources/annotations`. The build fails if a DTO field is not mapped (`unmappedTargetPolicy=ERROR`), so adding a field to a DTO cannot silently return `null`.

## Roadmap

| Phase | Goal | Status |
|---|---|---|
| 1 | Monolith CRUD (products and categories) | Done |
| 2 | Spring Security, JWT, roles (USER / ADMIN) | Done |
| 3 | Cart, orders, stock management | Done |
| 4 | Tests, Docker, Actuator | Done |
| 5 | Split into microservices (auth, product, order, payment, gateway, Eureka) | Done |
| 6 | OpenFeign, Resilience4j, advanced Docker Compose | Done |
| 7 | CI/CD with GitHub Actions | Done |
| 8 | Deployment and frontend (bonus) | In progress |

> **Note:** DTOs, global exception handler, Swagger/OpenAPI and unit tests were delivered as part of Phase 1.

Work is tracked with GitHub issues and a project board. Each issue is delivered through its own branch and pull request.

## License

MIT
