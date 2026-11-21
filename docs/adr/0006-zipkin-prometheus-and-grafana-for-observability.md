# ADR 0006: Zipkin, Prometheus and Grafana for observability

- **Status:** accepted
- **Date:** 2026-11-21
- **Issue:** #118, #119, #120

## Context

A request of the shop goes through the gateway, then one or two services: placing an order calls
product-service from order-service, a payment calls order-service from payment-service. Each application
already had its health checks and its metrics on `/actuator/metrics` (#53), but:

- nothing linked the log lines of one request across the containers: a slow or failed order meant reading the
  logs of three services side by side and matching them by time;
- `/actuator/metrics` gives the current value of one metric of one instance, for an ADMIN token. Nothing kept
  the history, compared the services, or computed a latency percentile.

The stack runs on one server with 4 GB of memory (ADR 0004): whatever is added must stay small, and must not
face the internet.

## Decision

**Traces: Micrometer Tracing with Brave, spans sent to Zipkin.**

- The gateway starts a trace for each request (or continues the W3C `traceparent` sent by the client) and
  passes it to the service it routes to. The Feign calls between services carry it (`feign-micrometer`), so
  one trace covers the gateway, order-service and product-service.
- Every log line has the trace id and the span id (Spring Boot's log correlation): the trace id of an error in
  the logs leads to the whole request in Zipkin.
- The health checks, the Prometheus scrapes, the calls of the Eureka client and the steps of the Spring
  Security filter chain are not traced (`ObservationConfig`, `management.observations.enable.spring.security`):
  without that, they make most of the traces.
- Sending is off by default (`ZIPKIN_ENABLED`): a service started alone with `./mvnw spring-boot:run` sends
  nothing. Docker Compose turns it on and samples every request (`TRACING_SAMPLING_PROBABILITY`).
- Zipkin keeps the spans in memory (100 000 at most): enough to follow recent requests, lost on restart.

**Metrics: Prometheus pulls `/actuator/prometheus`, finding the services through Eureka.**

- Prometheus asks Eureka for the instances of auth, product, order and payment service, like the gateway does,
  and reads the monolith at its fixed address. A second instance of a service is scraped as soon as it
  registers.
- The scrape endpoint is protected by HTTP Basic with a dedicated account (`METRICS_USERNAME` /
  `METRICS_PASSWORD`), in a filter chain of its own limited to that one path. Prometheus cannot log in and renew
  a JWT; the scraper account opens nothing else, and no user token opens the scrape endpoint.
- The HTTP request timers publish histogram buckets, from which Prometheus computes percentiles over every
  instance and any time range. 15 days or 1 GB of history are kept.
- The gateway and Eureka, which check no token, still expose no metrics.

**Dashboards: Grafana, provisioned from git.**

- The Prometheus and Zipkin data sources and the *E-commerce overview* dashboard are files in
  `infra/grafana`, loaded at each start. Grafana has no volume: what the dashboard shows is what is reviewed in
  a pull request, and a change made in the UI is exported into its JSON file to be kept.

Zipkin, Prometheus and Grafana run in Docker Compose next to the services, and share a `monitoring` network:
Grafana is only on that one, so it reaches Prometheus and Zipkin but no service, Eureka or PostgreSQL. On the server their ports only listen on the loopback, reached through an
SSH tunnel like the Eureka dashboard.

## Alternatives not chosen

- **OpenTelemetry (OTLP) to a collector, Tempo or Jaeger for the traces:** the standard path for a larger
  system, and Spring Boot 4 has a starter for it. It adds a collector container, or a trace store with its own
  configuration, for what Zipkin does alone in one container with an in-memory store. Moving later only changes
  the starter and the endpoint: the instrumentation (Micrometer Observation) stays the same.
- **Metrics pushed by the applications (OTLP, Pushgateway):** pull lets Prometheus notice an instance that
  stopped answering (`up == 0`), and the applications need no address of the metrics store.
- **A static list of targets in Prometheus:** simpler, but a new instance or service would have to be added by
  hand; Eureka already knows them.
- **The ADMIN JWT for the scrapes:** a JWT expires (1 h); Prometheus would need a token renewed outside it, and
  an ADMIN token in the Prometheus configuration opens every ADMIN endpoint.
- **A separate management port** for `/actuator`: would keep the scrape endpoint off the application port, but
  moves the health checks of Docker and Eureka and every Actuator URL of the documentation for one endpoint.
- **Centralised logs (Loki, ELK):** the trace id in each line already links the logs of one request;
  `docker logs` per container is enough at this size. Can be added next to Grafana later.

## Consequences

- Following a request: take the trace id from a log line (`[order-service] [nio-8083-exec-9] [6b01a5ca...-b0709a2a...]`) or
  search Zipkin by service, endpoint or duration. Grafana shows where the time and the errors are; Zipkin shows
  which call of which request was slow.
- `http.server.requests` and `http.client.requests` no longer count the `/actuator` requests and the Eureka
  client calls: the metrics only measure the traffic of the shop.
- Three more containers, about 530 MB together once started (Zipkin 210 MB, Prometheus 100 MB, Grafana
  220 MB; limits 384, 512 and 384 MB). With the tracing, the services also use a little more: the whole stack
  takes about 3 GB of the 4 GB of the server.
- The shop does not depend on them: with Zipkin stopped, the applications log one warning and drop their spans;
  with Prometheus stopped, the metrics are simply not recorded.
- `METRICS_PASSWORD` and `GRAFANA_ADMIN_PASSWORD` have no default on the server: `docker compose` stops before
  starting anything if `infra/.env` lacks them.
- The CI checks the Prometheus configuration with `promtool`, parses the Grafana files, and fails if a port of
  Zipkin, Prometheus or Grafana would be published on the internet.
