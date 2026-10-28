# Changelog 1.1.2

## [1.1.2] - 2026-10-28

Security and reliability improvements for stakes, payouts, world operations and the web panel. Existing external web binding defaults are preserved. No dependency upgrades were made.

### Stakes and payouts

- Validate item quantities, kits, players and money limits before funding a match. GUI stakes are removed once and are not deducted again at match start.
- Check Vault withdrawal and deposit responses; compensate only confirmed withdrawals. Reject non-finite amounts and overflowing combined pots.
- Persist payout components and stable receipts before delivery. Preserve inventory overflow and offline claims, and deduplicate completed payouts across restarts.
- Keep uncertain external operations for administrator review instead of automatically repeating them. Add `/eventpvp payouts list|inspect|resolve` with logged decisions.
- Migrate legacy payout YAML with a backup, preserve malformed records for inspection, and use atomic file replacement for journals.

### World operations

- Reserve overlapping world paths and reject conflicting jobs.
- Restore archives into exclusive staging directories before publishing without overwriting an existing target. Clean up only directories owned by the job.
- Reject unsafe filesystem links, check player evacuation and world unload results, and limit archive entry counts and actual extracted bytes.
- Bound world I/O concurrency, retain running jobs until completion, and record interrupted jobs during shutdown.

### Web panel and configuration

- Escape configuration values in rendered HTML and replace dynamic inline handlers with registered callbacks.
- Consume login tokens atomically, coordinate session expiry, use Secure cookies for configured HTTPS URLs, and require POST for logout.
- Bound request queues and expensive operations; validate JSON types and enforce request deadlines.
- Coordinate configuration writes, apply Bukkit changes on the main thread, and detect stale saves through revisions with HTTP 409 conflicts.
- Report saved, applied, deferred and failed outcomes. Defer gameplay reloads while LiveTrade sessions are active; report partial failures on manual reloads.

### Operations and verification

- Isolate shutdown steps so an earlier failure cannot skip later journal and return-state saves.
- Save statistics through an ordered writer using immutable snapshots; expose payout, world-job and persistence diagnostics.
- Bound texture import dimensions and extracted data, stage new textures, and retain existing textures after a failed import.
- Add regression coverage for recovery, partial payments, GUI stake ownership, conflicting world jobs, configuration writes, authentication races, texture limits and shutdown isolation.
- Add `tools/verify.ps1` with an explicit JDK 25-or-newer check and JavaScript checks. Build with JDK 26 using `mvn clean package`; 447 Java tests and 3 separate web tests pass.

### Migration and release validation

See [Hardening 1.1.2](../HARDENING_1.1.2.md) for limits, recovery commands and rollback precautions. Back up plugin data before the first start: the old plugin cannot safely consume the new payout ledger after migration and further payouts.

Real-server integration with Purpur, Vault economy, InventoryBackup and Multiverse remains pending. Unit and MockBukkit tests do not replace restart-recovery, inventory-restore and filesystem-link checks on the deployment platform. Exactly-once external execution across a process crash is not guaranteed; uncertain outcomes require administrator review.
