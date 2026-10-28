# Webpanel regression tests

Run from the repository root with Node.js and npm:

```powershell
npm ci --prefix tools/web-tests
npx --prefix tools/web-tests playwright install chromium
npm test --prefix tools/web-tests
```

Or include these tests in the complete JDK 25+ verification:

```powershell
./tools/verify.ps1 -JavaHome 'C:/path/to/jdk-25' -BrowserTests
```

The Chromium tests serve the real HTML, CSS and scripts through intercepted requests
at `http://panel.test`. API replies are fixtures: no Minecraft server, production
backup, login token or external service is modified. External font loading is blocked;
item textures use the repository assets where available and otherwise exercise the
existing placeholder fallback.

Coverage includes inventory geometry before/after opening the equipment editor,
all panel sections at a narrow viewport, 360/768/1024/1440 pixel layouts, 200% CSS
zoom, full/empty inventories, normalization, request races, partial saves, edits
while saving, revision conflicts, retries, session expiry, duplicate mutations,
dialog focus, tooltips and translation completeness. Screenshots are written to
`target/panel-*.png`. These tests do not replace a live-server integration check
for actual restore operations.
