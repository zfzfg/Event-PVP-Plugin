# Changelog 1.1.1

## [1.1.1] - 2026-09-23

Maintenance release. It adds **Minecraft 26.3** support, closes several gaps in the web panel's security, updates the build toolchain and corrects the compatibility documentation. You don't need to migrate any config. Existing `web-config.yml` files keep working, and the new options default to the old behaviour.

---

### 🌟 Release Highlights

- **Minecraft 26.3 support:** One JAR now runs on Purpur/Paper **26.2 and 26.3**.
- **Hardened web panel:** Requests from foreign origins are rejected. There are now limits on request size and login attempts, and the IP allowlist is actually enforced.
- **Thread-safe web actions:** Reloads and other actions triggered from the panel now run their Bukkit work on the server main thread.
- **Modernized build:** Current Maven plugins and test libraries, and the old build workarounds are gone.

---

### 🚀 Detailed Changes

#### 1. Minecraft 26.3
- The plugin compiles without warnings against `purpur-api 26.3`. The API diff between 26.2 and 26.3 only adds things: nothing was removed from `Material`, `EntityType`, `Sound`, `ItemType`, `Enchantment` or `PotionEffectType`.
- Tested on Purpur 26.3 (build 2639): plugin start, `/eventpvp reload` and the web panel.
- `api-version` stays at `26.2`, so the same JAR loads on 26.2 and 26.3 servers.
- New Maven profile `mc263` builds against the 26.3 API: `mvn -Pmc263 package`.
- **Web panel icons:** 8 explorer-map icons renamed to their final 26.3 item names: `ABANDONED_CAMP_MAP`, `BURIED_ANCIENT_CITY_MAP`, `BURIED_MINESHAFT_MAP`, `BURIED_TRIAL_CHAMBERS_MAP`, `JUNGLE_PYRAMID_MAP`, `OCEAN_MONUMENT_MAP`, `SWAMP_HUT_MAP`, `WOODLAND_MANSION_MAP`.
- The fallback `items.texture-source` now points at the `26.3` asset branch instead of `1.21`.

#### 2. Web Panel Security
- **No more CORS origin mirroring:** Before, the server reflected every `Origin` back with `Access-Control-Allow-Credentials: true`. `SameSite=Strict` ignores ports, so a page on another port of the same host (a map viewer, for example) could have used the admin cookie. Now only same-origin requests and requests from the configured `public-url` are accepted. Anything else gets **403**.
- **Request size limit:** Request bodies are capped at 2 MB. Larger requests get **413**. Line breaks in request bodies are also kept intact now.
- **Login rate limit:** At most 10 login attempts per minute per IP. Further attempts get **429**.
- **`security.allowed-ips` is enforced:** The option was already documented but never checked. An empty list still means every IP is allowed.
- **`security.required-permission` is honoured:** The permission used to be hard-coded to `eventpvp.admin.web`.
- **New option `security.bind-session-to-ip`** (default `false`): a session is only valid from the IP it was created on.
- The HTTP server uses a small thread pool (4 threads) instead of a single thread, so one slow request no longer blocks the whole panel.

#### 3. Thread Safety
- `/api/reload`, switching the inventory provider, saving `config.yml` and looking up players by name all run their Bukkit work on the server main thread, with a 30 s timeout. Before, they ran on the HTTP thread and competed with the server tick.

#### 4. Build & Tooling
- `maven.compiler.release=25` replaces separate `source`/`target`.
- Updated: maven-compiler-plugin 3.15.0, maven-shade-plugin 3.6.2, maven-surefire-plugin 3.5.6, JUnit 5.14.4, Mockito 5.23.0, AssertJ 3.27.7, PlaceholderAPI 2.12.3. The PlaceholderAPI repository URL is updated as well.
- Removed:
  - the Groovy manifest workaround
  - the stray `paper-api 1.21-R0.1-SNAPSHOT` test dependency
  - the dead Sonatype and Spigot repositories
  - the `-Dnet.bytebuddy.experimental=true` flag
- `dependency-reduced-pom.xml` is no longer tracked by Git.

#### 5. Logging
- A broken entry in `event_stats.yml` is logged with its key instead of being skipped silently.
- `MultiverseHelper` writes a debug message when resolving a clone source fails, instead of swallowing the error.

#### 6. Documentation
- **Compatibility matrix corrected:** Paper/Purpur 1.21.x and 1.20.x can't load a plugin with `api-version: 26.2`. On top of that, the plugin needs Java 25 bytecode support and Adventure 5. Supported now: **26.2 and 26.3**.
- **Adventure note corrected:** Adventure 5.2.0 is shaded but **not relocated**. Relocating it would break Paper methods that take a `Component`. The 1.1.0 notes said otherwise.
- README, README_DE and the Modrinth description now list the correct server versions and Java 25.
- Added notes on running the panel behind a reverse proxy with HTTPS, and on `allowed-ips` and `bind-session-to-ip`.

---

### 🧪 Quality

| Check | Result |
|---|---|
| Unit, integration and MockBukkit tests | **370 / 370 passing** (10 new) |
| i18n audit (D1–D11, console, self-tests) | **clean** |
| Compilation against Purpur 26.2 and 26.3 | **0 warnings** |

---

### ⚙️ Technical Specifications

| Component | Version |
|---|---|
| **Plugin Version** | `1.1.1` |
| **Target API** | `purpur-api:26.2.build.2618-stable` (profile `mc263`: `26.3.build.2639-experimental`) |
| **api-version** | `26.2` |
| **Supported Server Engines** | Purpur / Paper / Pufferfish 26.2 and 26.3, Spigot 26.2+ |
| **Java** | 25+ |
| **Kyori Adventure** | `5.2.0` (shaded, not relocated) |
