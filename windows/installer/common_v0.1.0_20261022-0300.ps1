# =============================================================================
# Tab2Monitor - funzioni comuni degli script del pacchetto (tools\common.ps1)
# Versione: v0.1.0 - 2026-10-22 03:00 (ora locale)
# =============================================================================
# Nel pacchetto questo file si chiama tools\common.ps1 (nome fisso: lo includono
# start.ps1, setup.ps1 e install_tablet.ps1). Solo caratteri ASCII: Windows
# PowerShell 5.1 legge gli script senza BOM come ANSI e rovinerebbe le lettere accentate.
#
# Struttura del pacchetto (cartella radice = $T2Root):
#   Tab2Monitor.cmd, Setup.cmd, Install-Tablet.cmd, LEGGIMI-README.txt
#   python\   Python "embeddable" + psutil       server\  server e supervisore
#   web\      dashboard e pagina /config          sensors\ Tab2Sensors.exe + LibreHardwareMonitorLib
#   apk\      APK per i tablet                    tools\   script PowerShell
#   data\     config.json, fan_config.json, logs (solo copia PORTABILE)
# Copia INSTALLATA (Setup -> Program Files): file "installed.txt" nella radice e dati in
# %ProgramData%\Tab2Monitor (la cartella dei programmi non e' scrivibile dagli utenti).
# =============================================================================

$T2Root = Split-Path -Parent $PSScriptRoot
$T2It = ((Get-UICulture).TwoLetterISOLanguageName -eq 'it')

function T([string]$it, [string]$en) { if ($T2It) { $it } else { $en } }

function Test-T2Admin {
    ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-T2Installed { Test-Path (Join-Path $T2Root 'installed.txt') }

function Get-T2DataDir {
    if (Get-T2Installed) { Join-Path $env:ProgramData 'Tab2Monitor' } else { Join-Path $T2Root 'data' }
}

function Get-T2Python([switch]$Windowless) {
    $exe = if ($Windowless) { 'pythonw.exe' } else { 'python.exe' }
    Join-Path $T2Root "python\$exe"
}

# file con versione piu' alta (stesso criterio del supervisore: ordine del nome)
function Get-T2Latest([string]$dir, [string]$pattern) {
    Get-ChildItem -Path $dir -Filter $pattern -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
}

function Get-T2Config {
    $f = Join-Path (Get-T2DataDir) 'config.json'
    if (Test-Path $f) { try { return (Get-Content $f -Raw | ConvertFrom-Json) } catch { } }
    return [pscustomobject]@{ port = 8090 }
}

function Get-T2Port {
    $c = Get-T2Config
    if ($c.port) { [int]$c.port } else { 8090 }
}

# config.json: legge, cambia una chiave, riscrive (UTF-8; il server accetta anche il BOM)
function Set-T2ConfigValue([string]$key, $value) {
    $f = Join-Path (Get-T2DataDir) 'config.json'
    $c = Get-T2Config
    if ($c.PSObject.Properties[$key]) { $c.$key = $value } else { $c | Add-Member -NotePropertyName $key -NotePropertyValue $value }
    New-Item -ItemType Directory -Force (Split-Path $f) | Out-Null
    $c | ConvertTo-Json | Set-Content -Path $f -Encoding UTF8
}

# IPv4 della scheda di rete usata verso il router (quella da scrivere sul tablet)
function Get-T2LanIp {
    try {
        $r = Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction Stop | Sort-Object RouteMetric | Select-Object -First 1
        $ip = Get-NetIPAddress -InterfaceIndex $r.ifIndex -AddressFamily IPv4 -ErrorAction Stop | Select-Object -First 1
        return $ip.IPAddress
    } catch { return $null }
}

function Find-T2Adb {
    $c = Get-T2Config
    $cands = @()
    if ($c.adb -and $c.adb -notin @('auto', 'off')) { $cands += $c.adb }
    $cands += (Join-Path $T2Root 'adb\platform-tools\adb.exe')
    $cands += (Join-Path $env:LOCALAPPDATA 'Tab2Monitor\platform-tools\adb.exe')
    $w = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($w) { $cands += $w.Source }
    $cands += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
    if ($env:ANDROID_HOME) { $cands += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') }
    foreach ($p in $cands) { if ($p -and (Test-Path $p)) { return $p } }
    return $null
}

function Ask-T2YesNo([string]$question, [bool]$default = $false) {
    $hint = if ($default) { T '[S/n]' '[Y/n]' } else { T '[s/N]' '[y/N]' }
    $a = Read-Host "$question $hint"
    if ([string]::IsNullOrWhiteSpace($a)) { return $default }
    return ($a.Trim().ToLower() -in @('s', 'si', 'y', 'yes'))
}
