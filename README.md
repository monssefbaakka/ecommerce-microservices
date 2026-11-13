# E-Commerce Platform

[![CI](https://github.com/monssefbaakka/ecommerce-microservices/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/monssefbaakka/ecommerce-microservices/actions/workflows/ci.yml?query=branch%3Amain+event%3Apush)
[![Java](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fmonssefbaakka%2Fecommerce-microservices%2Fmain%2Fmonolith%2Fpom.xml&query=%2F%2F%2A%5Blocal-name%28%29%3D%27java.version%27%5D&label=Java&logo=openjdk&logoColor=white&color=ED8B00)](monolith/pom.xml)
[![Spring Boot](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fmonssefbaakka%2Fecommerce-microservices%2Fmain%2Fmonolith%2Fpom.xml&query=%2F%2A%5Blocal-name%28%29%3D%27project%27%5D%2F%2A%5Blocal-name%28%29%3D%27parent%27%5D%2F%2A%5Blocal-name%28%29%3D%27version%27%5D&label=Spring%20Boot&logo=springboot&logoColor=white&color=6DB33F)](monolith/pom.xml)
[![Spring Cloud](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fmonssefbaakka%2Fecommerce-microservices%2Fmain%2Fservices%2Fapi-gateway%2Fpom.xml&query=%2F%2F%2A%5Blocal-name%28%29%3D%27spring-cloud.version%27%5D&label=Spring%20Cloud&logo=spring&logoColor=white&color=6DB33F)](services/api-gateway/pom.xml)
[![Docker images](https://img.shields.io/badge/images-ghcr.io-2496ED?logo=docker&logoColor=white)](#published-images)
[![License](https://img.shields.io/github/license/monssefbaakka/ecommerce-microservices)](LICENSE)

A Spring Boot e-commerce backend, built step by step from a simple monolith to a production-style microservices architecture with Docker and CI/CD.

> **Status:** Phases 1 to 3 completed (catalog, JWT security, cart and orders). Phase 4 in progress: tests, Docker image and health checks are done. Phase 5 in progress: Eureka, API gateway, auth-service, product-service, order-service and payment-service run next to the monolith, each with its own database. Phase 6 done: the services call each other through OpenFeign clients, behind a circuit breaker and retries, the cart is still shown when product-service is down, and the gateway has timeouts. Each application publishes its metrics (HTTP, JVM, connection pool, circuit breakers, orders, payments) for an ADMIN token. Docker Compose runs the whole stack on two networks (PostgreSQL only reachable by the services that own a database), with health checks, memory limits and log rotation. Phase 7 done: GitHub Actions builds and tests every project, keeps the jars and builds the Docker images on each pull request, then publishes the images to GitHub Container Registry from `main` and release tags. See the [roadmap](#roadmap).

## Tech stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5, Spring Web, Spring Data JPA, Bean Validation |
| Database | PostgreSQL 16 |
| Build | Maven (wrapper included) |
| Mapping | MapStruct (entity ↔ DTO, generated at compile time) |
| Containers | Docker, Docker Compose (health checks, two networks, memory limits, log rotation) |
| API docs | OpenAPI 3 / Swagger UI (springdoc) |
| Security | Spring Security, JWT (jjwt) |
| Microservices | Spring Cloud 2025.0: Netflix Eureka (service registry), Spring Cloud Gateway, LoadBalancer, OpenFeign (calls between services) |
| Monitoring | Spring Boot Actuator (health, liveness / readiness probes, info, metrics), Micrometer (business counters) |
| Tests | JUnit 5, Mockito, MockMvc, Testcontainers, WireMock (fake services for the Feign clients), JaCoCo |
| Resilience | Resilience4j 2.2: circuit breaker and retries around the Feign calls, last known products as fallback for the cart |
| CI | GitHub Actions: build, tests and Docker images of every project on each pull request, images published to GitHub Container Registry (ghcr.io) |
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

The whole stack (PostgreSQL, the monolith and the [microservices](#microservices)) can also run in containers, without Java on the machine:

```bash
docker compose -f infra/docker-compose.yml --profile app up -d --build --wait
```

| Container | URL | Role |
|---|---|---|
| `ecommerce-api-gateway` | `http://localhost:8090` | Single entry point: use this one |
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

One job per Maven project (the monolith and the six services), run in parallel. A failing project does not stop the others, so every broken project shows up in the same run.

| Step | What it does |
|---|---|
| Build, test and package | `./mvnw -B verify`: compile, unit and slice tests, executable jar, integration tests on PostgreSQL with Testcontainers, coverage report |
| No integration test skipped | Fails the job if any `*IT` was skipped. On a laptop without Docker they are skipped on purpose, but in CI a green run must mean they ran |
| Job summary | Number of unit and integration tests, line coverage and jar size, shown on the run page |
| Artifacts | The tested jar (`jar-<project>`) and the JaCoCo report (`coverage-<project>`), kept 7 days. When a job fails, the Surefire / Failsafe reports too (`test-reports-<project>`) |

The Maven dependencies are cached per project, keyed on its `pom.xml`: they are downloaded again only when that file changes. A new push on a pull request cancels the run still in progress for it.

### Docker job

Once every build job has passed, the Docker image of each project is built from its own `Dockerfile`, as `ghcr.io/monssefbaakka/ecommerce-<project>`. An image is never built from code that fails its tests. The image is then checked: it must not run as root and must have a `HEALTHCHECK` (needed by Docker Compose to wait for it). Its user, health check and size go into the job summary.

On `main` and on a release tag, the checked image is then pushed to GitHub Container Registry (see [Published images](#published-images)). On a pull request it is only built and checked: the run never logs in to the registry.

The image layers are cached between runs, one cache per image: when only the code changed, the layer holding the Maven dependencies is reused. The push reuses the same cache, so nothing is compiled twice.

A separate job validates `infra/docker-compose.yml` with every service of the `app` profile (`docker compose config`), so that a broken Compose file fails the pull request rather than the next `docker compose up`. It does the same with `infra/docker-compose.ghcr.yml` merged over it, and fails if any service image is still a local build instead of a `ghcr.io` one. Then with the production override on top ([Deployment](#deployment)): the job fails if any port other than Caddy's is published on every interface, and it also validates the Caddyfile and runs ShellCheck on `infra/deploy.sh`.

### Published images

The seven images are on GitHub Container Registry, under the [packages of the repository](https://github.com/monssefbaakka?tab=packages&repo_name=ecommerce-microservices): `ghcr.io/monssefbaakka/ecommerce-<project>`, with `<project>` one of `monolith`, `discovery-server`, `auth-service`, `product-service`, `order-service`, `payment-service`, `api-gateway`.

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
| [infra/docker-compose.prod.yml](infra/docker-compose.prod.yml) | Merged over `docker-compose.yml` and `docker-compose.ghcr.yml`: no port of the stack published, `JWT_SECRET` and `POSTGRES_PASSWORD` required, Caddy in front of the gateway |
| [infra/caddy/Caddyfile](infra/caddy/Caddyfile) | The only public entry point: HTTPS for `DOMAIN` with a Let's Encrypt certificate renewed by Caddy, everything sent to the gateway |
| [infra/deploy.sh](infra/deploy.sh) | On the server: pulls one image version, waits until every container is healthy, starts the previous version again if one is not |
| [Deploy workflow](.github/workflows/deploy.yml) | After CI passed on `main`: copies `infra/` to the server over SSH and runs `deploy.sh` with the image of the tested commit (`sha-<commit>`, never `latest`), then calls the public URL |

The secrets stay in `infra/.env` on the server; GitHub only holds the SSH key of the deploy user (secret `DEPLOY_SSH_KEY`) and the address of the server (variables `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_KNOWN_HOSTS`, `DEPLOY_URL`). Without `DEPLOY_HOST` the workflow is skipped. A roll back is a manual run of the workflow (*Actions* → *Deploy* → *Run workflow*) with an older tag.

## Microservices

Phase 5 extracts the monolith step by step. The first services are in [services/](services/):

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

| Méthode | URL | Description | Codes |
|---|---|---|---|
| POST | /api/auth/register | Créer un compte (rôle USER) | 201, 400, 409 |
| POST | /api/auth/login | Se connecter et obtenir un token JWT | 200, 400, 401 |

Corps de la requête : `{ "email": "john@example.com", "password": "secret123" }`.
Le mot de passe doit contenir entre 8 et 72 caractères. Il est stocké hashé (BCrypt) et n'est jamais renvoyé.

Le login prend le même corps et renvoie :

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "expiresIn": 3600 }
```

Le token est signé en HMAC-SHA (HS256 avec une clé de 256 bits). Il contient l'email (`sub`), le rôle (`role`) et expire après 1 h.
Un email inconnu et un mauvais mot de passe renvoient le même 401, pour ne pas révéler quels comptes existent.

| Variable | Rôle | Défaut |
|---|---|---|
| `JWT_SECRET` | Clé de signature, en Base64, 256 bits minimum (`openssl rand -base64 32`) | clé de dev dans `application.yml` |
| `JWT_EXPIRATION` | Durée de vie du token (`30m`, `1h`, `1d`...) | `1h` |

> La clé par défaut sert uniquement en local. Hors dev, définis toujours `JWT_SECRET`.

Pour s'authentifier, envoie le token dans l'en-tête de chaque requête :

    Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

Un filtre (`JwtAuthenticationFilter`) vérifie la signature et l'expiration, puis authentifie la requête avec l'email et le rôle du token, sans accès à la base.
L'API est stateless : pas de session ni de cookie. Un token absent, invalide ou expiré laisse la requête anonyme.

### Rôles et accès

| Endpoints | Accès |
|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | Public |
| `GET /api/categories/**`, `GET /api/products/**` | Public |
| `POST`, `PUT`, `DELETE` sur `/api/categories/**` et `/api/products/**` | `ADMIN` |
| Swagger UI, `/v3/api-docs` | Public |
| `/api/cart/**`, `/api/orders/**`, `/api/payments/**` et tout le reste | Connecté (`USER` ou `ADMIN`) |

Sans token valide, un endpoint protégé renvoie **401**. Avec un token valide mais le mauvais rôle, il renvoie **403**.
Les deux réponses ont le même format JSON que les autres erreurs (`status`, `error`, `message`, `path`).

L'inscription crée toujours un compte `USER`. Pour avoir un compte `ADMIN`, lance l'application avec :

| Variable | Rôle |
|---|---|
| `ADMIN_EMAIL` | Email du compte admin créé au démarrage |
| `ADMIN_PASSWORD` | Son mot de passe (8 à 72 caractères) |

```bash
ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=admin-pass ./mvnw spring-boot:run
```

Le compte est créé seulement si les deux variables sont définies et que l'email est libre. Un compte existant n'est jamais promu ADMIN.

Dans Swagger UI, clique sur **Authorize** et colle le token (sans `Bearer `) pour appeler les endpoints protégés.

### Categories

> Via la gateway (port 8090), les catégories et les produits sont servis par [product-service](#microservices), avec les mêmes URLs, règles, rôles et codes que le monolithe. Son Swagger UI est sur `http://localhost:8082/swagger-ui.html`.

| Méthode | URL | Description | Codes |
|---|---|---|---|
| GET | /api/categories | Liste des catégories | 200 |
| GET | /api/categories/{id} | Détail d'une catégorie | 200, 404 |
| POST | /api/categories | Créer une catégorie | 201, 400, 409 |
| PUT | /api/categories/{id} | Modifier une catégorie | 200, 404, 409 |
| DELETE | /api/categories/{id} | Supprimer une catégorie | 204, 404, 409 |

### Products

| Méthode | URL | Description | Codes |
|---|---|---|---|
| GET | /api/products | Liste paginée, avec recherche et filtre | 200 |
| GET | /api/products/{id} | Détail d'un produit | 200, 404 |
| POST | /api/products | Créer un produit | 201, 400, 404 |
| PUT | /api/products/{id} | Modifier un produit | 200, 400, 404 |
| DELETE | /api/products/{id} | Supprimer un produit | 204, 404 |

Corps de `POST` et `PUT` :

```json
{ "name": "iPhone 15", "description": "128 GB", "price": 999.99, "categoryId": 1, "stock": 25 }
```

`stock` est le nombre d'unités disponibles. Il est obligatoire et ne peut pas être négatif (400 sinon). Il est renvoyé dans chaque produit.
Seul un ADMIN le modifie, avec `PUT`. Passer commande le diminue automatiquement.

#### Paramètres de `GET /api/products`

| Paramètre | Type | Description | Défaut |
|---|---|---|---|
| name | string | Recherche partielle sur le nom (insensible à la casse) | - |
| categoryId | number | Filtre par catégorie | - |
| page | number | Numéro de page (commence à 0) | 0 |
| size | number | Nombre d'éléments par page | 10 |
| sort | string | Tri, par exemple `price,desc` | id |

Exemple :

    GET /api/products?name=phone&categoryId=1&page=0&size=5&sort=price,desc

### Cart

> Via la gateway (port 8090), le panier et les commandes sont servis par [order-service](#microservices), avec les mêmes URLs, règles et codes que le monolithe. Son Swagger UI est sur `http://localhost:8083/swagger-ui.html`.

Chaque utilisateur connecté a un seul panier, créé au premier ajout. L'email du token identifie le panier : on ne peut voir ou modifier que le sien.

| Méthode | URL | Description | Codes |
|---|---|---|---|
| GET | /api/cart | Voir mon panier (vide s'il n'existe pas encore) | 200, 401 |
| POST | /api/cart/items | Ajouter un produit, ou augmenter sa quantité s'il y est déjà | 200, 400, 401, 404, 409 |
| PUT | /api/cart/items/{productId} | Changer la quantité d'un produit | 200, 400, 401, 404, 409 |
| DELETE | /api/cart/items/{productId} | Retirer un produit | 200, 401, 404 |
| DELETE | /api/cart | Vider le panier | 204, 401 |

Corps de `POST /api/cart/items` : `{ "productId": 1, "quantity": 2 }`. Pour `PUT`, seulement `{ "quantity": 5 }`.
La quantité va de 1 à 100 par produit, sans dépasser le stock du produit (**409** sinon). Chaque réponse renvoie le panier complet :

```json
{
  "items": [
    { "productId": 1, "productName": "iPhone 15", "unitPrice": 999.99, "quantity": 2, "subtotal": 1999.98 }
  ],
  "totalQuantity": 2,
  "total": 1999.98
}
```

Le panier affiche toujours le prix actuel du produit. Si un ADMIN supprime un produit, il disparaît des paniers.

Via order-service, la réponse contient aussi `productsUpToDate` : `true` d'habitude. Si product-service est indisponible, le panier s'affiche quand même (**200**) avec les derniers noms et prix connus et `productsUpToDate: false` ; une ligne dont le produit n'a jamais été vu garde sa quantité, sans nom ni prix, et `total` vaut `null`. Ajouter un produit ou changer une quantité répond alors **503** : le stock doit être à jour.

### Orders

Passer commande transforme le panier de l'utilisateur connecté en commande `PENDING`, puis vide le panier. On ne peut voir que ses propres commandes.

| Méthode | URL | Description | Codes |
|---|---|---|---|
| POST | /api/orders | Passer commande avec le contenu de mon panier (sans corps) | 201, 400, 401, 409 |
| GET | /api/orders | Historique de mes commandes (paginé) | 200, 401 |
| GET | /api/orders/{id} | Voir une de mes commandes | 200, 401, 404 |

`GET /api/orders` renvoie uniquement mes commandes, les plus récentes d'abord, 10 par page (`?page=0&size=10`, `sort` modifiable comme pour les produits). Chaque élément de `content` a le même format qu'une commande ci-dessous.

`POST` renvoie **400** si le panier est vide, **409** si un produit n'a plus assez de stock, et **201** avec l'en-tête `Location: /api/orders/{id}` sinon.
La commande retire les unités du stock. Le stock est vérifié à nouveau à ce moment, car il a pu baisser depuis l'ajout au panier : si un seul produit manque, rien n'est modifié (ni stock, ni panier).
Une commande d'un autre utilisateur renvoie **404**, comme une commande qui n'existe pas.

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

Le nom et le prix sont copiés au moment de la commande : modifier ou supprimer le produit ensuite ne change pas la commande (dans le monolithe, `productId` devient `null` si le produit est supprimé ; dans order-service, il est gardé). Un produit supprimé disparaît du panier et n'est pas commandé.
Deux commandes passées au même moment sont traitées l'une après l'autre : les produits sont verrouillés (`SELECT ... FOR UPDATE`, dans product-service pour order-service) le temps de retirer le stock. La seconde voit le stock laissé par la première : deux clients ne peuvent pas prendre la même dernière unité (**409** pour le second). Un double clic sur « Commander » crée une seule commande : le panier est verrouillé, la seconde requête le trouve déjà vidé (**400**). Via order-service, **503** si product-service ne répond pas : rien n'est modifié.

### Payments

Servis par [payment-service](#microservices) via la gateway (Swagger UI sur `http://localhost:8084/swagger-ui.html`). Payer une de mes commandes `PENDING` débite son total ; un paiement accepté la passe `PAID`. Le paiement est **simulé** : aucune banque n'est appelée.

| Méthode | URL | Description | Codes |
|---|---|---|---|
| POST | /api/payments | Payer une de mes commandes | 201, 400, 401, 404, 409 |
| GET | /api/payments | Historique de mes paiements, refusés compris (paginé) | 200, 401 |
| GET | /api/payments/{id} | Voir un de mes paiements | 200, 401, 404 |

Corps de `POST` : `{ "orderId": 1, "cardNumber": "4242424242424242" }` (16 chiffres). Le montant n'est pas envoyé : c'est le total enregistré avec la commande.

| Carte | Résultat |
|---|---|
| Se termine par `0002` (ex. `4000000000000002`) | **201**, `status: DECLINED` : la commande reste `PENDING`, un autre essai est possible |
| Toute autre carte | **201**, `status: SUCCEEDED` : la commande passe `PAID` |

```json
{ "id": 1, "orderId": 1, "amount": 1999.98, "status": "SUCCEEDED", "cardLast4": "4242", "createdAt": "2026-11-03T10:00:00Z" }
```

Seuls les 4 derniers chiffres de la carte sont gardés. Une commande déjà `PAID` ou `CANCELLED` renvoie **409**, une commande d'un autre utilisateur **404**.
Les paiements d'une même commande passent l'un après l'autre (verrou PostgreSQL `pg_advisory_xact_lock` sur l'id de la commande) : un double clic sur « Payer » ne débite qu'une fois, la seconde requête trouve la commande déjà `PAID` (**409**). **503** si order-service ne répond pas : rien n'est débité.

### Erreurs

Toutes les erreurs ont le même format JSON, qu'elles viennent d'un service, de Spring MVC ou de Spring Security :

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

`errors` détaille les champs invalides d'un `@Valid` ; il est vide pour les autres erreurs.

| Code | Cas |
|---|---|
| 400 | Champ invalide, JSON malformé, mauvais type dans l'URL (`/api/products/abc`), paramètre obligatoire manquant, tri inconnu (`?sort=foo`), règle métier (panier vide...) |
| 401 / 403 | Token absent ou invalide / rôle insuffisant |
| 404 | Ressource introuvable ou URL inconnue |
| 405 | Méthode non supportée (en-tête `Allow` avec les méthodes possibles) |
| 409 | Conflit : nom déjà pris, stock insuffisant, contrainte de base de données |
| 415 | `Content-Type` non supporté (le JSON est attendu) |
| 500 | Erreur inattendue |
| 503 | Service appelé arrêté, non enregistré dans Eureka, en erreur, trop lent, ou circuit breaker ouvert. Sauf `GET /api/cart` via order-service : **200** avec `productsUpToDate: false` |
| 504 | La gateway n'a pas reçu la réponse du service en 15 s |

Les détails internes (message SQL, stack trace) ne sont jamais renvoyés au client : ils sont écrits dans les logs de l'application.

### Santé (Actuator)

| Méthode | URL | Accès | Description | Code |
|---|---|---|---|---|
| GET | `/actuator/health` | Public | État global : `{"status":"UP"}` ; avec un token ADMIN, détail de chaque composant (`db`, `diskSpace`...) | 200 / 503 |
| GET | `/actuator/health/liveness` | Public | Le processus fonctionne. Ne vérifie **jamais** la base : une panne de PostgreSQL ne doit pas faire redémarrer l'application | 200 / 503 |
| GET | `/actuator/health/readiness` | Public | L'application peut servir des requêtes : vérifie la base | 200 / 503 |
| GET | `/actuator/info` | Public | Nom, version et date du build, version de Java | 200 |
| GET | `/actuator/metrics` | ADMIN | Noms des métriques (requêtes HTTP, JVM, pool de connexions...) | 200 / 401 / 403 |
| GET | `/actuator/metrics/{nom}` | ADMIN | Valeur d'une métrique, filtrable par tag : `?tag=status:200` | 200 / 401 / 403 / 404 |

Seuls `health`, `info` et `metrics` sont exposés en HTTP : `env`, `beans`, `heapdump`... restent fermés (**404**, même pour un ADMIN). Toute autre URL `/actuator/**` que `health` et `info` demande le rôle ADMIN (**401** sans token, **403** avec un token USER).

L'image Docker a un `HEALTHCHECK` sur la readiness : `docker ps` affiche `healthy` quand l'application répond et voit la base, `unhealthy` si PostgreSQL tombe.

#### Métriques

Le monolithe et les quatre services publient leurs métriques avec Micrometer, sur `/actuator/metrics`, pour un token ADMIN. Chaque métrique porte le tag `application` (`order-service`, `ecommerce-monolith`...) : une fois les métriques de tous les services réunies au même endroit, on sait d'où vient chacune.

| Métrique | Où | Ce qu'elle mesure |
|---|---|---|
| `http.server.requests` | Partout | Requêtes reçues : nombre, durée, par URL, méthode et code de réponse |
| `jvm.memory.used`, `jvm.threads.live`, `process.cpu.usage`... | Partout | Mémoire, threads et CPU de la JVM |
| `hikaricp.connections.active`, `hikaricp.connections.pending` | Partout | Connexions à PostgreSQL utilisées, et requêtes qui attendent une connexion libre |
| `resilience4j.circuitbreaker.state`, `resilience4j.retry.calls` | order-service, payment-service | État du circuit breaker (tag `state`) et appels réessayés ou non, pour chaque client Feign |
| `orders.placed` | order-service | Commandes passées : stock réservé et commande enregistrée |
| `cart.fallback` | order-service | Paniers affichés avec les derniers produits connus parce que product-service était indisponible |
| `payments` | payment-service | Paiements enregistrés, avec le tag `status` (`succeeded` / `declined`) |

Les compteurs métier existent dès le démarrage, à 0 : un compteur absent voudrait dire « pas mesuré », pas « rien ne s'est passé ».

```bash
# Avec un token ADMIN (POST /api/auth/login) :
curl -H "Authorization: Bearer $TOKEN" http://localhost:8083/actuator/metrics/orders.placed
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8084/actuator/metrics/payments?tag=status:declined"
```

La gateway et Eureka ne vérifient aucun token : leurs métriques seraient publiques, elles ne sont donc pas exposées (**404**). La gateway ne route pas `/actuator/**` : depuis `:8090`, les métriques des services restent inaccessibles.

### Categories
| Méthode | URL | Description | Code |
|---|---|---|---|
| GET | /api/categories | Liste des catégories | 200 |
| GET | /api/categories/{id} | Détail d'une catégorie | 200 / 404 |
| POST | /api/categories | Créer | 201 / 409 |
| PUT | /api/categories/{id} | Modifier | 200 / 404 |
| DELETE | /api/categories/{id} | Supprimer | 204 / 409 |

### Products
| Méthode | URL | Description | Code |
|---|---|---|---|
| GET | /api/products | Liste des produits | 200 |
| GET | /api/products/{id} | Détail d'un produit | 200 / 404 |
| POST | /api/products | Créer | 201 / 400 / 404 |
| PUT | /api/products/{id} | Modifier | 200 / 400 / 404 |
| DELETE | /api/products/{id} | Supprimer | 204 / 404 |

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

Ce schéma est celui du monolithe (base `ecommerce`). Les microservices reprennent ces tables, chacun dans sa base : `users` dans `auth_db`, `categories` et `products` dans `product_db`, `carts`, `cart_items`, `orders` et `order_items` dans `order_db`, `payments` dans `payment_db`. Entre deux bases il n'y a pas de clé étrangère : `user_id` devient `user_email`, et `product_id` / `order_id` sont de simples colonnes.

Une commande garde une copie du nom et du prix de chaque produit au moment de l'achat : elle ne change pas si le produit est modifié ensuite.
Si un ADMIN supprime un produit, `order_items.product_id` passe à `NULL` (`ON DELETE SET NULL`) et la ligne reste dans l'historique.
`payments` garde chaque tentative de paiement (acceptée ou refusée) ; elle est créée par payment-service, qui passe aussi `orders.status` à `PAID`.

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
├── services/        microservices (Phase 5 and later), each a Maven project with its Dockerfile
│   ├── discovery-server/   Eureka registry
│   ├── auth-service/       register, login, JWT
│   ├── product-service/    categories and products, ADMIN writes checked from the JWT
│   ├── order-service/      cart and orders of the logged-in user, stock reserved in product-service
│   ├── payment-service/    simulated card payments, sets the order to PAID
│   └── api-gateway/        single entry point, routes to the services and the monolith
├── infra/           Docker Compose file (PostgreSQL; app profile: monolith and microservices), .env.example,
│                    docker-compose.ghcr.yml (same stack from the images published on ghcr.io)
│   └── postgres/    init-databases.sql: one database per service
├── docs/            HTTP request examples, architecture notes
└── .github/         CI workflow (workflows/ci.yml), issue and pull request templates
```

The code is organized by feature rather than by layer, which makes the later split into microservices straightforward.

Controllers only ever receive and return DTOs, never entities. Each feature has a MapStruct mapper (`ProductMapper`, `OrderMapper`, ...) that converts between the two; the implementation is generated by Maven at compile time in `target/generated-sources/annotations`. The build fails if a DTO field is not mapped (`unmappedTargetPolicy=ERROR`), so adding a field to a DTO cannot silently return `null`.

## Roadmap

| Phase | Goal | Status |
|---|---|---|
| 1 | Monolith CRUD (products and categories) | Done |
| 2 | Spring Security, JWT, roles (USER / ADMIN) | Done |
| 3 | Cart, orders, stock management | Done |
| 4 | Tests, Docker, Actuator | In progress |
| 5 | Split into microservices (auth, product, order, payment, gateway, Eureka) | In progress |
| 6 | OpenFeign, Resilience4j, advanced Docker Compose | Done |
| 7 | CI/CD with GitHub Actions | Done |
| 8 | Deployment and frontend (bonus) | Planned |

> **Note:** DTOs, global exception handler, Swagger/OpenAPI and unit tests were delivered as part of Phase 1.

Work is tracked with GitHub issues and a project board. Each issue is delivered through its own branch and pull request.

## License

MIT