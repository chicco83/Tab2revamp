# =============================================================================
# Tab2Monitor - avvio automatico del server (attivita' pianificata)
# Versione: v0.2.0 - 2026-10-22 00:55 (ora locale)
# =============================================================================
# Crea (o ricrea) l'attivita' "Tab2Monitor":
#   - si avvia all'accesso dell'utente corrente, con privilegi elevati e SENZA
#     richiesta UAC (serve admin per i sensori CPU/scheda madre via PawnIO);
#   - esegue pythonw.exe tab2monitor_service_v*.py (nessuna finestra), che avvia
#     e sorveglia il server; nessun limite di durata, anche a batteria;
#   - perche' all'accesso e non all'avvio del PC: il progetto sta su Google Drive
#     (G:), montato solo dopo l'accesso; un'attivita' di sistema all'avvio non lo
#     vedrebbe (servirebbe una copia locale dei file, scartata per non spargere file).
# Uso (PowerShell come amministratore, oppure doppio clic sul .cmd omonimo):
#   powershell -ExecutionPolicy Bypass -File install_tab2monitor_service_v0.2.0_20261022-0055.ps1 [-Remove] [-NoStart] [-ServerArgs "--gpu 9070"]
# Rollback: lo stesso script con -Remove (oppure: schtasks /Delete /TN Tab2Monitor /F)
# =============================================================================
param([switch]$Remove, [switch]$NoStart, [string]$ServerArgs = "")

$ErrorActionPreference = "Stop"
$TaskName = "Tab2Monitor"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { Write-Host "Serve PowerShell come amministratore."; exit 1 }

if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
    Write-Host "Attivita' $TaskName precedente rimossa."
}
if ($Remove) { Write-Host "Avvio automatico disattivato."; exit 0 }

$py = (Get-Command python.exe -ErrorAction Stop).Source
$pyw = Join-Path (Split-Path -Parent $py) "pythonw.exe"
if (-not (Test-Path $pyw)) { Write-Host "pythonw.exe non trovato accanto a $py"; exit 1 }
$svc = Get-ChildItem -Path $Here -Filter "tab2monitor_service_v*.py" | Sort-Object Name | Select-Object -Last 1
if (-not $svc) { Write-Host "tab2monitor_service_v*.py non trovato in $Here"; exit 1 }

$user = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$action = New-ScheduledTaskAction -Execute $pyw -Argument ("`"" + $svc.FullName + "`" " + $ServerArgs) -WorkingDirectory $Here
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
$principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) `
    -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal `
    -Settings $settings -Description "Tab2revamp: server metriche per il Galaxy Tab 2 (porta 8090)" | Out-Null
Write-Host "Attivita' $TaskName creata per $user : $pyw $($svc.Name) $ServerArgs"

if (-not $NoStart) {
    $busy = Get-NetTCPConnection -LocalPort 8090 -State Listen -ErrorAction SilentlyContinue
    if ($busy) { Write-Host "ATTENZIONE: porta 8090 gia' in uso (pid $($busy.OwningProcess)): chiudere il server avviato a mano, il supervisore riprova ogni 10 s." }
    Start-ScheduledTask -TaskName $TaskName
    Write-Host "Avviata. Log: $Here\logs\tab2monitor_service.log"
}
