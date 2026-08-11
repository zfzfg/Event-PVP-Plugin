<#
.SYNOPSIS
    Skaliert die Item-Icons aus item-assets/ auf eine web-taugliche Groesse und
    erzeugt das Manifest, aus dem das Web-Panel seine Item-Liste kennt.

.DESCRIPTION
    Quelle : <repo>/item-assets/*.png            (256x256, Dateiname = Bukkit-Material-Enum)
    Ziel   : src/main/resources/web/item-assets/ (64x64, wird ins JAR gepackt)
    Manifest: src/main/resources/web/item-assets/_index.json

    Die Icons sind Pixelart. Deshalb wird ausschliesslich NearestNeighbor interpoliert -
    bilineares Glaetten macht 16px-Vorlagen matschig. PixelOffsetMode/SmoothingMode
    werden ebenfalls hart gesetzt, weil GDI+ sonst je nach Windows-Version halbe Pixel
    an den Kanten einblendet.

    Das Skript ist idempotent: unveraenderte Dateien werden uebersprungen, solange
    -Force nicht gesetzt ist. Nach einem Asset-Update also einfach erneut laufen lassen.

.EXAMPLE
    pwsh -File tools/scale-item-assets.ps1
    pwsh -File tools/scale-item-assets.ps1 -Size 64 -Force
#>
[CmdletBinding()]
param(
    # Kantenlaenge der erzeugten Icons in Pixeln.
    [ValidateRange(16, 256)]
    [int]$Size = 64,

    # Bereits vorhandene Zieldateien neu erzeugen statt ueberspringen.
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$repoRoot   = Split-Path -Parent $PSScriptRoot
$sourceDir  = Join-Path $repoRoot 'item-assets'
$targetDir  = Join-Path $repoRoot 'src/main/resources/web/item-assets'
$manifest   = Join-Path $targetDir '_index.json'

if (-not (Test-Path $sourceDir)) {
    throw "Quellordner nicht gefunden: $sourceDir"
}
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

$sourceFiles = Get-ChildItem -Path $sourceDir -Filter '*.png' | Sort-Object Name
Write-Host "Quelle : $($sourceFiles.Count) PNG in $sourceDir"
Write-Host "Ziel   : $targetDir ($Size x $Size)"

$written = 0
$skipped = 0
$failed  = 0
$names   = New-Object System.Collections.Generic.List[string]

foreach ($file in $sourceFiles) {
    # Dateiname ist der Material-Enum-Name; genau so fragt das Panel das Icon ab.
    $name       = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    $targetPath = Join-Path $targetDir $file.Name

    if ((-not $Force) -and (Test-Path $targetPath)) {
        $names.Add($name)
        $skipped++
        continue
    }

    $source = $null
    $bitmap = $null
    $canvas = $null
    try {
        $source = [System.Drawing.Image]::FromFile($file.FullName)
        $bitmap = New-Object System.Drawing.Bitmap($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $canvas = [System.Drawing.Graphics]::FromImage($bitmap)

        # Pixelart: hart skalieren, nichts glaetten, keine halben Randpixel.
        $canvas.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $canvas.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $canvas.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::None
        $canvas.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighSpeed
        $canvas.CompositingMode    = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy

        $canvas.DrawImage($source, (New-Object System.Drawing.Rectangle(0, 0, $Size, $Size)))
        $canvas.Dispose(); $canvas = $null
        $source.Dispose(); $source = $null

        $bitmap.Save($targetPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $names.Add($name)
        $written++
    } catch {
        Write-Warning "Uebersprungen (Fehler): $($file.Name) - $($_.Exception.Message)"
        $failed++
    } finally {
        if ($canvas) { $canvas.Dispose() }
        if ($bitmap) { $bitmap.Dispose() }
        if ($source) { $source.Dispose() }
    }
}

# Verwaiste Zieldateien entfernen, deren Quelle verschwunden ist - sonst schleppt
# das JAR Icons fuer Materials mit, die es laengst nicht mehr gibt.
$sourceNames = [System.Collections.Generic.HashSet[string]]::new(
    [string[]]($sourceFiles | ForEach-Object { $_.Name }),
    [System.StringComparer]::OrdinalIgnoreCase)
$removed = 0
foreach ($existing in Get-ChildItem -Path $targetDir -Filter '*.png') {
    if (-not $sourceNames.Contains($existing.Name)) {
        Remove-Item $existing.FullName -Force
        $removed++
    }
}

$payload = [ordered]@{
    generated = (Get-Date).ToString('yyyy-MM-dd')
    size      = $Size
    count     = $names.Count
    items     = @($names)
}
# -Compress: das Manifest wird bei jedem Panel-Start geladen, Einrueckung waere hier
# reiner Ballast (~40 KB gegenueber ~25 KB).
$payload | ConvertTo-Json -Depth 3 -Compress | Set-Content -Path $manifest -Encoding UTF8

$totalBytes = (Get-ChildItem -Path $targetDir -Filter '*.png' | Measure-Object Length -Sum).Sum
Write-Host ''
Write-Host ("Geschrieben: {0}  uebersprungen: {1}  entfernt: {2}  fehlgeschlagen: {3}" -f $written, $skipped, $removed, $failed)
Write-Host ("Manifest   : {0} ({1} Items)" -f $manifest, $names.Count)
Write-Host ("Zielgroesse: {0:N1} MB" -f ($totalBytes / 1MB))
