# =============================================================================
# Tab2Monitor - assemblaggio del pacchetto portabile per altri PC (sul PC di sviluppo)
# Versione: v0.1.0 - 2026-10-22 03:00 (ora locale)
# =============================================================================
# Risultato: %LOCALAPPDATA%\Tab2Monitor-build\dist\Tab2Monitor_v<ver>\ e Tab2Monitor_v<ver>.zip
# (fuori dal repository: su Google Drive la sincronizzazione blocca python.exe durante la
# ricostruzione - errore "accesso negato" visto il 2026-10-22 - e caricherebbe 25 MB a ogni build). Contenuto e motivi: PIANO_INSTALLER_v0.2.0_*.md.
#
# Ingredienti (nessuno nel repository):
#   - Python "embeddable" ufficiale (python.org) nella cache:
#       %LOCALAPPDATA%\Tab2Monitor-build\cache\python-<ver>-embed-amd64.zip
#     scelto della stessa versione del Python del PC di sviluppo (3.13.15), cosi' il
#     psutil gia' installato qui (estensione compilata per 3.13) funziona identico;
#   - psutil: copiato dal site-packages del Python del PC di sviluppo (niente download);
#   - Tab2Sensors.exe + LibreHardwareMonitorLib.dll (LHM 0.9.6, MPL-2.0) dalla cartella
#     windows\lhm\LibreHardwareMonitor_v* del repository; se in questo checkout manca
#     (worktree), dal checkout principale indicato con -LhmDir;
#   - APK: quella con versione piu' alta in android\tab2monitor_app\dist (build prima).
#   - PawnIO NON incluso (licenza dell'installer non dichiarata): lo scarica il Setup.
#
# Uso (PowerShell):
#   powershell -ExecutionPolicy Bypass -File windows\installer\build_portable_v0.1.0_20261022-0300.ps1
#     [-PyVersion 3.13.15] [-LhmDir <cartella LHM>] [-NoZip]
# =============================================================================
param(
    [string]$PackageVersion = '0.1.0',
    [string]$PyVersion = '3.13.15',
    [string]$LhmDir = '',
    [string]$Cache = (Join-Path $env:LOCALAPPDATA 'Tab2Monitor-build\cache'),
    [string]$DistDir = (Join-Path $env:LOCALAPPDATA 'Tab2Monitor-build\dist'),
    [switch]$NoZip
)
$ErrorActionPreference = 'Stop'
$Here = $PSScriptRoot
$Repo = (Resolve-Path (Join-Path $Here '..\..')).Path
$Dist = $DistDir
$Name = "Tab2Monitor_v$PackageVersion"
$Out = Join-Path $Dist $Name

function Latest([string]$dir, [string]$pattern) {
    $f = Get-ChildItem -Path $dir -Filter $pattern -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
    if (-not $f) { throw "manca $pattern in $dir" }
    return $f
}
function Crlf([string]$src, [string]$dst) {   # .cmd/.txt con fine riga Windows
    $t = [IO.File]::ReadAllText($src) -replace "`r?`n", "`r`n"
    [IO.File]::WriteAllText($dst, $t, (New-Object Text.UTF8Encoding($false)))
}

Write-Host "Pacchetto $Name -> $Out"
if (Test-Path $Out) { Remove-Item $Out -Recurse -Force }
foreach ($d in 'python', 'server', 'web', 'sensors', 'apk', 'tools', 'data') { New-Item -ItemType Directory -Force (Join-Path $Out $d) | Out-Null }

# --- 1. Python embeddable + psutil ---------------------------------------------
$pyZip = Join-Path $Cache "python-$PyVersion-embed-amd64.zip"
if (-not (Test-Path $pyZip)) { throw "manca $pyZip (scaricare da https://www.python.org/ftp/python/$PyVersion/)" }
Expand-Archive $pyZip -DestinationPath (Join-Path $Out 'python') -Force
$pth = Get-ChildItem (Join-Path $Out 'python') -Filter 'python3*._pth' | Select-Object -First 1
# ._pth: senza "Lib\site-packages" il Python embeddable non vede psutil
$lines = Get-Content $pth.FullName | Where-Object { $_ -notmatch '^#?import site' }
$lines += 'Lib\site-packages'
$lines += 'import site'
Set-Content -Path $pth.FullName -Value $lines -Encoding ASCII
$sp = (& python -c "import psutil,os;print(os.path.dirname(os.path.dirname(psutil.__file__)))").Trim()
$psv = (& python -c "import psutil;print(psutil.__version__)").Trim()
$dstSp = Join-Path $Out 'python\Lib\site-packages'
New-Item -ItemType Directory -Force $dstSp | Out-Null
Copy-Item (Join-Path $sp 'psutil') $dstSp -Recurse
Get-ChildItem $sp -Directory -Filter "psutil-$psv*.dist-info" | Copy-Item -Destination $dstSp -Recurse
Get-ChildItem $dstSp -Recurse -Directory -Filter '__pycache__' | Remove-Item -Recurse -Force
$chk = & (Join-Path $Out 'python\python.exe') -c "import sys,psutil;print(sys.version.split()[0], psutil.__version__)"
Write-Host "  python embeddable + psutil: $chk"

# --- 2. server, pagine, script -------------------------------------------------
$agent = Join-Path $Repo 'windows\agent'
Copy-Item (Latest $agent 'tab2monitor_server_v*.py').FullName (Join-Path $Out 'server')
Copy-Item (Latest $agent 'tab2monitor_service_v*.py').FullName (Join-Path $Out 'server')
Copy-Item (Latest $agent 'config_page_v*.html').FullName (Join-Path $Out 'web')
Copy-Item (Latest (Join-Path $Repo 'dashboard') 'dashboard_*.html').FullName (Join-Path $Out 'web')
foreach ($n in 'common', 'start', 'setup', 'install_tablet') {
    Copy-Item (Latest $Here "${n}_v*.ps1").FullName (Join-Path $Out "tools\$n.ps1")
}
Crlf (Latest $Here 'launcher_Tab2Monitor_v*.cmd').FullName (Join-Path $Out 'Tab2Monitor.cmd')
Crlf (Latest $Here 'launcher_Setup_v*.cmd').FullName (Join-Path $Out 'Setup.cmd')
Crlf (Latest $Here 'launcher_Install-Tablet_v*.cmd').FullName (Join-Path $Out 'Install-Tablet.cmd')
Crlf (Latest $Here 'LEGGIMI-README_v*.txt').FullName (Join-Path $Out 'LEGGIMI-README.txt')
Crlf (Latest $Here 'THIRD-PARTY_v*.txt').FullName (Join-Path $Out 'THIRD-PARTY.txt')
# configurazione di partenza della copia portabile (decisioni utente: Claude spento)
'{' + "`r`n" + '  "port": 8090,' + "`r`n" + '  "claude": false,' + "`r`n" + '  "adb": "auto",' + "`r`n" +
    '  "sensors": true,' + "`r`n" + '  "discovery": true' + "`r`n" + '}' | Set-Content (Join-Path $Out 'data\config.json') -Encoding ASCII

# --- 3. sensori (Tab2Sensors + LibreHardwareMonitorLib) ---------------------------
if (-not $LhmDir) {
    $c = @(Get-ChildItem (Join-Path $Repo 'windows\lhm') -Directory -Filter 'LibreHardwareMonitor_v*' -ErrorAction SilentlyContinue) +
         @(Get-ChildItem (Join-Path $Repo '..\tab2revamp\windows\lhm') -Directory -Filter 'LibreHardwareMonitor_v*' -ErrorAction SilentlyContinue)
    $c = $c | Where-Object { Test-Path (Join-Path $_.FullName 'Tab2Sensors.exe') } | Sort-Object Name | Select-Object -Last 1
    if ($c) { $LhmDir = $c.FullName }
}
if (-not $LhmDir -or -not (Test-Path (Join-Path $LhmDir 'Tab2Sensors.exe'))) { throw "Tab2Sensors.exe non trovato (usare -LhmDir; build: windows\agent\sensors\build_tab2sensors_*.sh)" }
# solo la libreria e le sue dipendenze: niente interfaccia grafica di LHM (OxyPlot, Aga.Controls, TaskScheduler)
Get-ChildItem $LhmDir -File | Where-Object {
    ($_.Extension -eq '.dll' -and $_.Name -notmatch '^(OxyPlot|Aga\.Controls|Microsoft\.Win32\.TaskScheduler)') -or
    $_.Name -in 'Tab2Sensors.exe', 'Tab2Sensors.exe.config'
} | Copy-Item -Destination (Join-Path $Out 'sensors')
Write-Host "  sensori da $LhmDir : $(@(Get-ChildItem (Join-Path $Out 'sensors')).Count) file"

# --- 4. APK ---------------------------------------------------------------------
$apk = Get-ChildItem (Join-Path $Repo 'android\tab2monitor_app\dist') -Filter 'Tab2Monitor_v*.apk' |
    Sort-Object { [version]($_.BaseName -replace '^Tab2Monitor_v', '') } | Select-Object -Last 1
if (-not $apk) { throw 'APK non trovata: eseguire prima android\tab2monitor_app\build_tab2monitor_*.sh' }
Copy-Item $apk.FullName (Join-Path $Out 'apk')
Write-Host "  APK: $($apk.Name)"

# --- 5. versione, prova di avvio, zip -------------------------------------------
Set-Content (Join-Path $Out 'VERSION.txt') -Encoding ASCII -Value @(
    "Tab2Monitor $PackageVersion - $(Get-Date -Format 'yyyy-MM-dd HH:mm')",
    "server: $((Latest $agent 'tab2monitor_server_v*.py').Name)",
    "apk: $($apk.Name)", "python: $chk", "sensors: $(Split-Path $LhmDir -Leaf)")
$size = [math]::Round(((Get-ChildItem $Out -Recurse -File | Measure-Object Length -Sum).Sum / 1MB), 1)
Write-Host "  dimensione: $size MB"
if (-not $NoZip) {
    $zip = Join-Path $Dist "$Name.zip"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    Compress-Archive -Path $Out -DestinationPath $zip -CompressionLevel Optimal
    Write-Host "  zip: $zip ($([math]::Round((Get-Item $zip).Length / 1MB, 1)) MB)"
}
Write-Host 'OK'
