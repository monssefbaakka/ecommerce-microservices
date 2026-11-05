# E-Commerce Platform

A Spring Boot e-commerce backend, built step by step from a simple monolith to a production-style microservices architecture with Docker and CI/CD.

> **Status:** Phases 1 to 3 completed (catalog, JWT security, cart and orders). Phase 4 in progress: tests, Docker image and health checks are done. Phase 5 in progress: Eureka, API gateway, auth-service, product-service, order-service and payment-service run next to the monolith, each with its own database. Phase 6 started: the services call each other through OpenFeign clients. See the [roadmap](#roadmap).

## Tech stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5, Spring Web, Spring Data JPA, Bean Validation |
| Database | PostgreSQL 16 |
| Build | Maven (wrapper included) |
| Mapping | MapStruct (entity ↔ DTO, generated at compile time) |
| Containers | Docker, Docker Compose |
| API docs | OpenAPI 3 / Swagger UI (springdoc) |
| Security | Spring Security, JWT (jjwt) |
| Microservices | Spring Cloud 2025.0: Netflix Eureka (service registry), Spring Cloud Gateway, LoadBalancer, OpenFeign (calls between services) |
| Monitoring | Spring Boot Actuator (health, liveness / readiness probes, info) |
| Tests | JUnit 5, Mockito, MockMvc, Testcontainers, JaCoCo |
| Planned | Resilience4j, GitHub Actions |

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
docker compose -f infra/docker-compose.yml --profile app up -d --build
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

`docker ps` shows each container as `healthy` once it answers on `/actuator/health/readiness`. The services start last (about 1 min): they wait for Eureka and PostgreSQL. Each one has its own database (`auth_db`, `product_db`, `order_db`, `payment_db`, next to the monolith's `ecommerce`), created by `infra/postgres/init-databases.sql` the first time the volume starts. With a volume created before, run `docker compose -f infra/docker-compose.yml down -v` once: it deletes the data and the databases are created on the next `up`. Without `--profile app`, Compose only starts PostgreSQL, for `./mvnw spring-boot:run`.

To set the JWT secret or create an ADMIN account, copy `infra/.env.example` to `infra/.env` and fill it in (the file is ignored by git). The monolith and every service read it, so they share the same secret.

Each service has its own Dockerfile, built the same way. The monolith's image is built by [monolith/Dockerfile](monolith/Dockerfile) in two stages: the JDK and Maven only exist in the build stage, the final image holds a JRE and the application, split into Spring Boot layers so that a code change only rebuilds the last one. It runs as a non-root user and sizes the heap from the container memory limit (`JAVA_TOOL_OPTIONS`).

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
| discovery-server | The registry starts, accepts registrations, serves the dashboard; only `health` and `info` are exposed |
| auth-service | JWT, register / login rules, `ErrorResponse` bodies, security chain with MockMvc; `*IT` on PostgreSQL with Testcontainers (register then login, Actuator, OpenAPI) |
| product-service | JWT check, category and product rules, paging and search, `ErrorResponse` bodies, security chain (401 / 403) with MockMvc; `*IT` on PostgreSQL with Testcontainers (catalog flow, Actuator, OpenAPI) |
| product-service (stock) | Reservations all or nothing, release, `/internal/**` only for the `SERVICE` role; `StockIT` on PostgreSQL (rollback, last unit under concurrency) |
| order-service | JWT check, cart and order rules (stock, per-line maximum, stock given back if the order cannot be saved), product-service client against a fake HTTP server (404 / 409 passed on, 503), `/internal/orders` for the `SERVICE` role only, mappers, `ErrorResponse` bodies; `*IT` on PostgreSQL with Testcontainers and an in-memory product-service (cart and order flows, concurrent orders, deleted products, Actuator, OpenAPI) |
| payment-service | JWT check, simulated card, payment rules (`PAID` set in order-service on success, declined attempts kept, owner checked, 404 / 409 / 503), order-service client against a fake HTTP server, `ErrorResponse` bodies; `*IT` on PostgreSQL with Testcontainers and an in-memory order-service (payment flow, double payment stopped by the advisory lock, Actuator, OpenAPI) |
| api-gateway | Routing to every service and the monolith against stub services, 404 without route, 503 when a service is not registered or not answering, Actuator |

### Stop

```bash
docker compose -f infra/docker-compose.yml --profile app stop   # --profile app also stops the app and service containers
```

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
    order -- "/internal/products<br/>/internal/stock/**" --> product
    payment -- "/internal/orders/**" --> order
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
- **Calls between services:** what used to be a join is now an HTTP call, with a `@LoadBalanced` `RestClient` that finds the other service by name in Eureka (OpenFeign replaces it in #48). order-service reads names, prices and stock from product-service and reserves the stock when an order is placed (all lines or none, **409** if one is short); payment-service reads the owner, status and total of an order and sets it `PAID` in order-service. These endpoints are under `/internal/**`: the gateway does not route them, and they only accept a short-lived token with the `SERVICE` role, which the calling service signs itself with the shared JWT key (no account ever gets this role). A **404** or **409** of the other service is passed on with its message; if it is down or not registered, the answer is **503**.
- **No distributed transaction:** if the order cannot be saved after the stock was reserved, order-service gives the units back. A crash between the two, or a failure of the `PAID` call after the card was accepted, is not compensated yet (the card is simulated). A saga or an outbox would cover these cases.
- **Errors from the gateway** use the same `ErrorResponse` format: **404** when no route matches, **503** when the target service is not registered in Eureka or does not answer.
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

Les détails internes (message SQL, stack trace) ne sont jamais renvoyés au client : ils sont écrits dans les logs de l'application.

### Santé (Actuator)

| Méthode | URL | Accès | Description | Code |
|---|---|---|---|---|
| GET | `/actuator/health` | Public | État global : `{"status":"UP"}` ; avec un token ADMIN, détail de chaque composant (`db`, `diskSpace`...) | 200 / 503 |
| GET | `/actuator/health/liveness` | Public | Le processus fonctionne. Ne vérifie **jamais** la base : une panne de PostgreSQL ne doit pas faire redémarrer l'application | 200 / 503 |
| GET | `/actuator/health/readiness` | Public | L'application peut servir des requêtes : vérifie la base | 200 / 503 |
| GET | `/actuator/info` | Public | Nom, version et date du build, version de Java | 200 |

Seuls `health` et `info` sont exposés en HTTP : `env`, `beans`, `heapdump`... restent fermés (**404**, même pour un ADMIN). Toute autre URL `/actuator/**` demande de toute façon le rôle ADMIN (**401** / **403**).

L'image Docker a un `HEALTHCHECK` sur la readiness : `docker ps` affiche `healthy` quand l'application répond et voit la base, `unhealthy` si PostgreSQL tombe.

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
├── infra/           Docker Compose file (PostgreSQL; app profile: monolith and microservices), .env.example
│   └── postgres/    init-databases.sql: one database per service
└── docs/            HTTP request examples, architecture notes
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
| 6 | OpenFeign, Resilience4j, advanced Docker Compose | Planned |
| 7 | CI/CD with GitHub Actions | Planned |
| 8 | Deployment and frontend (bonus) | Planned |

> **Note:** DTOs, global exception handler, Swagger/OpenAPI and unit tests were delivered as part of Phase 1.

Work is tracked with GitHub issues and a project board. Each issue is delivered through its own branch and pull request.

## License

MIT