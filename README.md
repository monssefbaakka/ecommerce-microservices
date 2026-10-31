# E-Commerce Platform

A Spring Boot e-commerce backend, built step by step from a simple monolith to a production-style microservices architecture with Docker and CI/CD.

> **Status:** Phase 1 completed (monolith CRUD with DTOs, validation, exception handler, tests and Swagger). See the [roadmap](#roadmap).

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
| Tests | JUnit 5, Mockito, MockMvc, Testcontainers, JaCoCo |
| Planned | Spring Security + JWT, Spring Cloud (Eureka, Gateway, OpenFeign), Resilience4j, GitHub Actions |

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

The whole stack (PostgreSQL + the application) can also run in containers, without Java on the machine:

```bash
docker compose -f infra/docker-compose.yml --profile app up -d --build
```

The API is then on the same `http://localhost:8080`. Without `--profile app`, Compose only starts PostgreSQL, for `./mvnw spring-boot:run`.

To set the JWT secret or create an ADMIN account, copy `infra/.env.example` to `infra/.env` and fill it in (the file is ignored by git).

The image is built by [monolith/Dockerfile](monolith/Dockerfile) in two stages: the JDK and Maven only exist in the build stage, the final image holds a JRE and the application, split into Spring Boot layers so that a code change only rebuilds the last one. It runs as a non-root user and sizes the heap from the container memory limit (`JAVA_TOOL_OPTIONS`).

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

### Stop

```bash
docker compose -f infra/docker-compose.yml --profile app stop   # --profile app also stops the app container
```

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
| `/api/cart/**`, `/api/orders/**` et tout le reste | Connecté (`USER` ou `ADMIN`) |

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

Le nom et le prix sont copiés au moment de la commande : modifier ou supprimer le produit ensuite ne change pas la commande (`productId` devient `null` si le produit est supprimé).
Deux commandes passées au même moment sont traitées l'une après l'autre : le panier et ses produits sont verrouillés (`SELECT ... FOR UPDATE`) jusqu'à la fin de la commande. La seconde attend, puis voit le stock laissé par la première : deux clients ne peuvent pas prendre la même dernière unité (**409** pour le second). Un double clic sur « Commander » crée une seule commande : la seconde requête trouve le panier déjà vidé (**400**).

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

### Categories

| Method | URL | Description | Status codes |
|---|---|---|---|
| GET | `/api/categories` | List categories | 200 |
| GET | `/api/categories/{id}` | Get one category | 200, 404 |
| POST | `/api/categories` | Create a category | 201, 400, 409 |
| PUT | `/api/categories/{id}` | Update a category | 200, 400, 404, 409 |
| DELETE | `/api/categories/{id}` | Delete a category | 204, 404, 409 |

### Products (in progress)

| Method | URL | Description |
|---|---|---|
| GET | `/api/products` | List products (search and pagination planned) |
| GET | `/api/products/{id}` | Get one product |
| POST | `/api/products` | Create a product |
| PUT | `/api/products/{id}` | Update a product |
| DELETE | `/api/products/{id}` | Delete a product |

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
```

Une commande garde une copie du nom et du prix de chaque produit au moment de l'achat : elle ne change pas si le produit est modifié ensuite.
Si un ADMIN supprime un produit, `order_items.product_id` passe à `NULL` (`ON DELETE SET NULL`) et la ligne reste dans l'historique.

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
├── services/        microservices (Phase 5 and later)
├── infra/           Docker Compose file (PostgreSQL, app profile) and .env.example
└── docs/            HTTP request examples, architecture notes
```

The code is organized by feature rather than by layer, which makes the later split into microservices straightforward.

Controllers only ever receive and return DTOs, never entities. Each feature has a MapStruct mapper (`ProductMapper`, `OrderMapper`, ...) that converts between the two; the implementation is generated by Maven at compile time in `target/generated-sources/annotations`. The build fails if a DTO field is not mapped (`unmappedTargetPolicy=ERROR`), so adding a field to a DTO cannot silently return `null`.

## Roadmap

| Phase | Goal | Status |
|---|---|---|
| 1 | Monolith CRUD (products and categories) | Done |
| 2 | Spring Security, JWT, roles (USER / ADMIN) | In progress |
| 3 | Cart, orders, stock management | Planned |
| 4 | Actuator, Docker improvements | Planned |
| 5 | Split into microservices (auth, product, order, payment, gateway, Eureka) | Planned |
| 6 | OpenFeign, Resilience4j, advanced Docker Compose | Planned |
| 7 | CI/CD with GitHub Actions | Planned |
| 8 | Deployment and frontend (bonus) | Planned |

> **Note:** DTOs, global exception handler, Swagger/OpenAPI and unit tests were delivered as part of Phase 1.

Work is tracked with GitHub issues and a project board. Each issue is delivered through its own branch and pull request.

## License

MIT