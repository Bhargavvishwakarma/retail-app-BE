# Google Sign-In and role access

## Server configuration

Set these environment variables in the backend runtime. Do not put secrets in source control.

| Variable | Required | Purpose |
| --- | --- | --- |
| `GOOGLE_CLIENT_ID` | Yes for Google Sign-In | Google Web Client ID used as the accepted ID-token audience. |
| `JWT_SECRET` | Yes | Base64-encoded signing key with at least 32 decoded bytes. Generate one with `openssl rand -base64 32`. |
| `DB_URL` | Deployment-specific | PostgreSQL JDBC URL. |
| `DB_USER` | Deployment-specific | PostgreSQL username. |
| `DB_PASSWORD` | Yes | PostgreSQL password. |
| `BOOTSTRAP_OWNER_EMAIL` | First setup only | Verified Google email for the actual shop owner. Omit after an Owner exists. |
| `BOOTSTRAP_OWNER_NAME` | Optional | Display name used for the first Owner; defaults to `Owner`. |
| `CORS_ALLOWED_ORIGINS` | Optional | Comma-separated allowed origins. Defaults to the production React URL and `http://localhost:5173`. |
| `JWT_EXPIRATION_SECONDS` | Optional | Application JWT lifetime; defaults to 28800 seconds. |
| `AI_PROCESSOR_SHARED_SECRET` | If using internal AI calls | Shared secret for signed internal service requests. |
| `AI_ACTOR_TOKEN_SECRET` | Optional | Dedicated key for short-lived internal actor assertions; defaults to `JWT_SECRET`. |

The Google OAuth client secret is not used for ID-token verification and must not be configured in this backend. The server verifies Google ID tokens using Google's signing keys and accepts only the configured Web Client ID audience.

## Owner initialization

1. Back up the PostgreSQL database and deploy the backend with a strong `JWT_SECRET`, database variables, and `GOOGLE_CLIENT_ID` set.
2. For the first startup only, set `BOOTSTRAP_OWNER_EMAIL` to the actual shop owner's Google email and, optionally, `BOOTSTRAP_OWNER_NAME`.
3. Flyway applies `V4__add_google_identity_and_account_access.sql`. The startup bootstrap takes a PostgreSQL advisory transaction lock and creates the configured account as `OWNER` only if no Owner role already exists. It never changes the role of an existing account.
4. The Owner signs in at `POST /api/auth/google` with a Google account whose verified email exactly matches the configured email. The first successful login binds the stable Google `sub` to that pre-created account.
5. Remove `BOOTSTRAP_OWNER_EMAIL` from the runtime configuration and restart. A later configuration cannot promote another person while any Owner record exists.

If the configured email already belongs to a non-Owner account while no Owner exists, startup fails instead of promoting it. Resolve that state through a controlled database administration process.

## API contract

All JSON errors use `application/problem+json` (RFC 9457) with the standard fields `type`, `title`, `status`, `detail`, and `instance`, plus `code`, `timestamp`, and `requestId`.

### Google login

`POST /api/auth/google` is public.

```json
{ "idToken": "<Google ID token from Google Identity Services>" }
```

Success (`200`):

```json
{
  "token": "<application JWT>",
  "tokenType": "Bearer",
  "expiresIn": 28800,
  "user": {
    "id": "<application UUID>",
    "name": "Shop Owner",
    "email": "owner@example.com",
    "role": "OWNER",
    "workspaceTheme": "forest",
    "createdAt": "2026-01-01T10:00:00",
    "active": true
  }
}
```

The Google token must have a valid signature, Google issuer, unexpired `exp`, the configured audience, a non-empty `sub`, and `email_verified: true`. The backend matches by stored Google `sub`; an unlinked, pre-registered user can be linked once by its exact normalized verified email. Unknown accounts receive `403`; invalid tokens, unverified emails, and conflicting identity bindings receive `401`. Google tokens are never returned or logged.

The existing `POST /api/auth/login` password endpoint remains available for previously provisioned password accounts. The first Owner created by bootstrap has no password and must use Google Sign-In. Existing application JWTs from before this release must sign in again because the new tokens use an application-user UUID subject, issuer, audience, and token version.

### Current user

`GET /api/auth/me` requires `Authorization: Bearer <application JWT>` and returns the `user` object shown above.

### Logout

`POST /api/auth/logout` requires the application bearer token and returns `204 No Content`. It increments the user's token version, invalidating all JWTs issued to that user.

### Receptionist pre-registration

`POST /api/auth/receptionists` requires an Owner bearer token.

```json
{ "name": "Front Desk", "email": "desk@example.com" }
```

Success is `201 Created` with the created user object. This pre-registers an active `RECEPTIONIST` with no password and no Google subject. The staff member becomes able to sign in only with a Google account whose verified email matches this address. Duplicate emails return `409`. The backend does not send email; the Owner must communicate the invited address through the shop's normal process.

### Staff list and account administration

`GET /api/auth/users` requires an Owner token and returns user objects (paged with optional `page` and `size`).

`PATCH /api/auth/users/{id}/status` requires an Owner token:

```json
{ "active": false }
```

`PATCH /api/auth/users/{id}/role` requires an Owner token:

```json
{ "role": "RECEPTIONIST" }
```

Allowed roles are `OWNER` and `RECEPTIONIST`. Owners cannot change their own access, and the last active Owner cannot be deactivated or demoted. Role changes take effect on the next request because authorities are loaded from PostgreSQL, not trusted from the JWT.

## Authorization summary

- Owners can list, pre-register, activate/deactivate, and change staff roles; create/update/delete products and categories; view low-stock data, sales, and the dashboard; and access shared operational APIs.
- Receptionists can use chat, read products/categories/customers, view low-stock inventory, create customers and sales, and read their own profile/preferences. They cannot administer staff, change inventory, list sales, or access the Owner dashboard.
- Receptionists can read `GET /api/dashboard/operational`, which omits revenue and recent sale details; `GET /api/dashboard/summary` remains Owner-only.
- Other `/api/**` requests require authentication. Unauthenticated requests return `401`; authenticated users without the required role receive `403`.

## CORS and frontend integration

`GET /api/dashboard/operational` returns `todaySalesCount`, `totalProducts`, `lowStockCount`, and `totalCustomers` without revenue or recent-sale data. `GET /api/dashboard/summary` remains Owner-only.

Allowed browser origins default to:

- `https://retail-app-fe.bhargavvishwa2002.workers.dev`
- `http://localhost:5173`

The API allows `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, and `OPTIONS`; request headers `Authorization`, `Content-Type`, and `X-Request-Id`; it does not use credentialed cookies. Keep the application JWT in frontend memory or another protected browser storage strategy and send it as a bearer header. A role change is reflected immediately; logout, account deactivation, and account removal invalidate access without waiting for the JWT's expiry.

## Migration and manual Google test

Flyway V4 adds nullable `google_sub`, `active` (backfilled to `true` for existing users), and `token_version` (backfilled to `0`), makes `password_hash` nullable for Google-only accounts, and adds unique indexes. It does not drop or rewrite user/business rows. The case-insensitive email index will fail safely if old data contains duplicate addresses differing only by case; inspect and resolve those duplicates before deployment.

For a real login test, configure the Google Web Client ID on both Google Identity Services and `GOOGLE_CLIENT_ID` on the backend, ensure the owner's account was bootstrapped or the staff email pre-registered, obtain an ID token through the frontend Google Sign-In flow, and POST it to `/api/auth/google`. Then call `/api/auth/me` with the returned application JWT and verify staff-only/Owner-only routes using each role. Never use the Google OAuth client secret in the browser or this ID-token exchange.
