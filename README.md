# GLM Web Shell — Android client

**Unofficial client. Not affiliated with Z.ai.**

A stability-first Android wrapper for the GLM web chat (Z.ai): a native Jetpack
Compose UI around a WebView, with an adapter layer that survives site layout
changes (semantic / network / URL strategies, never CSS classes as the only
strategy), per-capability self-tests, graceful degradation, and remotely
updatable signed adapter packages.

Implements [TZ v2.0](./docs/TZ-glm-webview-app.md) in full (multi-module,
Android API 26+).

---

## TL;DR

- Single-Activity Jetpack Compose app.
- Multi-module Gradle: `:app`, `:core:{common,ui,data}`, `:pageengine`,
  `:adapter`, `:features:{chat,diagnostics,history,prompts,settings}`.
- `PageEngine` abstraction — MVP runs on **W1** (System WebView + AndroidX
  WebKit). `W2` (GeckoView) plugs in without rewriting the core (TODO marker).
- Adapter is a **declarative JSON contract**, signed with Ed25519. Remote
  updates, canary channel, kill-switches, auto-rollback, bundled fallback.
- Bridge: `addDocumentStartJavaScript` + `addWebMessageListener`. No
  `addJavascriptInterface`.
- Local history via Room (`Chat`, `Message`, `Prompt`, `AdapterState`,
  `Settings`). Markdown / text / JSON export.
- A `mock-chat.html` asset is shipped so adapter self-tests pass without
  hitting the live site.

---

## How to open

1. Open this folder in **Android Studio Ladybug (2024.2) or newer**.
2. Let Gradle sync. Accept SDK / build-tools install prompts if any.
3. Pick a device or emulator running Android 8.0 (API 26)+.
4. Run the `app` configuration.

> The project uses Gradle 8.10.2 (wrapper included) with the Kotlin DSL and a
> version catalog at `gradle/libs.versions.toml`. No `local.properties` is
> required beyond the Android SDK path that Android Studio writes
> automatically — copy `local.properties.template` to `local.properties` and
> adjust the path if you're building from CLI.

### Build from the command line

```bash
# 1. Make sure you have Android SDK 35 + Build Tools 35.0.0 installed
#    (or let Android Studio install them for you).
#
# 2. Create local.properties pointing at the SDK:
cp local.properties.template local.properties
# edit local.properties: sdk.dir=/path/to/Android/Sdk

# 3. Build the debug APK:
./gradlew assembleDebug

# 4. Run unit tests:
./gradlew testDebugUnitTest

# 5. Lint:
./gradlew lintDebug

# Output APK:
ls -la app/build/outputs/apk/debug/
```

### CI: GitHub Actions

The repository ships a workflow at [`.github/workflows/build.yml`](.github/workflows/build.yml)
that:

1. Checks out the code.
2. Sets up JDK 17 (Temurin).
3. Sets up Android SDK 35 + Build Tools 35.0.0 (via `android-actions/setup-android@v3`).
4. Writes `local.properties` automatically (points at `$ANDROID_HOME`).
5. Caches Gradle dependencies and wrapper.
6. Runs `./gradlew assembleDebug`, `testDebugUnitTest`, and `lintDebug`.
7. Uploads the built APK, test results, and lint reports as artifacts.

To use it: push to `main` / `master` / `develop` (or open a PR). The workflow
triggers automatically and the APK appears under the run's "Artifacts" section.

> No signing config is configured — the CI produces a debug-signed APK. To
> release-sign, add a `keystore.properties` (gitignored) and a `release`
> signing config in `:app/build.gradle.kts`.

---

## Module map (and which ТЗ section it satisfies)

```
glm-web-shell/
├── app/                            # Application, MainActivity, manifest, nav, share-target, QS tile, widget
│   └── ТЗ §6.1.9, §6.1.10
├── core/
│   ├── common/                     # Origin whitelist, AppResult, Dispatchers, Logger
│   │   └── ТЗ §2, §7 (security)
│   ├── ui/                          # Material3 theme, common Compose components, insets
│   │   └── ТЗ §6.1.8 (theme)
│   └── data/                       # Room DB, DAOs, repositories, Hilt module
│       └── ТЗ §9 (data model)
├── pageengine/                     # PageEngine + W1 (WebViewEngine) + bridge + ProxyController + FileChooser + DownloadListener + blob
│   └── ТЗ §4 (W1), §6.1 (MVP), §5.4.1 (bridge), §7 (security)
├── adapter/                        # Capability system, 6 capabilities, strategies, self-test, remote signed packages
│   └── ТЗ §5 (adapter), §5.4 (remote update), §5.3 (health)
├── features/
│   ├── chat/                       # Full-screen WebView shell, prompt insert, copy last code
│   │   └── ТЗ §6.1, §6.3.3, §6.3.4
│   ├── diagnostics/               # Capability health list, report button
│   │   └── ТЗ §5.3
│   ├── history/                   # Chat list, search, pin, tags, export
│   │   └── ТЗ §6.3.1, §6.3.2, §6.3.6
│   ├── prompts/                   # Library, persons, manual insert with visible text
│   │   └── ТЗ §6.3.4
│   └── settings/                  # Proxy, theme, sign-out, clear data, telemetry, biometrics
│       └── ТЗ §6.1.6, §7
└── docs/TZ-glm-webview-app.md    # Source spec
```

---

## Architecture at a glance

```
┌──────────────────────────────────────────────────────────┐
│  Compose UI (features/*)                                 │
│   - chat shell, history, prompts, diagnostics, settings   │
└──────────────────────┬───────────────────────────────────┘
                       │ uses
┌──────────────────────▼───────────────────────────────────┐
│  Core (data + common)                                    │
│   - Room store, AdapterStateRepo, SettingsRepo            │
└──────────────────────┬───────────────────────────────────┘
                       │ queries / commands
        ┌──────────────┼───────────────┐
        ▼              ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐
│ :pageengine  │ │ :adapter     │ │ Remote adapter       │
│ PageEngine   │ │ capabilities │ │ signed JSON, Ed25519 │
│ W1 (WebView) │ │ self-tests   │ │ canary + rollback    │
└──────┬───────┘ └──────┬───────┘ └──────────────────────┘
       │                │
       │ injects bridge │ talks through
       ▼                ▼
┌──────────────────────────────────────────────────────────┐
│ WebView (System) ──► GLM web chat                        │
└──────────────────────────────────────────────────────────┘
```

---

## Stability hierarchy (mandatory, ТЗ §2)

Per capability, strategies are tried **in this order**; first one that passes
its self-test wins, the rest are kept as fallback:

1. **URL routes** — e.g. `nav.openChat(id)` just navigates to a URL.
2. **Network stream observation** — `chat.observe` watches requests the page
   itself issues, parses them as JSON/JSON-lines.
3. **Semantic DOM** — ARIA roles (`role=log`, `role=textbox`), `aria-live`,
   `contenteditable`, structural heuristics.
4. **CSS selectors** — forbidden as the **only** strategy. Allowed only inside
   a strategy as a last-resort heuristic.

Each capability has a `selfTest` that runs on page load. Result is one of
`OK` / `DEGRADED` / `FAILED`. `FAILED` capabilities are hidden from the UI
but the underlying web chat is fully usable (Tier 0).

---

## Bridge

The bridge is intentionally thin:

- `addDocumentStartJavaScript` injects `bridge-boot.js` **before** any page
  script runs, only for origins on the allow-list.
- Two-way messaging via `addWebMessageListener` with `allowedOriginRules`.
- `addJavascriptInterface` is **never** used.
- All incoming messages are validated against a JSON schema with a hard size
  cap (1 MiB per message).

---

## Remote adapters (ТЗ §5.4)

```
adapter-package.json
├── manifest:
│   ├── adapterVersion: Int
│   ├── minAppVersion: String
│   ├── channel: "stable" | "canary"
│   └── generatedAt: ISO-8601
├── capabilities: [Capability...]
└── signature: Ed25519 (Base64) of the canonical JSON
```

- Public key is bundled into the app at build time.
- Fetch cadence: at most once per `N` hours (default 6h), with TTL cache.
- Three consecutive `FAILED` self-tests after a remote update → auto-rollback
  to the previous known-good bundle.
- Bundled `assets/adapter/bundled-adapter.json` is always available as
  fallback, even with no network.

> Crypto is **BouncyCastle 1.78** (`Ed25519Signer`). No remote code is ever
> executed — only declarative strategy rules.

---

## Mock chat page

`app/src/main/assets/mock-chat.html` is a minimal chat-like page exposing
`role="log"`, `role="textbox"`, `aria-live="polite"`, and a POST endpoint
`/api/chat` that returns an SSE-like stream.

Use **Settings → Diagnostics → Run on mock** to exercise every capability
without touching the live site.

---

## Security checklist (ТЗ §7)

- ✅ Allowed origins list; everything else → external browser / Custom Tabs.
- ✅ `file://` and `content://` access disabled from the page; Safe Browsing
  on; mixed content blocked.
- ✅ `setWebContentsDebuggingEnabled(false)` in release builds.
- ✅ Cookies / tokens never logged; `android:allowBackup="false"` and
  `android:fullBackupContent="@xml/backup_rules"` excluding site data.
- ✅ Optional app biometric / PIN lock.
- ✅ Bridge messages validated by size + schema.

---

## Distribution notes (ТЗ §10)

Google Play may reject "wrapper" apps without site-owner permission. Realistic
channels: APK direct, RuStore, F-Droid. For Play, lead with **native
overlays + API mode** rather than the pure wrapper.

---

## What is intentionally stubbed

This source tree compiles in Android Studio but the following are **deferred**
behind `TODO()` markers so the code stays readable:

- GeckoView (W2) implementation — `PageEngine` interface is ready.
- Native API mode (`ТЗ §6.4`) — repository and storage layer exist; HTTP
  client + streaming UI are stubbed.
- Playwright CI fixtures (ТЗ §8.1) — the contract test layout exists under
  `tools/contract-tests/` (not generated here; the daily CI is the project
  owner's responsibility).

These are explicit in `ROADMAP.md` if present.

---

## License & disclaimer

Unofficial client, not affiliated with Z.ai. The app does not bypass
limits, payment, or automation barriers; everything it does is in response
to a user action and is visible to the user.
