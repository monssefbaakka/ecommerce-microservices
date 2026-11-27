# Observability

With `docker compose --profile app`, three more containers follow what the stack does: **Zipkin** for the
traces, **Prometheus** for the metrics history, **Grafana** for the dashboards. Why these:
[ADR 0006](adr/0006-zipkin-prometheus-and-grafana-for-observability.md).

## Traces (Zipkin)

Every request gets a trace id, from the gateway to the last service it reaches. The gateway starts the trace (or
continues the W3C `traceparent` header sent by the client) and passes it to the service; the Feign calls carry it
on (order-service to product-service, payment-service to order-service). Each application sends its spans to
Zipkin, and writes the trace id and the span id in every log line, after its name and thread:

```
2026-11-21T14:08:10.297Z DEBUG 1 --- [order-service] [nio-8083-exec-9] [6b01a5ca309b5b7f242914dc3578de05-b0709a2a8efc917e] c.m.o.product.ProductServiceApi : [ProductServiceApi#reserve] ---> POST http://product-service/internal/stock/reservations HTTP/1.1
```

To follow a request: copy the trace id of a log line into the search box of **http://localhost:9411**, or search
Zipkin by service, endpoint, duration or error. Placing an order shows the span of the gateway, `POST
/api/orders` in order-service and, under it, the calls to product-service with the time spent in each.

The health checks, the Prometheus scrapes, the calls of the Eureka client and the steps of the Spring Security
filter chain are not traced: they would make most of the traces.

| Variable | Default | |
|---|---|---|
| `ZIPKIN_ENABLED` | `false` (`true` in Compose) | Send the spans. Off when an application runs alone (`./mvnw spring-boot:run`): the trace id is still in the logs |
| `ZIPKIN_URL` | `http://localhost:9411/api/v2/spans` (`http://zipkin:9411/...` in Compose) | Where to send them |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Share of the requests whose spans are sent, e.g. `0.1` under real traffic. Set in `infra/.env` for the whole stack |

Zipkin keeps the spans in memory (100 000 at most): they are lost when its container restarts. When Zipkin is
down, the applications keep answering: they log one warning and drop the spans until it is back.

## Metrics (Prometheus)

Every 15 s, Prometheus reads `/actuator/prometheus` on every instance of auth, product, order and payment service
([infra/prometheus/prometheus.yml](../infra/prometheus/prometheus.yml)). It asks Eureka which instances exist,
like the gateway: a second instance of a service is scraped as soon as it registers. 15 days (1 GB at most) of
history are kept in the `prometheus_data` volume.

**http://localhost:9090/targets** lists what is scraped (every line should be `UP`), and the query page runs
PromQL, e.g. `sum by (application) (rate(http_server_requests_seconds_count[5m]))`.

The scrape endpoint is protected by a dedicated account with HTTP Basic, not by a JWT (Prometheus cannot log in
and renew a token). The applications and Prometheus read the same two variables:

| Variable | Default | |
|---|---|---|
| `METRICS_USERNAME` | `prometheus` | Scraper account |
| `METRICS_PASSWORD` | `metrics-dev-only` | Its password; required on the server, no default there |

```bash
curl -u prometheus:metrics-dev-only http://localhost:8083/actuator/prometheus | grep orders_placed
```

## Dashboards (Grafana)

**http://localhost:3001**, `admin` / `admin` in dev (`GRAFANA_ADMIN_PASSWORD`). The home page is the
*E-commerce overview* dashboard, filtered by application:

| Row | Panels |
|---|---|
| Overview | Each scraped instance `UP` or `DOWN` |
| HTTP requests | Requests per second, share of 5xx, latency p95 per application, the 10 slowest endpoints, latency of the Feign calls between services |
| Resilience and business | Circuit breakers open or half open, orders, payments and cancellations per minute (and carts shown from the fallback), error log lines per minute |
| JVM and database | Heap used, CPU, database connections in use and waiting |

The traces are in *Explore* → *Zipkin*: paste a trace id, or search by service. The data sources and the
dashboard are files in [infra/grafana](../infra/grafana), loaded at each start; Grafana has no volume, so a change
made in the UI is lost on restart: export the dashboard (*Share* → *Export*) into
`infra/grafana/dashboards/ecommerce-overview.json` to keep it.
