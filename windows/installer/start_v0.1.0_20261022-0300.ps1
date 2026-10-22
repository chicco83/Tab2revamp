# =============================================================================
# Tab2Monitor - avvio del server dal pacchetto (tools\start.ps1, lanciato da Tab2Monitor.cmd)
# Versione: v0.1.0 - 2026-10-22 03:00 (ora locale)
# =============================================================================
# 1) se un server Tab2Monitor risponde gia' sulla porta (es. avvio automatico attivo)
#    apre la pagina /config nel browser ed esce;
# 2) chiede i diritti di amministratore (UAC): servono per leggere temperature di CPU e
#    scheda madre e le ventole (driver PawnIO). Se l'utente rifiuta, parte lo stesso
#    senza quei sensori;
# 3) avvia il server in questa finestra (chiuderla = fermare il server).
# Opzioni: -NoElevate (non chiedere l'amministratore), -NoBrowser
# =============================================================================
param([switch]$NoElevate, [switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"
$Host.UI.RawUI.WindowTitle = 'Tab2Monitor'

# file arrivati in uno zip scaricato: togliere il "Mark of the Web" evita blocchi su exe/dll
Get-ChildItem -Path $T2Root -Recurse -File -ErrorAction SilentlyContinue | Unblock-File -ErrorAction SilentlyContinue

$port = Get-T2Port
try {
    $info = Invoke-RestMethod "http://127.0.0.1:$port/api/info" -TimeoutSec 2
    Write-Host (T "Tab2Monitor e' gia' attivo su questo PC ($($info.name), $($info.ver))." "Tab2Monitor is already running on this PC ($($info.name), $($info.ver)).")
    if (-not $NoBrowser) { Start-Process "http://localhost:$port/config" }
    exit 0
} catch { }

if (-not (Test-T2Admin) -and -not $NoElevate) {
    try {
        $args2 = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"", '-NoElevate')
        if ($NoBrowser) { $args2 += '-NoBrowser' }
        Start-Process powershell -Verb RunAs -ArgumentList $args2 -ErrorAction Stop | Out-Null
        exit 0
    } catch {
        Write-Host (T 'Amministratore non concesso: avvio senza i sensori di CPU e scheda madre.' 'Administrator not granted: starting without CPU and motherboard sensors.') -ForegroundColor Yellow
    }
}

$py = Get-T2Python
$srv = Get-T2Latest (Join-Path $T2Root 'server') 'tab2monitor_server_v*.py'
if (-not (Test-Path $py) -or -not $srv) { Write-Host (T 'Pacchetto incompleto (python\ o server\ mancanti).' 'Incomplete package (python\ or server\ missing).') -ForegroundColor Red; exit 1 }
$data = Get-T2DataDir
New-Item -ItemType Directory -Force $data | Out-Null

$ip = Get-T2LanIp
Write-Host ''
Write-Host '  Tab2Monitor' -ForegroundColor Cyan
Write-Host ('  ' + (T 'Pagina di configurazione sul PC: ' 'Configuration page on the PC: ') + "http://localhost:$port/config")
if ($ip) {
    Write-Host ('  ' + (T 'Indirizzo per il tablet (se la ricerca automatica non lo trova): ' 'Address for the tablet (if the automatic search does not find it): ') + "http://${ip}:$port")
    Write-Host ('  ' + (T 'APK da installare dal browser del tablet: ' 'APK to install from the tablet browser: ') + "http://${ip}:$port/apk")
}
try {
    $prof = Get-NetConnectionProfile -ErrorAction Stop | Where-Object { $_.NetworkCategory -eq 'Public' }
    if ($prof) {
        Write-Host ('  ' + (T "ATTENZIONE: la rete '$($prof[0].Name)' e' 'Pubblica': il firewall blocca il tablet. Usare Setup.cmd (voce firewall / rete privata)." "WARNING: network '$($prof[0].Name)' is 'Public': the firewall blocks the tablet. Use Setup.cmd (firewall / private network).")) -ForegroundColor Yellow
    }
} catch { }
Write-Host ('  ' + (T 'Chiudere questa finestra per fermare il server.' 'Close this window to stop the server.'))
Write-Host ''
if (-not $NoBrowser) {
    Start-Job -ScriptBlock { param($p) Start-Sleep 4; Start-Process "http://localhost:$p/config" } -ArgumentList $port | Out-Null
}
& $py -u $srv.FullName --data $data
