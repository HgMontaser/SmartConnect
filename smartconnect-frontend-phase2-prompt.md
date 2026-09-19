# SmartConnect — Phase 2: Frontend

Paste this into Claude Code once phase 1 (backend) is confirmed working end to end. **Backend is done — do not touch `libs/commons`, `services/api-gateway`, `services/user-service`, or `services/notification-service` in this phase.**

---

## Before writing any UI code

Read `smartconnect-ui-spec.md` in this repo first. It's the source of truth for every visual and layout decision — colors, typography, spacing, components, branding. Don't improvise on design; if something needed isn't covered in the spec, ask before guessing.

## Mission

Build the two frontend apps against the backend that already exists: `login-app` (standalone auth) and `backoffice-app` (admin panel for user management). Both talk to `api-gateway`, both auth against the `smartconnect` Keycloak realm, both follow `smartconnect-ui-spec.md` for how they look.

## Repo structure (this phase)

```
smartconnect/
├── apps/
│   ├── login-app/
│   └── backoffice-app/
└── smartconnect-ui-spec.md   # already in repo root — read before building
```

## Tech stack

React 18 + Vite, `keycloak-js` adapter. Whatever component/styling approach `smartconnect-ui-spec.md` specifies — follow it rather than defaulting to your own choice.

## Build order

### 1. `login-app`
- Own deploy target, e.g. `auth.smartconnect.local`
- `keycloak-js`, Authorization Code + PKCE flow against the `login-app` Keycloak client (already configured in the realm from phase 1)
- On successful handshake (token confirmed valid): redirect the browser to the business app's home URL (configurable env var `BUSINESS_APP_HOME_URL`). Rely on Keycloak's SSO cookie so the business app can pick up the session silently (`checkSsoSilently`) — no tokens in the redirect URL
- One screen, no app shell beyond it — follow the login screen spec in `smartconnect-ui-spec.md` exactly
- Confirm: successful login redirects; failed login shows the error state from the spec, not a default browser alert

### 2. `backoffice-app`
- Auth via the `backoffice-app` Keycloak client, same PKCE pattern
- User list/create/update/delete, calling `api-gateway` → `user-service` (never `user-service` directly)
- Subscribe to the gateway's websocket bridge for live updates when `user-events` fire — two admins should see each other's changes without refreshing
- Layout, navigation, table/form components — all from `smartconnect-ui-spec.md`, not invented on the fly
- Confirm: a user created from one browser tab shows up live in another tab without a manual refresh

## Constraints

- No secret in a frontend `.env` checked into git — Keycloak client IDs are public and fine, but nothing else
- `backoffice-app` never calls `user-service` directly, only through the gateway
- Match `smartconnect-ui-spec.md` — if a screen or state isn't in the spec, ask rather than inventing it
- Don't touch anything under `libs/` or `services/` — that's phase 1, already verified

## Stop condition

Stop after both apps are confirmed working against the live backend and report back.
