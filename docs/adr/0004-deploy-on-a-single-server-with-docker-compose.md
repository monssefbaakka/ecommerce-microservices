# ADR 0004: Deploy on a single server with Docker Compose

- **Status:** accepted
- **Date:** 2026-11-13
- **Issue:** #59

## Context

The stack is nine containers: PostgreSQL, Eureka, the gateway, four services and the monolith, with Caddy in
front once deployed. The CI already tests every project and publishes its image on ghcr.io (#57), and
[infra/docker-compose.ghcr.yml](../../infra/docker-compose.ghcr.yml) starts the whole stack from those images.
Issue #59 asks for a *simple* host: a demo that anyone can call, for a few euros a month at most, which one
person can set up and understand end to end.

The options:

- **A PaaS** (Render, Railway, Fly.io...): one app per container, so seven apps plus a managed database. The
  free tiers sleep after a few minutes, and a JVM woken by a request takes 20 s or more to answer; seven always-on
  apps cost more than a server. The services find each other through Eureka by container address, which each
  platform handles differently (private networking is often paid or missing).
- **Kubernetes** (managed, or k3s on a server): the right tool for many servers, rolling updates and
  autoscaling. For one demo it adds a cluster to run and every manifest to write again, while the Compose file
  already describes the networks, health checks, memory limits and start order.
- **One server with Docker and the existing Compose files**: the same files as in development, with a
  production override merged on top.

## Decision

Deploy on **one Linux server with Docker Compose**, from the published images:

- [docker-compose.prod.yml](../../infra/docker-compose.prod.yml) is merged over the two existing files. It
  publishes no port of the stack (`ports: !reset []`), adds **Caddy** as the only public entry point (HTTPS with
  a Let's Encrypt certificate for `DOMAIN`), and makes `JWT_SECRET` and `POSTGRES_PASSWORD` required.
- The secrets live in `infra/.env` **on the server only**. GitHub only holds an SSH key that can deploy.
- A [Deploy workflow](../../.github/workflows/deploy.yml) runs after each successful CI run on `main`: it copies
  `infra/` over SSH and runs [deploy.sh](../../infra/deploy.sh) with the image tag of the tested commit,
  `sha-<commit>`, never `latest`.
- `deploy.sh` pulls first, then `docker compose up --wait`; if a container never gets healthy, it starts the
  previous version again.

## Consequences

- Nothing new to learn or maintain: the server runs what `docker compose` runs on a laptop, and the CI checks
  the production override like the other Compose files (no port facing the internet but Caddy's).
- The deploy is tied to the tests: only an image that passed the CI on `main` can reach the server, and the
  exact one (an immutable `sha-` tag). Rolling back is deploying an older tag.
- Without the `DEPLOY_HOST` variable the workflow is skipped: a fork or a clone without a server still has a
  green Actions tab.
- One server is a single point of failure, PostgreSQL included, and each deploy makes a recreated service
  unavailable for a few seconds. Acceptable for a demo; moving to several servers would mean an orchestrator
  (Kubernetes, Swarm, Nomad) and a managed database, starting from the same images.
- The images are `linux/amd64` only: the server must be x86_64.
