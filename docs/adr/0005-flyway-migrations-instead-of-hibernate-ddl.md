# ADR 0005: Flyway migrations instead of Hibernate DDL

- **Status:** accepted
- **Date:** 2026-11-20
- **Issue:** #114, #115, #116

## Context

Until now Hibernate created and updated the tables of the monolith and of the four services that own a
database (`ddl-auto: update`). That was enough while the schema only grew, but since #59 the stack runs on a
server with real data, and `update`:

- only adds: it never drops, renames or changes the type of a column, and never fills a new `NOT NULL` column,
  so any other change has to be done by hand in `psql`, on every database, with no record of it;
- names the constraints and indexes with hashes (`uk6dotkott2kjsp8vw4d0m25fb7`), and creates no index on the
  foreign keys or the columns the services search on (`orders.user_email`, `payments.user_email`...);
- decides the schema from the entities at startup: a typo in an annotation changes a production table, and
  nothing tells which schema a database has.

## Decision

The schema is written as **versioned SQL scripts run by Flyway**, one folder per application
(`src/main/resources/db/migration`), and Hibernate only validates it (`ddl-auto: validate`).

- The first scripts recreate exactly what Hibernate generated, constraint names included, so that a database
  created by Hibernate and a new one are the same.
- The databases created before Flyway are **baselined**: `baseline-on-migrate: true`, with `baseline-version`
  set to the last of those first scripts (5 for the monolith, 2 for product-service and order-service, 1 for
  auth-service and payment-service). Flyway records the existing tables as that version instead of failing on
  a non-empty schema, then runs the scripts that come after.
- The scripts that follow add the indexes the queries need (foreign keys, user emails, `upper(email)` for the
  case-insensitive registration check).
- Flyway runs **inside each application, at startup**, before JPA. Each service is the only one to touch its
  database, so no migration ever waits for another service. Flyway takes a PostgreSQL lock while it migrates,
  should two instances of one service ever start together.
- A script on `main` is never edited: the [CI](../../.github/workflows/ci.yml) fails a pull request that
  modifies, renames or deletes one.

## Alternatives not chosen

- **Keep `ddl-auto: update`:** nothing to do now, but every change it cannot make becomes a manual step on the
  server, and the schema has no history or review.
- **Liquibase:** also versioned, database independent, with rollbacks written next to each change. The project
  only targets PostgreSQL, and plain SQL is easier to review than XML or YAML changelogs; Spring Boot supports
  both equally.
- **One `V1__init.sql` per application:** fewer files, but one script per table makes each change easy to find
  and review. The baseline version only has to match the number of first scripts.
- **A separate migration step** (a Flyway container, or a job before `docker compose up`): useful when
  several instances start together or the application user must not own the tables. With one instance per
  service it adds a container to every deploy for no gain.

## Consequences

- A schema change is a new `V<n>__*.sql` next to the entity change, reviewed in the pull request. The
  integration tests run every script on an empty PostgreSQL and validate the entities, so a broken or missing
  script fails `./mvnw verify` and the CI before it reaches the server.
- `FlywayMigrationIT` in each project loads the DDL Hibernate used to run, baselines it, and checks that it ends
  with the same columns, constraints and indexes as a database migrated from `V1`.
- Each database has a `flyway_schema_history` table; [`/actuator/flyway`](../../README.md#health-actuator)
  shows it to an ADMIN.
- **Rolling back** to an older image still works: Flyway ignores the scripts it does not know (applied by a newer
  version), and Hibernate only validates the tables the older entities use. This holds as long as a script
  stays compatible with the previous version of the code: add columns as nullable or with a default, and drop a
  column only in a later release, once no deployed version reads it.
- The first deploy with Flyway baselines the four service databases and the monolith's, then creates the
  indexes. Nothing else changes in the tables.
