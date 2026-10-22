# =============================================================================
# Tab2Monitor - Setup del PC (tools\setup.ps1, lanciato da Setup.cmd)
# Versione: v0.1.0 - 2026-10-22 03:00 (ora locale)
# =============================================================================
# Il pacchetto funziona anche SENZA setup (copia portabile: doppio clic su
# Tab2Monitor.cmd). Il setup serve per:
#   1) Installare in Program Files + avvio automatico. Scelte (decisione dell'utente):
#        Logon     = all'accesso, con amministratore (sensori CPU/scheda madre, nessun UAC)
#        Boot      = all'avvio del PC, account SYSTEM (prima dell'accesso)
#        LogonUser = all'accesso, SENZA amministratore (niente sensori CPU/scheda madre);
#                    l'unica possibile anche dalla copia portabile
#        None      = nessun avvio automatico
#      Perche' Program Files per Logon/Boot: un'attivita' con privilegi elevati che esegue
#      file da una cartella scrivibile dall'utente (Download, Desktop, Google Drive) e' un
#      modo per ottenere l'amministratore sostituendo un file; Program Files e' scrivibile
#      solo dagli amministratori.
#   2) Firewall: regole "Tab2Monitor" solo per python.exe del pacchetto, porta TCP+UDP
#      (8090), profilo PRIVATO, solo sottorete locale (decisione: niente token, basta il
#      limite alla LAN). Su rete "Pubblica" propone di renderla privata.
#   3) Driver PawnIO (sensori CPU/scheda madre/ventole): il setup lo PROPONE; download dal
#      rilascio ufficiale github.com/namazso/PawnIO.Setup 2.2.0, verifica SHA256 e firma,
#      poi l'installer di PawnIO (con le sue schermate). Non incluso nel pacchetto: la
#      licenza dell'installer non e' dichiarata.
#   4) Installazione dell'APK sul tablet (tools\install_tablet.ps1).
#   5) Disinstallazione (attivita', regole, copia in Program Files; dati a richiesta).
#
# Uso non interattivo (prove, distribuzione):
#   powershell -ExecutionPolicy Bypass -File tools\setup.ps1 -Action Status
#   ... -Action Install -Autostart Logon|Boot|LogonUser|None [-Claude] [-Yes]
#   ... -Action Autostart -Autostart LogonUser         (dalla copia portabile, senza admin)
#   ... -Action Firewall | PawnIO | Uninstall [-Yes] [-KeepData]
#   -TaskName (default Tab2Monitor) e -InstallDir permettono prove senza toccare
#   un'installazione esistente. -DryRun mostra cosa farebbe senza cambiare nulla.
# =============================================================================
param(
    [ValidateSet('Menu', 'Status', 'Install', 'Autostart', 'Firewall', 'PawnIO', 'Tablet', 'Uninstall')]
    [string]$Action = 'Menu',
    [ValidateSet('Logon', 'Boot', 'LogonUser', 'None', '')]
    [string]$Autostart = '',
    [string]$TaskName = 'Tab2Monitor',
    [string]$InstallDir = (Join-Path $env:ProgramFiles 'Tab2Monitor'),
    [switch]$Claude,
    [switch]$Yes,
    [switch]$KeepData,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"

$PawnUrl = 'https://github.com/namazso/PawnIO.Setup/releases/download/2.2.0/PawnIO_setup.exe'
$PawnSha = '1F519A22E47187F70A1379A48CA604981C4FCF694F4E65B734AAA74A9FBA3032'
$PawnSigner = 'CN=namazso.eu'
$FwGroup = 'Tab2Monitor'

function Say([string]$it, [string]$en, [string]$color = '') {
    if ($color) { Write-Host (T $it $en) -ForegroundColor $color } else { Write-Host (T $it $en) }
}

function Confirm-T2([string]$it, [string]$en, [bool]$default = $false) {
    if ($Yes) { return $true }
    return (Ask-T2YesNo (T $it $en) $default)
}

function Do-T2([string]$what, [scriptblock]$sb) {
    if ($DryRun) { Write-Host "  [dry-run] $what" -ForegroundColor DarkGray; return }
    & $sb
}

function Need-Admin {
    if (Test-T2Admin) { return $true }
    if ($DryRun) { Write-Host '  [dry-run] (servirebbe l''amministratore)' -ForegroundColor DarkGray; return $true }
    Say 'Questa operazione richiede l''amministratore: riaprire Setup.cmd e accettare la richiesta di Windows (UAC).' 'This operation needs administrator rights: reopen Setup.cmd and accept the Windows prompt (UAC).' Yellow
    return $false
}

# ------------------------------------------------------------------- stato
function Get-T2Task { Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue }

function Show-Status {
    $port = Get-T2Port
    Write-Host ''
    Write-Host '  Tab2Monitor - Setup' -ForegroundColor Cyan
    Say "  Cartella: $T2Root" "  Folder: $T2Root"
    Say ('  Tipo: ' + $(if (Get-T2Installed) { 'installato (dati in ' + (Get-T2DataDir) + ')' } else { 'portabile (dati in ' + (Get-T2DataDir) + ')' })) `
        ('  Type: ' + $(if (Get-T2Installed) { 'installed (data in ' + (Get-T2DataDir) + ')' } else { 'portable (data in ' + (Get-T2DataDir) + ')' }))
    $t = Get-T2Task
    if ($t) {
        $a = $t.Actions[0]
        Say "  Avvio automatico '$TaskName': $($t.State) -> $($a.Execute) $($a.Arguments)" "  Autostart '$TaskName': $($t.State) -> $($a.Execute) $($a.Arguments)"
    } else { Say "  Avvio automatico: nessuno" "  Autostart: none" }
    $fw = Get-NetFirewallRule -Group $FwGroup -ErrorAction SilentlyContinue
    Say ("  Regole firewall Tab2Monitor: " + @($fw).Count) ("  Tab2Monitor firewall rules: " + @($fw).Count)
    $pw = Get-Service PawnIO -ErrorAction SilentlyContinue
    Say ("  Driver PawnIO (sensori CPU/scheda madre): " + $(if ($pw) { $pw.Status } else { 'non installato' })) `
        ("  PawnIO driver (CPU/motherboard sensors): " + $(if ($pw) { $pw.Status } else { 'not installed' }))
    try {
        foreach ($p in Get-NetConnectionProfile -ErrorAction Stop) {
            Say "  Rete '$($p.Name)': $($p.NetworkCategory)" "  Network '$($p.Name)': $($p.NetworkCategory)"
        }
    } catch { }
    $c = Get-T2Config
    Say "  Porta: $port   Claude: $($c.claude)" "  Port: $port   Claude: $($c.claude)"
    try {
        $i = Invoke-RestMethod "http://127.0.0.1:$port/api/info" -TimeoutSec 2
        Say "  Server attivo: $($i.name) $($i.ver)" "  Server running: $($i.name) $($i.ver)" Green
    } catch { Say '  Server: non attivo' '  Server: not running' }
    Write-Host ''
}

# ------------------------------------------------------------------- avvio automatico
function Set-T2Autostart([string]$mode, [string]$root, [string]$data) {
    $t = Get-T2Task
    if ($t) {
        $a = $t.Actions[0]
        if (-not ("$($a.Execute) $($a.Arguments)" -like "*$root*")) {
            Say "Esiste gia' un'attivita' '$TaskName' che avvia un'altra copia:" "A task '$TaskName' already exists and starts another copy:" Yellow
            Write-Host "    $($a.Execute) $($a.Arguments)"
            if (-not (Confirm-T2 'Sostituirla?' 'Replace it?')) { return }
        }
        Do-T2 "rimuovi attivita' $TaskName" { Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue; Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false }
    }
    if ($mode -eq 'None') { Say 'Avvio automatico disattivato.' 'Autostart disabled.'; return }
    $pyw = Join-Path $root 'python\pythonw.exe'
    $svc = Get-T2Latest (Join-Path $root 'server') 'tab2monitor_service_v*.py'
    if (-not $svc -and -not $DryRun) { throw "tab2monitor_service_v*.py non trovato in $root\server" }
    $svcPath = if ($svc) { $svc.FullName } else { "$root\server\tab2monitor_service_vX.py" }
    $action = New-ScheduledTaskAction -Execute $pyw -Argument ("`"$svcPath`" --data `"$data`"") -WorkingDirectory (Join-Path $root 'server')
    $user = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    switch ($mode) {
        'Logon'     { $trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
                      $principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Highest }
        'LogonUser' { $trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
                      $principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited }
        'Boot'      { $trigger = New-ScheduledTaskTrigger -AtStartup
                      $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest }
    }
    $settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries `
        -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) `
        -MultipleInstances IgnoreNew
    Do-T2 "crea attivita' $TaskName ($mode): $pyw `"$svcPath`" --data `"$data`"" {
        Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings `
            -Description 'Tab2Monitor: server metriche del PC per il tablet (porta 8090)' | Out-Null
    }
    Say "Avvio automatico '$mode' creato ($TaskName)." "Autostart '$mode' created ($TaskName)." Green
    if ($mode -eq 'Boot') {
        Say 'Nota: come SYSTEM il pannello Claude non legge i log dell''utente (claude_dir in config.json) e adb usa chiavi proprie: il tablet chiedera'' di nuovo l''autorizzazione USB.' `
            'Note: as SYSTEM the Claude panel cannot read the user logs (claude_dir in config.json) and adb uses its own keys: the tablet will ask again for USB authorization.' Yellow
    }
    if ((Get-NetTCPConnection -LocalPort (Get-T2Port) -State Listen -ErrorAction SilentlyContinue)) {
        Say 'La porta e'' gia'' in uso (server avviato a mano?): chiuderlo, l''avvio automatico riprova ogni 10 s.' 'The port is already in use (server started by hand?): close it, autostart retries every 10 s.' Yellow
    }
    Do-T2 "avvia $TaskName" { Start-ScheduledTask -TaskName $TaskName }
}

# ------------------------------------------------------------------- firewall
function Set-T2Firewall([string]$root) {
    if (-not (Need-Admin)) { return }
    $port = Get-T2Port
    $py = Join-Path $root 'python\python.exe'
    Do-T2 "rimuovi regole gruppo $FwGroup" { Get-NetFirewallRule -Group $FwGroup -ErrorAction SilentlyContinue | Remove-NetFirewallRule }
    foreach ($proto in 'TCP', 'UDP') {
        Do-T2 "regola $proto $port, Private, LocalSubnet, $py" {
            New-NetFirewallRule -DisplayName "Tab2Monitor $proto $port" -Group $FwGroup -Direction Inbound -Action Allow `
                -Program $py -Protocol $proto -LocalPort $port -Profile Private -RemoteAddress LocalSubnet `
                -Description 'Tab2Monitor: tablet sulla rete locale (solo profilo privato)' | Out-Null
        }
    }
    Say "Firewall: consentite TCP e UDP $port per $py, solo rete privata e sottorete locale." "Firewall: TCP and UDP $port allowed for $py, private network and local subnet only." Green
    try {
        foreach ($p in Get-NetConnectionProfile -ErrorAction Stop | Where-Object { $_.NetworkCategory -eq 'Public' }) {
            Say "La rete '$($p.Name)' e' 'Pubblica': le regole non valgono e il tablet non si collega." "Network '$($p.Name)' is 'Public': the rules do not apply and the tablet cannot connect." Yellow
            if (Confirm-T2 "Impostare '$($p.Name)' come rete privata (solo se e' la rete di casa/ufficio fidata)?" "Set '$($p.Name)' as a private network (only if it is your trusted home/office network)?") {
                Do-T2 "rete $($p.Name) -> Private" { Set-NetConnectionProfile -InterfaceIndex $p.InterfaceIndex -NetworkCategory Private }
            }
        }
    } catch { }
    $old = Get-NetFirewallRule -DisplayName 'python.exe' -ErrorAction SilentlyContinue | Where-Object { $_.Enabled -eq 'True' -and $_.Action -eq 'Allow' }
    if ($old) {
        Say "Nota: ci sono $(@($old).Count) regole 'python.exe' generiche (tutte le porte) create da Windows: valutare di toglierle in 'Windows Defender Firewall con sicurezza avanzata'." `
            "Note: there are $(@($old).Count) generic 'python.exe' rules (all ports) created by Windows: consider removing them in 'Windows Defender Firewall with Advanced Security'."
    }
}

# ------------------------------------------------------------------- PawnIO
function Install-T2PawnIO {
    if (Get-Service PawnIO -ErrorAction SilentlyContinue) { Say 'PawnIO e'' gia'' installato.' 'PawnIO is already installed.' Green; return }
    Say 'PawnIO e'' il driver (firmato) con cui LibreHardwareMonitor legge temperature di CPU e scheda madre e le ventole. Senza, si vedono comunque GPU, carico, RAM, dischi e rete.' `
        'PawnIO is the (signed) driver LibreHardwareMonitor uses to read CPU and motherboard temperatures and fans. Without it you still get GPU, load, RAM, disks and network.'
    if (-not (Confirm-T2 "Scaricare PawnIO 2.2.0 (3,4 MB) da $PawnUrl e installarlo?" "Download PawnIO 2.2.0 (3.4 MB) from $PawnUrl and install it?" $true)) { return }
    if (-not (Need-Admin)) { return }
    $f = Join-Path $env:TEMP 'PawnIO_setup_2.2.0.exe'
    $local = Join-Path $T2Root 'drivers\PawnIO_setup.exe'
    Do-T2 "download $PawnUrl" {
        if (Test-Path $local) { Copy-Item $local $f -Force } else { & curl.exe -sSL -o $f $PawnUrl }
    }
    if ($DryRun) { return }
    $h = (Get-FileHash $f -Algorithm SHA256).Hash
    $sig = Get-AuthenticodeSignature $f
    if ($h -ne $PawnSha -or $sig.Status -ne 'Valid' -or $sig.SignerCertificate.Subject -notlike "*$PawnSigner*") {
        Say "File non verificato (SHA256 $h, firma $($sig.Status)): non lo eseguo." "File not verified (SHA256 $h, signature $($sig.Status)): not running it." Red
        Remove-Item $f -Force -ErrorAction SilentlyContinue
        return
    }
    Say 'Verificato (SHA256 e firma namazso.eu). Si apre l''installer di PawnIO.' 'Verified (SHA256 and namazso.eu signature). The PawnIO installer opens now.'
    Start-Process $f -Wait
    Remove-Item $f -Force -ErrorAction SilentlyContinue
    if (Get-Service PawnIO -ErrorAction SilentlyContinue) { Say 'PawnIO installato.' 'PawnIO installed.' Green }
}

# ------------------------------------------------------------------- installazione
function Install-T2 {
    if (-not $Autostart) {
        if ($Yes) { $mode = 'Logon' } else {
            Say 'Avvio automatico del server:' 'Server autostart:'
            Say '  1) all''accesso, con amministratore (consigliato: tutti i sensori)' '  1) at logon, with administrator (recommended: all sensors)'
            Say '  2) all''avvio del PC, prima dell''accesso (account SYSTEM)' '  2) at PC startup, before logon (SYSTEM account)'
            Say '  3) all''accesso, senza amministratore (niente sensori CPU/scheda madre)' '  3) at logon, without administrator (no CPU/motherboard sensors)'
            Say '  4) nessuno (avvio a mano con Tab2Monitor.cmd)' '  4) none (start by hand with Tab2Monitor.cmd)'
            $mode = @{ '1' = 'Logon'; '2' = 'Boot'; '3' = 'LogonUser'; '4' = 'None' }[(Read-Host '1-4')]
            if (-not $mode) { $mode = 'Logon' }
        }
    } else { $mode = $Autostart }
    if (-not (Need-Admin)) { return }
    $data = Join-Path $env:ProgramData 'Tab2Monitor'
    if ((Resolve-Path $T2Root).Path.TrimEnd('\') -ne $InstallDir.TrimEnd('\')) {
        Say "Copia in $InstallDir ..." "Copying to $InstallDir ..."
        Do-T2 "robocopy $T2Root -> $InstallDir (senza data\)" {
            if (Get-T2Task) { Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue; Start-Sleep 2 }
            & robocopy.exe $T2Root $InstallDir /MIR /XD (Join-Path $T2Root 'data') /NFL /NDL /NJH /NJS /NP | Out-Null
            if ($LASTEXITCODE -ge 8) { throw "robocopy errore $LASTEXITCODE" }
        }
    }
    Do-T2 "installed.txt + $data" {
        Set-Content -Path (Join-Path $InstallDir 'installed.txt') -Value "Tab2Monitor installato il $(Get-Date -Format s). Dati: $data" -Encoding ASCII
        New-Item -ItemType Directory -Force $data | Out-Null
        $cfg = Join-Path $data 'config.json'
        if (-not (Test-Path $cfg)) {
            $src = Join-Path $T2Root 'data\config.json'
            if (Test-Path $src) { Copy-Item $src $cfg } else { '{"port": 8090, "claude": false}' | Set-Content $cfg -Encoding ASCII }
        }
    }
    # da qui in poi le funzioni lavorano sulla copia installata
    $script:T2Root = $InstallDir
    if (-not $DryRun) {
        $useClaude = $Claude -or ((-not $Yes) -and (Ask-T2YesNo (T 'Mostrare sul tablet i token usati da Claude Code su questo PC?' 'Show on the tablet the tokens used by Claude Code on this PC?') $false))
        Set-T2ConfigValue 'claude' ([bool]$useClaude)
    }
    Set-T2Firewall $InstallDir
    if ($mode -in 'Logon', 'Boot') { Install-T2PawnIO }
    Set-T2Autostart $mode $InstallDir $data
    Say "Installato in $InstallDir. Collegamento: $InstallDir\Tab2Monitor.cmd" "Installed in $InstallDir. Shortcut: $InstallDir\Tab2Monitor.cmd" Green
    Do-T2 'collegamento nel menu Start' {
        $lnk = Join-Path ([Environment]::GetFolderPath('CommonPrograms')) 'Tab2Monitor.lnk'
        $ws = New-Object -ComObject WScript.Shell
        $s = $ws.CreateShortcut($lnk); $s.TargetPath = (Join-Path $InstallDir 'Tab2Monitor.cmd'); $s.WorkingDirectory = $InstallDir; $s.Save()
        $lnk2 = Join-Path ([Environment]::GetFolderPath('CommonPrograms')) 'Tab2Monitor Setup.lnk'
        $s = $ws.CreateShortcut($lnk2); $s.TargetPath = (Join-Path $InstallDir 'Setup.cmd'); $s.WorkingDirectory = $InstallDir; $s.Save()
    }
}

function Uninstall-T2 {
    $t = Get-T2Task
    if ($t) {
        $a = $t.Actions[0]
        $ours = "$($a.Execute) $($a.Arguments)" -like "*$InstallDir*" -or "$($a.Execute) $($a.Arguments)" -like "*$T2Root*"
        if ($ours -or (Confirm-T2 "L'attivita' '$TaskName' avvia un'altra copia ($($a.Arguments)). Rimuoverla lo stesso?" "Task '$TaskName' starts another copy ($($a.Arguments)). Remove it anyway?")) {
            Do-T2 "rimuovi attivita' $TaskName" { Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue; Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false }
            Say "Attivita' $TaskName rimossa." "Task $TaskName removed."
        }
    }
    if (Get-NetFirewallRule -Group $FwGroup -ErrorAction SilentlyContinue) {
        if (Need-Admin) { Do-T2 "rimuovi regole $FwGroup" { Get-NetFirewallRule -Group $FwGroup | Remove-NetFirewallRule }; Say 'Regole firewall rimosse.' 'Firewall rules removed.' }
    }
    if (Test-Path (Join-Path $InstallDir 'installed.txt')) {
        if (Need-Admin) {
            Do-T2 'rimuovi collegamenti Start' { Remove-Item (Join-Path ([Environment]::GetFolderPath('CommonPrograms')) 'Tab2Monitor*.lnk') -Force -ErrorAction SilentlyContinue }
            Do-T2 "rimuovi $InstallDir" {
                Get-Process python, pythonw -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "$InstallDir*" } | Stop-Process -Force
                Start-Sleep 1
                if ((Resolve-Path $T2Root).Path.TrimEnd('\') -eq $InstallDir.TrimEnd('\')) {
                    # lo script gira da dentro la cartella: la cancella un processo separato dopo l'uscita
                    Start-Process cmd.exe -ArgumentList "/c timeout /t 3 >nul & rmdir /s /q `"$InstallDir`"" -WindowStyle Hidden
                } else { Remove-Item $InstallDir -Recurse -Force }
            }
            Say "Programma rimosso da $InstallDir." "Program removed from $InstallDir."
        }
    }
    $data = Join-Path $env:ProgramData 'Tab2Monitor'
    if ((Test-Path $data) -and -not $KeepData) {
        if (Confirm-T2 "Cancellare anche i dati (${data}: nomi delle ventole, configurazione, log)?" "Also delete the data (${data}: fan names, configuration, logs)?") {
            Do-T2 "rimuovi $data" { Remove-Item $data -Recurse -Force }
        }
    }
    if (Get-Service PawnIO -ErrorAction SilentlyContinue) {
        Say 'Il driver PawnIO resta installato (puo'' servire ad altri programmi, es. LibreHardwareMonitor, FanControl). Per toglierlo: Impostazioni > App > PawnIO.' `
            'The PawnIO driver stays installed (other programs may use it, e.g. LibreHardwareMonitor, FanControl). To remove it: Settings > Apps > PawnIO.'
    }
}

function Run-Tablet { & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'install_tablet.ps1') }

# ------------------------------------------------------------------- menu
if ($Action -eq 'Menu' -and -not (Test-T2Admin)) {
    # il menu chiede subito l'amministratore (serve a quasi tutte le voci); se negato resta utilizzabile
    try {
        Start-Process powershell -Verb RunAs -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"") -ErrorAction Stop | Out-Null
        exit 0
    } catch { Say 'Senza amministratore: disponibili solo stato, avvio all''accesso senza admin e tablet.' 'Without administrator: only status, logon autostart without admin and tablet are available.' Yellow }
}

switch ($Action) {
    'Status'    { Show-Status }
    'Install'   { Install-T2 }
    'Autostart' {
        if ($Autostart -in 'Logon', 'Boot' -and -not (Get-T2Installed)) {
            Say 'Logon/Boot con amministratore solo dalla copia installata (Install): una cartella scrivibile dall''utente non deve girare come amministratore.' `
                'Logon/Boot with administrator only from the installed copy (Install): a user-writable folder must not run as administrator.' Yellow
        } elseif ($Autostart -in 'Logon', 'Boot' -and -not (Need-Admin)) {
        } else { Set-T2Autostart ($(if ($Autostart) { $Autostart } else { 'LogonUser' })) $T2Root (Get-T2DataDir) }
    }
    'Firewall'  { Set-T2Firewall $T2Root }
    'PawnIO'    { Install-T2PawnIO }
    'Tablet'    { Run-Tablet }
    'Uninstall' { Uninstall-T2 }
    'Menu' {
        while ($true) {
            Show-Status
            Say '  1) Installa nel PC (Program Files) con avvio automatico' '  1) Install on this PC (Program Files) with autostart'
            Say '  2) Avvio automatico all''accesso SENZA amministratore, da questa cartella' '  2) Autostart at logon WITHOUT administrator, from this folder'
            Say '  3) Firewall (consenti il tablet sulla rete privata)' '  3) Firewall (allow the tablet on the private network)'
            Say '  4) Driver PawnIO per i sensori di CPU e scheda madre' '  4) PawnIO driver for CPU and motherboard sensors'
            Say '  5) Installa l''app sul tablet (cavo USB)' '  5) Install the app on the tablet (USB cable)'
            Say '  6) Disinstalla' '  6) Uninstall'
            Say '  0) Esci' '  0) Exit'
            $c = Read-Host '>'
            try {
                switch ($c) {
                    '1' { Install-T2 }
                    '2' { Set-T2Autostart 'LogonUser' $T2Root (Get-T2DataDir) }
                    '3' { Set-T2Firewall $T2Root }
                    '4' { Install-T2PawnIO }
                    '5' { Run-Tablet }
                    '6' { Uninstall-T2 }
                    '0' { exit 0 }
                }
            } catch { Write-Host $_ -ForegroundColor Red }
            Read-Host (T 'Invio per continuare' 'Enter to continue') | Out-Null
        }
    }
}
