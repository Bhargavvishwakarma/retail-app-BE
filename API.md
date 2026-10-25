# Frontend authentication and API guide

Base URL for local development: `http://localhost:8080`

All entity IDs are UUID strings. For example, `/api/products/{id}` and sale request `customerId` / `productId` values use UUIDs. The initial Flyway migration creates UUID columns and relationships directly. This migration chain is for a fresh database; it does not preserve or convert data from the former numeric-ID schema.

## First owner setup

There is no public signup endpoint, so a client cannot grant itself owner access. On the first backend start, set `BOOTSTRAP_OWNER_EMAIL` and `BOOTSTRAP_OWNER_PASSWORD` (and optionally `BOOTSTRAP_OWNER_NAME`). The backend creates that owner if the email is not already present. The password must be at least 8 characters. Keep these environment values private and remove the password from the runtime environment after the initial account is created.

For deployments, set `JWT_SECRET` to a base64-encoded random value containing at least 32 bytes. Example generation: `openssl rand -base64 32`. Local development has a built-in development-only fallback so the app can start without this variable; never use that fallback in a deployed or shared environment. Tokens expire after 8 hours by default; set `JWT_EXPIRATION_SECONDS` to change this.

Example local environment values (use your own secrets):

```text
JWT_SECRET=<base64-encoded 32+ random bytes>  # required outside local development
BOOTSTRAP_OWNER_NAME=Store Owner
BOOTSTRAP_OWNER_EMAIL=owner@example.com
BOOTSTRAP_OWNER_PASSWORD=<private password, 8+ characters>
```

## Login

`POST /api/auth/login` is public.

Request:

```json
{
  "email": "owner@example.com",
  "password": "your-password"
}
```

Success (`200`):

```json
{
  "token": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 28800,
  "user": {
    "id": "9c1a5fc4-7b01-4f6e-9b18-13861a5748ee",
    "name": "Store Owner",
    "email": "owner@example.com",
    "role": "OWNER",
    "workspaceTheme": "forest",
    "createdAt": "2026-10-24T10:00:00"
  }
}
```

Send the token on every protected request:

```http
Authorization: Bearer eyJ...
```

Use the `user.role` value (`OWNER` or `RECEPTIONIST`) to choose frontend screens. The backend also checks roles; hiding a screen in the frontend is not the security boundary. Invalid credentials return `401`; missing/invalid/expired tokens return `401`; insufficient role returns `403`.

## Error responses

All API errors use the RFC 9457 `application/problem+json` format. The response includes a stable application `code`, timestamp, request path, and the `X-Request-Id` used in server logs. Validation errors include one entry per invalid field. Internal exception details and stack traces are never returned.

```json
{
  "type": "about:blank",
  "title": "Service Unavailable",
  "status": 503,
  "detail": "AI Processor is unavailable",
  "instance": "/api/chat",
  "code": "SERVICE_UNAVAILABLE",
  "timestamp": "2026-10-24T08:00:00Z",
  "requestId": "50ca79e2-d28b-4cdb-a9a0-15366207cc86"
}
```

Validation failures also contain `errors`, an array of `{ "field", "message" }` objects. Clients should use `detail` for a displayable summary, `code` for programmatic handling, and `requestId` when reporting an issue. The request ID is also returned in the `X-Request-Id` response header.

`GET /api/auth/me` returns the logged-in user's `id`, `name`, `email`, `role`, and `workspaceTheme`. The theme is one of `forest`, `ocean`, `berry`, or `clay`, and defaults to `forest` for existing and new users.

`PUT /api/auth/me/preferences` updates the authenticated user's saved workspace theme. It accepts the theme string and returns the updated public user profile; no user ID is accepted from the client, so users can update only their own preference.

```json
{ "workspaceTheme": "ocean" }
```

A JWT is stateless; logging out in the frontend means discarding the token. The frontend should require a fresh login after `expiresIn` seconds.

`GET /api/auth/users` is owner-only and returns all app users for the owner management screen. Each entry contains only `id`, `name`, `email`, `role`, and `createdAt`; password hashes are never returned.

```json
[
  {
    "id": "9c1a5fc4-7b01-4f6e-9b18-13861a5748ee",
    "name": "Front Desk",
    "email": "frontdesk@example.com",
    "role": "RECEPTIONIST",
    "createdAt": "2026-10-06T18:00:00"
  }
]
```

## Create receptionist accounts

Only an owner can create a receptionist. `POST /api/auth/receptionists` (Bearer token required):

```json
{
  "name": "Front Desk",
  "email": "frontdesk@example.com",
  "password": "a-private-password"
}
```

Returns `201` and the public user details (no token or password). A receptionist then signs in through `/api/auth/login` like the owner. There is deliberately no public endpoint for choosing a role.

## Database migrations

Flyway applies migrations from `src/main/resources/db/migration` during startup. Hibernate validates the schema and no longer alters it automatically. `V1` creates the schema with UUID primary keys. Start with an empty database; this migration chain does not import or convert the old numeric-ID schema.

## Current role access

| API operation | Owner | Receptionist |
| --- | --- | --- |
| Read products, categories, customers | Yes | Yes |
| Low-stock report | Yes | No |
| Create/update/delete products and categories | Yes | No |
| Create/update customers | Yes | Yes |
| Delete customers | Yes | No |
| Create sales | Yes | Yes |
| Read sales and dashboard summary | Yes | No |
| Read customers with recorded sales today | Yes | No |
| Create receptionist login | Yes | No |

## Retail chat

`POST /api/chat` requires a valid bearer token for either role. The frontend sends only the question and optional conversation ID; identity and role are resolved from the authenticated account on the server.

`GET /api/chat/models` requires the same authentication and returns the processor's configured allowlisted models. The frontend should use a returned `id` as `modelId`; provider API keys are never sent to the browser.

`GET /api/chat/conversations?page=0&size=8` requires a valid bearer token and returns only the authenticated user's conversation summaries. Pages are zero-based, `page` defaults to 0, `size` defaults to 10, and `size` must be between 1 and 50. Results are ordered by most recently updated first, with conversation ID as a stable tie-breaker. Each list item contains only `conversationId`, `title`, and `updatedAt`; message contents are returned only by `GET /api/chat/conversations/{conversationId}` after the user opens a conversation. The response contains `content`, `page`, `size`, and `hasMore`. An out-of-range page returns an empty `content` array and `hasMore: false`. Invalid pagination values return `400`; missing or invalid authentication returns `401`.

```json
{
  "content": [
    {
      "conversationId": "50ca79e2-d28b-4cdb-a9a0-15366207cc86",
      "title": "Which products are low on stock?",
      "updatedAt": "2026-10-24T08:00:00Z"
    }
  ],
  "page": 0,
  "size": 8,
  "hasMore": true
}
```

```json
{
  "question": "Which products are low on stock?",
  "conversationId": "optional",
  "modelId": "ollama/qwen3.5:9b"
}
```

The synchronous JSON response contains `answer`, `conversationId`, the selected `modelId`, and `dataSources`. Set `AI_PROCESSOR_URL` (default `http://localhost:8090`) and the same private `AI_PROCESSOR_SHARED_SECRET` in both services. Also set a separate, random base64 `AI_ACTOR_TOKEN_SECRET` for short-lived actor assertions; keep all secrets out of source control. If the model or retail data service is unavailable, the route returns an error rather than fabricating current figures.

Invalid or unavailable model IDs return `400`; unauthorized retail-data access returns `403`; provider/data outages return `503`, and timeouts return `504`.

The private `/internal/v1/ai-data/query` endpoint is HMAC-authenticated and requires a short-lived backend-signed actor assertion. It exposes read-only operations only. Sales summaries, best sellers, recent sales, customer visit reports, and low-stock reports are OWNER-only. The customer visit report lists names tied to today's recorded sales, transaction counts, and unnamed walk-in transactions; it cannot count people who entered without a recorded sale. `CUSTOMER_DIRECTORY` returns the total customer count and all customer records with fields requested from the fixed `name`, `phone`, `email`, and `address` allowlist (the name is always included). Current inventory omits archived products. Other customer responses are aggregate summaries without contact details.

All existing API paths are under `/api`. Existing request and response shapes are unchanged. The route list and DTOs are in the controllers; the above table describes the new access rules.

`DELETE /api/products/{id}` archives the product (`active: false`) instead of physically deleting its row. This preserves links from historical sale items. Archived products are omitted from product lists, search results, low-stock results, and dashboard product counts; existing sales keep their product reference. Owners can reactivate an archived product by updating it with `active: true`.

## Frontend fetch example

```js
const response = await fetch("http://localhost:8080/api/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email, password }),
});
const result = await response.json();
if (!response.ok) throw new Error(result.detail ?? "Login failed");

localStorage.setItem("token", result.token);
// Keep result.user.role in app state and route OWNER / RECEPTIONIST accordingly.
```

For protected calls, add the token header:

```js
const token = localStorage.getItem("token");
const response = await fetch("http://localhost:8080/api/products", {
  headers: { Authorization: `Bearer ${token}` },
});
```

For production, serve frontend and API over HTTPS and store tokens in a way that matches the frontend's XSS threat model.
