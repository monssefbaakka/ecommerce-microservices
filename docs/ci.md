# Continuous integration

Every pull request and every push to `main` runs the [CI workflow](../.github/workflows/ci.yml) on GitHub Actions. Nothing to set up: the runners already have Java and Docker.

The **CI** badge at the top of the README shows the last run on `main` (pushes only: a failing pull request does not turn it red) and links to its runs. The Java, Spring Boot and Spring Cloud badges read their version from the `pom.xml` of `main` (order-service's, and the gateway's for Spring Cloud), so they follow an upgrade without editing this file.

## Build job

One job per Maven project (the six services), run in parallel. The migrations and the frontend have a job of their own ([below](#migrations-job)). A failing project does not stop the others, so every broken project shows up in the same run.

| Step | What it does |
|---|---|
| No default JWT key | Fails if an `application.yml` gives `JWT_SECRET` a default: that key would be public on GitHub, and a deploy that forgets the variable would accept tokens anyone can sign |
| Build, test and package | `./mvnw -B verify`: compile, unit and slice tests, executable jar, integration tests on PostgreSQL with Testcontainers, coverage report. Each integration test starts the application on an empty database: the Flyway migrations run, then Hibernate validates the tables against the entities, so a broken migration or a forgotten one fails the build |
| No integration test skipped | Fails the job if any `*IT` was skipped. On a laptop without Docker they are skipped on purpose, but in CI a green run must mean they ran |
| Job summary | Number of unit and integration tests, line coverage and jar size, shown on the run page |
| Artifacts | The tested jar (`jar-<project>`) and the JaCoCo report (`coverage-<project>`), kept 7 days. When a job fails, the Surefire / Failsafe reports too (`test-reports-<project>`) |

The Maven dependencies are cached per project, keyed on its `pom.xml`: they are downloaded again only when that file changes. A new push on a pull request cancels the run still in progress for it.

## Migrations job

On a pull request only. Fails if the branch edits, renames or deletes a Flyway script that is already on `main` (`*/db/migration/V*.sql`): that script has run on the server, Flyway stored its checksum, and the next deploy would refuse to start. A schema change is always a new script ([Database schema](database.md)).

## Frontend job

`npm ci` (exactly the versions of `package-lock.json`), the Vitest unit tests, then `npm run build`: the strict TypeScript check of the whole app and the Vite build. The size of each built file goes into the job summary and the `dist/` folder is kept as the `frontend-dist` artifact (7 days). The npm cache is keyed on `package-lock.json`.

## Docker job

Once every build job and the frontend job have passed, the Docker image of each project is built from its own `Dockerfile`, as `ghcr.io/monssefbaakka/ecommerce-<project>`. An image is never built from code that fails its tests. The image is then checked: it must not run as root and must have a `HEALTHCHECK` (needed by Docker Compose to wait for it). Its user, health check and size go into the job summary.

On `main` and on a release tag, the checked image is then pushed to GitHub Container Registry (see [Published images](#published-images)). On a pull request it is only built and checked: the run never logs in to the registry.

The image layers are cached between runs, one cache per image: when only the code changed, the layer holding the Maven dependencies is reused. The push reuses the same cache, so nothing is compiled twice.

A separate job validates `infra/docker-compose.yml` with every service of the `app` profile (`docker compose config`), so that a broken Compose file fails the pull request rather than the next `docker compose up`. It does the same with `infra/docker-compose.ghcr.yml` merged over it, and fails if any service image is still a local build instead of a `ghcr.io` one. Then with the production override on top ([Deployment](deployment.md)): the job fails if any port other than Caddy's is published on every interface (Grafana, Prometheus and Zipkin included), and it also validates the Caddyfile and runs ShellCheck on `infra/deploy.sh`. The Prometheus configuration is checked by `promtool` from the Prometheus image of the Compose file, and every Grafana dashboard must be valid JSON whose panels only query a data source of `datasources.yml`.

## Published images

The seven images are on GitHub Container Registry, under the [packages of the repository](https://github.com/monssefbaakka?tab=packages&repo_name=ecommerce-microservices): `ghcr.io/monssefbaakka/ecommerce-<project>`, with `<project>` one of `discovery-server`, `auth-service`, `product-service`, `order-service`, `payment-service`, `api-gateway`, `frontend`.

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

The run token (`GITHUB_TOKEN`) pushes the images: no personal token or secret to create, and only the Docker job may write packages. Each image carries the OCI labels linking it to this repository and commit, an SBOM (every package and library inside it) and its build provenance (`docker buildx imagetools inspect ghcr.io/monssefbaakka/ecommerce-order-service:latest`). The tags and the digest published by a run are listed in its job summary.

A package created by its first push is **private**, even for a public repository. To let anyone pull without logging in, open each package once, then *Package settings* → *Change visibility* → *Public*. While a package is private, pulling it needs `docker login ghcr.io` with a personal access token that has the `read:packages` scope.
