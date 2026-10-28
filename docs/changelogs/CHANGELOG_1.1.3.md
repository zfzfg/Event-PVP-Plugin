# Changelog 1.1.3

## [1.1.3] - 2026-10-28

Web panel layout and reliability improvements. The existing dark/green design, public API endpoints and configuration formats are preserved.

### Inventory display

- Fix inconsistent slot sizes, collapsing empty armor slots and overflowing inventory grids. Load shared inventory styles statically and scope equipment-editor styles so visiting the editor no longer changes backup previews.
- Align the main inventory and hotbar to nine equal columns; keep horizontal scrolling inside the inventory on narrow screens.
- Position stack counts and enchantment indicators without changing slot dimensions. Normalize inventory data before rendering and counting, excluding AIR, invalid slots and duplicate entries.
- Show the equipment export action once. Clear previous backup data while loading and enable preview actions only for the current loaded backup.
- Keep item tooltips within the viewport and support focus, tapping and Escape dismissal.

### Loading, saving and sessions

- Cancel superseded player and backup requests and ignore stale responses.
- Bind restore and delete operations to their original player and backup; prevent duplicate submissions while requests are pending.
- Track configuration loading per category, show failures and offer retries without replacing edits in other categories. Do not save categories that failed to load.
- Save immutable snapshots and update baselines only for confirmed successes. Preserve edits made during a save and handle partial success per category.
- Present revision conflicts after all saves finish; reload only after confirmation to discard unsaved changes.
- Share request handling, bound read request duration, clean up reads and status polling on logout, and prevent old write responses from expiring a new login session. Writes are not retried automatically.

### Layout, accessibility and localization

- Improve wrapping of headers, buttons and notifications; keep mobile navigation before page content.
- Constrain dialog height, manage initial focus and focus return, trap keyboard focus and support Escape through existing close handlers.
- Preserve open editor inputs during language changes and add shared panel messages in all seven supported languages.

### Verification

- Add 15 Chromium regression tests with simulated API responses covering layout, editor visit order, 360/768/1024/1440 pixel viewports, 200% CSS zoom, request races, partial saves, revision conflicts, session lifecycle, dialogs and localization.
- Extend `tools/verify.ps1` with syntax checks for the shared runtime and item helpers and optional browser checks through `-BrowserTests`.
- Verification passed: 447 Java tests, 15 browser tests and 3 web safety tests. Build and verify the 1.1.3 JAR with the final web resources.
- Browser tests use fixtures; live-server inventory restoration was not exercised. No new configuration migration is introduced by this release.
