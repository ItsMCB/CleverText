# CleverText

Enhances the text experience for Paper Minecraft servers with priority and permission-based chat formatting, multi-surface text filtering, configurable URL whitelisting, async Discord webhook logging, and an event API.

---

## 🚀 Requirements & Dependencies

- **Paper 26.3+** (Java **25+**)
- **[VexelCore](https://github.com/ItsMCB/VexelCore)** *(required)*
- **PlaceholderAPI** *(optional — soft dependency)*

---

## 🛡️ Monitored Text Surfaces

CleverText processes and filters player-authored text across all major server surfaces (individually toggleable under `features.handler.surfaces`):

- **Chat** (`CHAT`) — Priority and permission-based formatting with MiniMessage/`&` prefixes, hover tooltips, click actions, staff catch hover diagnostics, and clickable whitelisted hyperlinks.
- **Private Messages & Text Commands** (`COMMAND`) — Monitors configurable commands (`/msg`, `/tell`, `/w`, `/whisper`, `/pm`, `/r`, `/reply`, `/me`, `/say`, etc.) with per-command argument offsets so recipient usernames are skipped while message payloads are filtered or blocked.
- **Signs** (`SIGN`) — Scans both front and back edits per-line and joined across all 4 lines to catch split-line bypasses while preserving line colors/styles.
- **Books** (`BOOK`) — Scans book titles (when signing) and all pages. Blocking cancels without losing unsigned drafts and reports the offending page/title; whitelisted URLs become clickable links when signing.
- **Anvils** (`ANVIL`) — Silently censors or clears output previews during typing (`PrepareAnvilEvent`) without spamming chat/events on every keystroke, then fires `CleverTextCatchEvent` and alerts once when taking/clicking the result.
- **Entity Name Tags** (`ENTITY_NAME`) — Filters pre-renamed Name Tags applied directly to entities.

---

## 🌐 URL Whitelisting (`features.url-filter`)

CleverText includes a host and path-aware URL filter powered by `java.net.URI` (rather than brittle raw regex):

- **Modes (`mode`):**
  - `WHITELIST_REPLACE` *(default)* — Censors unlisted URLs (`replacement: "[link removed]"`) and optionally turns whitelisted URLs into clickable green links.
  - `WHITELIST_BLOCK` — Blocks messages/edits containing any unlisted URL.
  - `ALLOW_ALL` — Allows all URLs (hyperlinking them in chat and signed books if `hyperlink-allowed: true`).
  - `DISABLED` — Disables URL filtering and auto-hyperlinking.
- **Smart Whitelist Matching:**
  - `"youtube.com"` matches `youtube.com` and `www.youtube.com`, never `evil-youtube.com`.
  - `"discord.gg/86qJJHtDgT"` matches only paths starting with `/86qJJHtDgT`, blocking other invites.
- **Bare Domain & IP Detection (`match-bare-domains: true`):** Detects domain names and IPv4 addresses even without `http(s)://` while ignoring version numbers (`1.21.4`) and filenames.
- Moderation text processors take priority over hyperlinking so profanity inside a whitelisted URL is never converted into a clickable link.

---

## 🧩 Text Processors (`features.text-processors`)

Processors support clean `match-type` presets:

- `WORD_CONTAINS` — Matches words containing any entry (`\b\w*...\w*\b`).
- `WORD_WITH_ENDINGS` — Matches entries with common English suffixes (`ing`, `ed`, `er`, `est`, `able`, `ies`, `ty`, `es`, `s`, `y`).
- `WORD_EXACT` — Matches whole words matching any entry.
- `PHRASE` — Matches multi-word phrases cleanly.
- `REGEX` — Compiles a custom `pattern` directly.

Each processor supports `action: REPLACE | BLOCK`, `replacement`, `logging`, `fire-event`, `enabled`, and optional `surfaces` scoping.

---

## 🔔 Webhook Logging (`webhook`)

CleverText can send color-coded Discord webhook embeds for moderation catches and `/clearchat` actions:

- Configure `webhook.url` in `config.yml`, or supply the `CLEVERTEXT_WEBHOOK_URL` environment variable *(overrides `config.yml` so secrets stay out of config files)*.
- **Events (`webhook.events`):** `FILTER_REPLACE`, `FILTER_BLOCK`, `CLEAR_CHAT`
- **Surfaces (`webhook.surfaces`):** Optional list of `TextSurface`s to log (empty logs all surfaces).
- **Safety:** Disables Discord mention parsing (`allowed_mentions: { parse: [] }`) so player input cannot ping `@everyone` or roles, escapes code blocks, truncates long inputs safely, respects `respect-rule-logging`, and skips `/ct test` dry runs.

---

## 📡 Developer Event API (`CleverTextCatchEvent`)

External plugins (e.g., moderation or proxy punishment bridges) can listen to `me.itsmcb.clevertext.api.event.CleverTextCatchEvent`:

- Fires when moderation rules or unlisted URLs catch input (never for allowed hyperlinks).
- Async-aware (`event.isAsynchronous()` accurately reflects async chat vs sync surfaces).
- Exposes `getPlayer()`, `getSurface()` (`TextSurface`), `getOriginalText()`, `getCatches()` (`List<FilterCatch>`), `isBlocked()` / `setBlocked(boolean)`, and `isCancelled()` / `setCancelled(boolean)`.

---

## 📖 Commands

All commands support both **players** and **console**, with Brigadier tab-completion and `/clevertext help [page]`.

### `/clevertext` *(Alias: `/ct`)*

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/clevertext help [page]` | Displays paginated command help. | `clevertext.admin` |
| `/clevertext reload` | Reloads `config.yml`, URL filter, webhook settings, processors, and formats. | `clevertext.admin` |
| `/clevertext debug` | Outputs version, surface states, URL filter stats, webhook status, PAPI status, and counts. | `clevertext.admin` |
| `/clevertext list` | Lists URL whitelist rules, text processors, and message formats. | `clevertext.admin` |
| `/clevertext test <player> [--surface <surface>] <message>` | Dry-run previews formatting, catches, and block status on any surface (without sending webhooks). | `clevertext.admin` |
| `/clevertext clearchat [options]` | Clears chat using configured or specified line count. | `clevertext.clearchat` |

### `/clearchat` *(Alias: `/cc`)*

- `/clearchat` — Clears the default configured lines (`150`) and broadcasts announcement.
- `/clearchat <lines>` or `/clearchat --lines <amount>` (`-l`) — Clears a custom number of lines (`1`–`500`).
- `/clearchat --silent` (`-s`) — Clears chat without broadcasting an announcement.

---

## 🔐 Permissions

| Permission | Default | Description |
| :--- | :---: | :--- |
| `clevertext.admin` | `op` | Grants full access to `/clevertext` subcommands and includes `clevertext.clearchat` and `clevertext.staff`. |
| `clevertext.clearchat` | `op` | Allows using `/clearchat` and `/clevertext clearchat` *(configurable via `features.clearchat.permission`)*. |
| `clevertext.staff` | `op` | Views filter catch hover tooltips and cross-surface filter alerts *(also honors `features.handler.staff-permission`, default `group.staff`)*. |
| `clevertext.bypass` | `false` | Bypasses moderation filters and URL restrictions while keeping whitelisted hyperlinks active. |

---

## ✋ Help
Please join the [Libre Galaxy](https://discord.gg/86qJJHtDgT) Discord server.

## ⚖️ License
CleverText is licensed under the [GNU General Public License v3.0](LICENSE).