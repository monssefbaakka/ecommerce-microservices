# API reference

Every URL below goes through the gateway (`http://localhost:8090` locally, the public domain on the server).
Each service also serves its own Swagger UI: auth-service on `:8081`, product-service on `:8082`, order-service on
`:8083`, payment-service on `:8084` (`/swagger-ui.html`). Ready-to-run requests: [requests.http](requests.http).

## Auth

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
| `JWT_SECRET` | Signing key, Base64, 256 bits at least (`openssl rand -base64 32`) | none: the service stops at startup without it |
| `JWT_EXPIRATION` | Lifetime of the token (`30m`, `1h`, `1d`...) | `1h` |

> The services have no default key. Only the local Docker Compose file sets one, public on GitHub, so that
> `docker compose up` works as is: set your own `JWT_SECRET` anywhere else (the production Compose file requires it).

To authenticate, send the token in the header of each request:

    Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

A filter (`JwtAuthenticationFilter`) checks the signature and the expiry date, then authenticates the request with the email and role of the token, without a database call.
The API is stateless: no session, no cookie. A missing, invalid or expired token leaves the request anonymous.

## Roles and access

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

## Categories

> Served by product-service. Its Swagger UI is on `http://localhost:8082/swagger-ui.html`.

| Method | URL | Description | Codes |
|---|---|---|---|
| GET | /api/categories | List the categories | 200 |
| GET | /api/categories/{id} | One category | 200, 404 |
| POST | /api/categories | Create a category | 201, 400, 409 |
| PUT | /api/categories/{id} | Update a category | 200, 404, 409 |
| DELETE | /api/categories/{id} | Delete a category | 204, 404, 409 |

## Products

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

### Parameters of `GET /api/products`

| Parameter | Type | Description | Default |
|---|---|---|---|
| name | string | Partial match on the name (case-insensitive) | - |
| categoryId | number | Filter by category | - |
| page | number | Page number (starts at 0) | 0 |
| size | number | Items per page | 10 |
| sort | string | Sort, for example `price,desc` | id |

Example:

    GET /api/products?name=phone&categoryId=1&page=0&size=5&sort=price,desc

## Cart

> Served by order-service. Its Swagger UI is on `http://localhost:8083/swagger-ui.html`.

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

The answer also holds `productsUpToDate`: usually `true`. If product-service is unavailable, the cart is still shown (**200**) with the last known names and prices and `productsUpToDate: false`; a line whose product was never seen keeps its quantity, without name nor price, and `total` is `null`. Adding a product or changing a quantity then answers **503**: the stock must be current.

## Orders

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

The name and price are copied when the order is placed: changing or deleting the product later does not change the order (`productId` is kept). A deleted product disappears from the cart and is not ordered.
Two orders placed at the same moment are handled one after the other: the products are locked (`SELECT ... FOR UPDATE` in product-service) while the stock is taken. The second one sees the stock left by the first: two customers cannot take the same last unit (**409** for the second). A double click on "Order" creates a single order: the cart is locked, and the second request finds it already empty (**400**). **503** if product-service does not answer: nothing changes.

## Payments

Served by payment-service (Swagger UI on `http://localhost:8084/swagger-ui.html`). Paying one of my `PENDING` orders charges its total; an accepted payment sets it to `PAID`. The payment is **simulated**: no bank is called.

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

## Errors

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
| 503 | Called service stopped, not registered in Eureka, failing, too slow, or its circuit breaker open. Except `GET /api/cart`: **200** with `productsUpToDate: false` |
| 504 | The gateway did not get the answer of the service within 15 s |

Internal details (SQL message, stack trace) are never sent to the client: they go to the application logs.

## Health (Actuator)

| Method | URL | Access | Description | Code |
|---|---|---|---|---|
| GET | `/actuator/health` | Public | Overall status: `{"status":"UP"}`; with an ADMIN token, the details of each component (`db`, `diskSpace`...) | 200 / 503 |
| GET | `/actuator/health/liveness` | Public | The process is running. **Never** checks the database: a PostgreSQL outage must not restart the application | 200 / 503 |
| GET | `/actuator/health/readiness` | Public | The application can serve requests: checks the database | 200 / 503 |
| GET | `/actuator/info` | Public | Name, version and date of the build, Java version | 200 |
| GET | `/actuator/metrics` | ADMIN | Names of the metrics (HTTP requests, JVM, connection pool...) | 200 / 401 / 403 |
| GET | `/actuator/metrics/{name}` | ADMIN | Value of a metric, filtered by tag: `?tag=status:200` | 200 / 401 / 403 / 404 |
| GET | `/actuator/flyway` | ADMIN | Migrations of the database: version, description, state and date each script ran | 200 / 401 / 403 |
| GET | `/actuator/prometheus` | Scraper account | Every metric in the Prometheus text format, read by the [Prometheus](observability.md#metrics-prometheus) container with HTTP Basic (`METRICS_USERNAME` / `METRICS_PASSWORD`) | 200 / 401 |

Only `health`, `info`, `metrics`, `flyway` and `prometheus` are exposed over HTTP: `env`, `beans`, `heapdump`... stay closed (**404**, even for an ADMIN). Any `/actuator/**` URL other than `health`, `info` and `prometheus` needs the ADMIN role (**401** without a token, **403** with a USER token). `/actuator/prometheus` is the other way round: only the scraper account opens it, a token never does (**401**, even an ADMIN one), and that account opens nothing else.

`/actuator/flyway` exists on the four services that own a database. It tells which version of the schema a running instance has, without opening `psql`:

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8083/actuator/flyway \
  | jq '.contexts[].flywayBeans.flyway.migrations[] | {version, description, state}'
```

The Docker image has a `HEALTHCHECK` on the readiness probe: `docker ps` shows `healthy` when the application answers and sees the database, `unhealthy` if PostgreSQL goes down.

### Metrics

The four services publish their metrics with Micrometer, on `/actuator/metrics`, for an ADMIN token. Each metric carries the `application` tag (`order-service`, `payment-service`...): once the metrics of every service are gathered in one place, the origin of each one is known.

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

`http.server.requests` (and `http.client.requests`, the Feign calls) only count the traffic of the shop: the `/actuator` requests (health checks of Docker and Eureka, scrapes of Prometheus) and the calls of the Eureka client are left out. Both publish histogram buckets, from which Prometheus computes the latency percentiles of the [Grafana dashboard](observability.md#dashboards-grafana).

```bash
# With an ADMIN token (POST /api/auth/login):
curl -H "Authorization: Bearer $TOKEN" http://localhost:8083/actuator/metrics/orders.placed
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8084/actuator/metrics/payments?tag=status:declined"
```

The gateway and Eureka check no token: their metrics would be public, so they are not exposed (**404**). The gateway does not route `/actuator/**`: from `:8090`, the metrics of the services stay out of reach.
