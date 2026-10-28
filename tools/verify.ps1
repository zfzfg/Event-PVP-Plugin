param([string]$JavaHome, [switch]$BrowserTests)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -and $env:JAVA_HOME) { $JavaHome = $env:JAVA_HOME }
if ($JavaHome) {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    $env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
}
$javaVersion = (& java -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "(\d+)' -or [int]$Matches[1] -lt 25) {
    throw 'JDK 25 or newer is required. Pass -JavaHome with the installed JDK directory.'
}
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $taskRoot
try {
    & node --check src/main/resources/web/panel-runtime.js
    if ($LASTEXITCODE -ne 0) { throw 'panel-runtime.js syntax check failed' }
    & node --check src/main/resources/web/items.js
    if ($LASTEXITCODE -ne 0) { throw 'items.js syntax check failed' }
    & node --check src/main/resources/web/app.js
    if ($LASTEXITCODE -ne 0) { throw 'app.js syntax check failed' }
    & node --check src/main/resources/web/editors.js
    if ($LASTEXITCODE -ne 0) { throw 'editors.js syntax check failed' }
    & node --test tools/web-safety.test.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Web safety tests failed' }
    if ($BrowserTests) {
        & npm ci --prefix tools/web-tests
        if ($LASTEXITCODE -ne 0) { throw 'Browser test dependencies failed' }
        & npx --prefix tools/web-tests playwright install chromium
        if ($LASTEXITCODE -ne 0) { throw 'Chromium installation failed' }
        & npm test --prefix tools/web-tests
        if ($LASTEXITCODE -ne 0) { throw 'Browser regression tests failed' }
    }
    & mvn -B verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed' }
} finally { Pop-Location }
