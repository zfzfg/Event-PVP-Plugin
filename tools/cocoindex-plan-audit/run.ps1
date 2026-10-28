param([string]$JavaHome)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$taskReportPath = Join-Path $taskRoot 'target\surefire-reports\TEST-de.zfzfg.core.web.WebAuthManagerTest.xml'
if (-not (Test-Path -LiteralPath $taskReportPath)) {
    throw 'First run mvn test with the required JDK to build classes and create the dependency report.'
}
[xml]$taskReport = Get-Content -LiteralPath $taskReportPath -Raw
$taskClasspath = ($taskReport.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
if (-not $JavaHome) {
    $JavaHome = ($taskReport.testsuite.properties.property | Where-Object name -eq 'java.home').value
}
$taskOutput = Join-Path $taskRoot 'target\cocoindex-plan-audit'
New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null
$taskSource = Join-Path $PSScriptRoot 'PlanAuditProbe.java'
$taskJavacArgs = @('-encoding', 'UTF-8', '-cp', ('"' + $taskClasspath.Replace('\', '/') + '"'), '-d', ('"' + $taskOutput.Replace('\', '/') + '"'), ('"' + $taskSource.Replace('\', '/') + '"'))
$taskArgfile = Join-Path $taskOutput 'javac.args'
[System.IO.File]::WriteAllLines($taskArgfile, $taskJavacArgs, [System.Text.UTF8Encoding]::new($false))
& (Join-Path $JavaHome 'bin\javac.exe') "@$taskArgfile"
if ($LASTEXITCODE -ne 0) { throw 'Audit probe compilation failed.' }
$taskRuntimeArgs = @('-XX:+EnableDynamicAgentLoading', '-cp', ('"' + ($taskOutput + ';' + $taskClasspath).Replace('\', '/') + '"'), 'PlanAuditProbe')
$taskRuntimeArgfile = Join-Path $taskOutput 'java.args'
[System.IO.File]::WriteAllLines($taskRuntimeArgfile, $taskRuntimeArgs, [System.Text.UTF8Encoding]::new($false))
Push-Location $taskRoot
try {
    & (Join-Path $JavaHome 'bin\java.exe') "@$taskRuntimeArgfile" 2>&1 | Tee-Object -FilePath (Join-Path $taskOutput 'results.txt')
    if ($LASTEXITCODE -ne 0) { throw 'An audit assertion failed; inspect target/cocoindex-plan-audit/results.txt.' }
} finally {
    Pop-Location
}
