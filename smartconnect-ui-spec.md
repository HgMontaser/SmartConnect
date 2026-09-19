# SmartConnect — UI Spec

Paste this into `smartconnect-claude-code-prompt.md` (or give it to Claude Code alongside screenshots of the three screens) so component styling matches exactly instead of being guessed from images alone.

---

## Design tokens

```css
:root {
  /* Backgrounds */
  --bg: #0A0D12;
  --surface: #12161D;
  --surface-2: #171C24;
  --surface-3: #1B2028;
  --border: #232933;

  /* Text */
  --text: #ECEFF3;
  --text-2: #8B95A5;
  --text-3: #5B6472;

  /* Accent + status */
  --accent: #2DD4BF;   /* teal — primary actions, live indicators */
  --success: #34D399;  /* active / online */
  --warn: #F5A623;     /* pending */
  --danger: #F87171;   /* suspended / error */
}
```

Radii: 8px (inputs/buttons), 10–12px (cards/panels), 999px (pills/badges).
Borders: 1px solid `--border` everywhere — no shadows for elevation, borders only.
Spacing scale: 4 / 8 / 12 / 16 / 20 / 24 / 28 / 32px.

## Typography

Load via Google Fonts:
```
Space+Grotesk:wght@500;600;700
IBM+Plex+Sans:wght@400;500;600
IBM+Plex+Mono:wght@400;500
```

| Use | Font | Weight | Size |
|---|---|---|---|
| Page/section headings | Space Grotesk | 600–700 | 22–40px |
| Body text, labels, buttons | IBM Plex Sans | 400–600 | 13–16px |
| Technical values (realm, IDs, timestamps, event names) | IBM Plex Mono | 400–500 | 11–12.5px |

Never Inter/Roboto/Arial — that's the whole point of this pairing.

## Global component patterns

- **Primary button**: 46–48px height, `background: var(--accent)`, text `#04211D` (dark, not white — keeps contrast on teal), weight 600, radius 8px.
- **Ghost/secondary button**: transparent bg, 1px `--border`, text `--text`, hover → `background: var(--surface-3)`.
- **Input**: 46px height, `background: var(--surface-2)`, 1px `--border`, radius 8px, label above in `--text-2` 13px. Focus state: border → `--accent`, plus a soft `0 0 0 3px rgba(45,212,191,0.15)` ring.
- **Status pill**: pill radius, tinted background at ~12% opacity of the status color, text in the full status color, 11.5px medium.
- **Live indicator dot**: 6px circle, color = `--success` (system online) or `--accent` (Kafka event just landed), with a soft outward `box-shadow` pulse animation (~2.2s ease-out loop) — this is what signals "just updated via Kafka" vs a static timestamp.
- **Icons**: inline stroke SVGs only, 1.6–1.8px stroke, no filled icon sets, no emoji.

## Screen 1 — Login (`login-app`)

1440×900, split layout, no scroll.

- **Left panel** (560px, `--bg` with a faint 22px dot-grid): wordmark top, headline + subtext middle ("One gateway. Every channel, secured."), three feature chips (OAuth2/OIDC via Keycloak realm `smartconnect`, Kafka event bus, Vault-backed secrets), footer status line (`gateway-01 · online · v1.0`).
- **Right panel** (`--surface`, centered 380px form): "Sign in to continue" heading, username + password fields, "keep me signed in" checkbox + forgot-password link, primary Sign in button, divider, ghost "Continue with Company SSO" button, footer caption "Secured by Keycloak · session encrypted via Vault".
- Top-right corner: floating "Gateway online" status pill.

## Screen 2 — SSO handshake (`login-app`, post-login transition)

1440×900, single centered card (520px) on the same dotted `--bg`.

- Vertical 3-step tracker: **Verifying credentials** (done — green check), **Establishing SSO session** (active — spinning ring in `--accent`), **Redirecting to workspace** (pending — grey outline).
- Thin animated progress bar under the stepper.
- Mono technical readout box: `realm: smartconnect`, `client: login-app`, `session: sso_9f21e4...c88a`, `redirect: workspace.smartconnect.ram/home`.
- This is the screen that does the actual `checkSsoSilently()` handoff — no user action, auto-redirects on success.

## Screen 3 — Backoffice users (`backoffice-app`)

1440×900, app shell layout.

- **Topbar** (64px): wordmark + "Backoffice" label + realm chip, search input, Kafka status pill (`Kafka · live · 3 consumers` with pulsing green dot), avatar.
- **Sidebar** (220px): Users (active), Roles & permissions, Sessions, Event log, Gateway health, Settings. Active state = subtle `--surface-2` background fill, never a colored left border.
- **Main — Users table**: columns Name / Email / Role / Status / Last sync / actions. Status = colored pill. "Last sync" shows either a relative static timestamp (`14 min ago`) **or**, if the row was just touched by a Kafka event, a pulsing accent dot + `2s ago` in accent color — this is the visual tell for "this row updated live, not from a page reload."
- **Right rail — Event stream** (320px): scrolling list of raw Kafka events (`user.updated`, `user.created`, `session.login`, `user.suspended`), each with event name, relative time, and a one-line human description. This panel is what proves the async path is wired end-to-end, separate from the synchronous table view.

---

**Note for the Claude Code prompt:** tell it explicitly that "Last sync" on the table and the event-stream panel are driven by the same Kafka consumer — that's the detail that makes it build one WebSocket/SSE bridge feeding both, instead of two disconnected polling loops.
