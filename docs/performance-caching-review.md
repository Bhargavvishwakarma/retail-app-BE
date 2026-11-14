# API performance and caching review

## Audit scope and evidence

The audit traced controller routes through services to Spring Data JPA, PostgreSQL, or the internal AI Processor. The code has request-level duration logging and Actuator, but this workspace has no representative request log sample, PostgreSQL query statistics, database execution plans, or measured response sizes. Query latency, request frequency, payload bytes, and p50/p95/p99 therefore remain unmeasured. The cache tests use mocked repositories and are correctness checks, not performance benchmarks.

`RequestLoggingFilter` records end-to-end request duration and status. It does not separate database time or report percentiles. The current dashboard implementation already uses one aggregate query plus one top-five projection query; those changes were present in the working tree before this cache change. The existing migrations also provide a sale-date covering index, an active-product stock index, and a customer name/id index. No additional index was added without production-shaped `EXPLAIN (ANALYZE, BUFFERS)` evidence.

## Cache plan

Only the two dashboard summary service methods are cached:

- `dashboardSummary`: 60-second write TTL, maximum 10 entries.
- `dashboardOperational`: 60-second write TTL, maximum 10 entries.

Both keys use the current `LocalDate`. The summary includes scalar metrics and an immutable list of `RecentSaleResponse` records; both cached maps are unmodifiable. Separate regions keep the owner-only summary distinct from the operational summary available to receptionists. Security authorization still runs on every request before the controller calls the service.

This schema has no tenant or shop identifier; dashboard data is global to the current single-store database. If tenant scoping is introduced, both the SQL and cache keys must include the tenant/shop ID before these caches can be reused safely.

Product create/update/archive, sale creation, and customer create/delete evict both current-day dashboard entries. Customer edits and category writes do not change dashboard fields. Sale creation is transactional and the cache manager defers key eviction until transaction commit. Product and customer writes currently rely on repository transactions that complete before their service methods return. Failed service operations do not trigger eviction. The TTL bounds stale data if an in-flight read races a write and repopulates an old value after eviction.

## APIs excluded from caching

- Product catalog, search, low-stock, and detail routes return mutable JPA entities and inventory changes on each sale. Listing variants also have high-cardinality search/page inputs. These need response-size, frequency, and inventory-consistency measurements before considering DTO-based caching.
- Category reads are a small reference-data query, but currently return JPA entities. Their likely cache benefit is low without evidence of repeated load; category CRUD remains uncached.
- Customer list/detail routes contain contact information and return JPA entities. Search and pagination variants are high-cardinality, so they remain uncached.
- Sales list/detail routes are dynamic and owner-only. The optional paginated path uses a lightweight projection; the legacy unpaged path returns full sale graphs with eager associations and is a query-count/response-size measurement candidate.
- Authentication, refresh/logout, user management, and preference routes remain uncached because they depend on current account, role, token version, or password state.
- Chat, model discovery, conversation history, and internal AI data remain uncached because they call an upstream service or expose user/role-sensitive or frequently changing information. HMAC and actor authorization checks must run on every internal AI request.
- Actuator metrics are owner-only and uncached.

## Database and runtime findings

Product and customer unpaged reads and the legacy unpaged sale graph can return data proportional to table size. The paginated controller paths already present in the workspace reduce response volume for callers that use them. Sale creation performs product lookup and update work per line item; changing that path without production-shaped load tests could affect stock correctness. Product and customer substring filters use leading-wildcard matching, for which the existing ordinary indexes may not help at scale. These are candidates for measurement, not speculative migration changes.

The dashboard aggregate counts sales in a date range, active/low-stock products, and all customers. Current migration indexes support the sale-date and active-stock predicates. The customer count remains proportional to table size. The recent-sales query returns only five projected rows. PostgreSQL plans and table sizes are unavailable here, so these observations are query-shape analysis, not measured bottlenecks.

The application uses the default Hikari connection pool configuration; no connection-acquisition timing or pool saturation samples were available. Actuator and Micrometer are already dependencies. Caffeine stats are enabled, and cache plus HTTP server metrics are registered through Micrometer. `/actuator/health` and `/actuator/metrics` are exposed only to owners by the existing JWT security chain; health details stay hidden. Request latency is also written to logs.

## Consistency, deployment, and limits

Caffeine is process-local. The Dockerfile builds one application container, but the workspace contains no deployment replica/autoscaling configuration, so the deployed instance count cannot be verified. Local invalidation is correct within the process; another instance will not receive that eviction and may serve dashboard data up to the configured 60-second TTL. Dashboard metrics are reporting data and currently accept that eventual-consistency window. Transactionally critical inventory and sales responses are not cached.

The cache is bounded to at most 10 entries per region (20 total), with each value containing only a compact summary and at most five sale records. Heap impact was not measured; Caffeine limits entry count rather than bytes. There are no before/after latency, query-count, hit-ratio, eviction, or heap benchmark results from a live database. The cache tests verify hit reuse, day-key separation, TTL expiry, maximum size, successful-write eviction, transaction-aware eviction, and role authorization without fabricating timing numbers.

## Route inventory

The controller-level route decisions are summarized here; the accompanying audit canvas expands each route into its data access, scope, cost shape, and decision.

- Authentication and account operations: `POST /api/auth/login`, `/google`, `/refresh`, `/logout`, `/receptionists`; `GET /api/auth/me`, `/users`; `PUT /api/auth/me/preferences`; `PATCH /api/auth/users/{userId}/status`, `/role`. No cache.
- Products: `GET /api/products` (search/page variants), `/low-stock`, `/{id}`; `POST /api/products`; `PUT /api/products/{id}`; `DELETE /api/products/{id}`. Reads uncached; writes evict dashboard summaries where relevant.
- Categories: `GET /api/categories`; `POST`, `PUT /api/categories/{id}`, and `DELETE /api/categories/{id}`. No cache.
- Customers: `GET /api/customers` (search/page variants), `/{id}`; `POST`, `PUT /api/customers/{id}`, and `DELETE /api/customers/{id}`. Reads uncached; create/delete evict dashboard summaries.
- Sales: `GET /api/sales` (date/search/page variants), `/{id}`; `POST /api/sales`. Reads uncached; successful sale creation evicts dashboard summaries after commit.
- Dashboard: `GET /api/dashboard/summary` and `/operational`. Cached separately for 60 seconds.
- Chat: `POST /api/chat`; `GET /api/chat/models`, `/conversations` (page/size variants), and `/conversations/{conversationId}`. No cache.
- Internal AI: `POST /internal/v1/ai-data/query`. No cache; request signatures, actor state, and operation-level role checks run every time.
- Operations: `GET /actuator/health` and `/actuator/metrics[/{requiredMetricName}]`. Owner-only; no cache.
