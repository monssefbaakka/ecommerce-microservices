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
| Containers | Docker, Docker Compose |
| API docs | OpenAPI 3 / Swagger UI (springdoc) |
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

To try the endpoints, open `docs/requests.http` in VS Code (REST Client extension) or IntelliJ.

### Run tests

```bash
cd monolith
./mvnw test
```

### Stop

```bash
docker compose -f infra/docker-compose.yml stop
```

## API endpoints

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
        bigint category_id FK
        timestamp created_at
    }
```

## Project structure

```
.
├── monolith/        Spring Boot application (Phases 1 to 4)
│   └── src/main/java/com/monssef/ecommerce/
│       ├── category/
│       │   ├── entity, repository, service, controller
│       │   └── dto/       CategoryDTO, CategoryRequest
│       ├── product/
│       │   ├── entity, repository, service, controller
│       │   └── dto/       ProductDTO, ProductRequest
│       ├── common/
│       │   ├── exceptions/  ResourceNotFoundException, GlobalExceptionHandler, ErrorResponse
│       │   └── shared classes
│       └── test/          Controller tests with MockMvc
├── services/        microservices (Phase 5 and later)
├── infra/           Docker Compose files
└── docs/            HTTP request examples, architecture notes
```

The code is organized by feature rather than by layer, which makes the later split into microservices straightforward.

## Roadmap

| Phase | Goal | Status |
|---|---|---|
| 1 | Monolith CRUD (products and categories) | Done |
| 2 | Spring Security, JWT, roles (USER / ADMIN) | Planned |
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