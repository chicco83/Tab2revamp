# =============================================================================
# Tab2Monitor - installazione dell'APK su un tablet Android via USB (tools\install_tablet.ps1,
# lanciato da Install-Tablet.cmd o dalla voce 5 del Setup)
# Versione: v0.1.0 - 2026-10-22 03:00 (ora locale)
# =============================================================================
# Alternativa senza cavo: dal browser del tablet aprire http://<IP-del-PC>:8090/apk
# (Tab2Monitor avviato sul PC) e installare il file scaricato.
#
# Cosa fa (sostituisce android/install_tab2monitor_v0.1.0_*.sh, che richiedeva Git Bash):
#   1) trova adb (pacchetto, PATH, Android SDK, %LOCALAPPDATA%\Tab2Monitor); se manca
#      propone di scaricare le Android Platform Tools ufficiali da dl.google.com;
#   2) sceglie il tablet (se ce ne sono piu' d'uno lo chiede; -Serial per forzarlo);
#   3) adb install -r dell'APK con versione piu' alta in apk\; se la firma e' diversa da
#      quella gia' installata spiega e chiede se disinstallare (le impostazioni si perdono);
#   4) permesso fotocamera (presenza, Android 6+), adb reverse, primo avvio con l'indirizzo
#      del PC (http://<IP>:8090, cosi' non serve la ricerca automatica);
#   5) a richiesta: schermata Home (consigliato da Android 10: e' l'unico modo per ripartire
#      da sola dopo un riavvio) e amministratore dispositivo (spegnimento reale dello schermo).
# Opzioni: -Serial <id>  -Apk <file>  -SetHome  -Admin  -Yes (nessuna domanda: niente Home/admin
#          se non richiesti)  -Url <http://...> (indirizzo del PC da salvare sul tablet)
# =============================================================================
param([string]$Serial = '', [string]$Apk = '', [switch]$SetHome, [switch]$Admin, [switch]$Yes, [string]$Url = '')
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"
$Pkg = 'it.tab2revamp.monitor'
$PtUrl = 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip'

function Say([string]$it, [string]$en, [string]$color = '') {
    if ($color) { Write-Host (T $it $en) -ForegroundColor $color } else { Write-Host (T $it $en) }
}

# ------------------------------------------------------------------- adb
$adb = Find-T2Adb
if (-not $adb) {
    Say 'adb (Android Platform Tools) non trovato su questo PC.' 'adb (Android Platform Tools) not found on this PC.' Yellow
    if ($Yes -or (Ask-T2YesNo (T "Scaricare le Platform Tools ufficiali di Google (~7 MB) da $PtUrl in $env:LOCALAPPDATA\Tab2Monitor?" "Download Google's official Platform Tools (~7 MB) from $PtUrl into $env:LOCALAPPDATA\Tab2Monitor?") $true)) {
        $dst = Join-Path $env:LOCALAPPDATA 'Tab2Monitor'
        New-Item -ItemType Directory -Force $dst | Out-Null
        $z = Join-Path $env:TEMP 'platform-tools-latest-windows.zip'
        & curl.exe -sSL -o $z $PtUrl
        Expand-Archive $z -DestinationPath $dst -Force
        Remove-Item $z -Force
        $adb = Join-Path $dst 'platform-tools\adb.exe'
    }
    if (-not $adb -or -not (Test-Path $adb)) { Say 'Senza adb non posso installare via USB. Usare http://<IP-PC>:8090/apk dal tablet.' 'Without adb I cannot install over USB. Use http://<PC-IP>:8090/apk from the tablet.' Red; exit 1 }
}
# adb scrive avanzamento e avvisi su stderr: in PowerShell 5.1, con ErrorActionPreference=Stop,
# '2>&1' su un eseguibile trasforma ogni riga di stderr in un errore fatale -> qui 'Continue'
# e righe convertite in testo (bug trovato provando sull'emulatore API 21)
function A { $ErrorActionPreference = 'Continue'; & $adb @args 2>&1 | ForEach-Object { "$_" } }
function AS { $ErrorActionPreference = 'Continue'; & $adb -s $script:Serial @args 2>&1 | ForEach-Object { "$_" } }

# ------------------------------------------------------------------- tablet
A start-server | Out-Null
$devs = @(A devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' } | ForEach-Object { $p = ($_ -split '\s+'); [pscustomobject]@{ id = $p[0]; state = $p[1] } })
if ($Serial) { $devs = @($devs | Where-Object { $_.id -eq $Serial }) }
$unauth = @($devs | Where-Object { $_.state -eq 'unauthorized' })
$ok = @($devs | Where-Object { $_.state -eq 'device' })
if ($ok.Count -eq 0) {
    if ($unauth.Count -gt 0) { Say 'Il tablet chiede di autorizzare il debug USB: accettare sul tablet ("Consenti sempre") e rilanciare.' 'The tablet asks to authorize USB debugging: accept on the tablet ("Always allow") and run again.' Yellow }
    else {
        Say 'Nessun tablet collegato. Sul tablet: Impostazioni > Info > toccare 7 volte "Numero build", poi Opzioni sviluppatore > Debug USB; collegare il cavo. Alcuni produttori (Samsung, ...) richiedono il loro driver USB per Windows.' `
            'No tablet connected. On the tablet: Settings > About > tap "Build number" 7 times, then Developer options > USB debugging; plug in the cable. Some vendors (Samsung, ...) need their Windows USB driver.' Yellow
    }
    exit 1
}
if ($ok.Count -gt 1 -and -not $Serial) {
    Say 'Piu'' dispositivi collegati:' 'Several devices connected:'
    for ($i = 0; $i -lt $ok.Count; $i++) {
        $m = (A -s $ok[$i].id shell getprop ro.product.model) -join ''
        Write-Host "  $($i + 1)) $($ok[$i].id)  $m"
    }
    $script:Serial = $ok[[int](Read-Host ('1-' + $ok.Count)) - 1].id
} else { $script:Serial = $ok[0].id }

$model = (AS shell getprop ro.product.model) -join ''
$sdk = [int]((AS shell getprop ro.build.version.sdk) -join '').Trim()
$rel = (AS shell getprop ro.build.version.release) -join ''
Say "Tablet: $model, Android $rel (API $sdk), $Serial" "Tablet: $model, Android $rel (API $sdk), $Serial"
if ($sdk -lt 21) { Say 'Serve Android 5.0 o successivo.' 'Android 5.0 or later is required.' Red; exit 1 }

# ------------------------------------------------------------------- APK
if (-not $Apk) {
    $Apk = Get-ChildItem (Join-Path $T2Root 'apk') -Filter 'Tab2Monitor_v*.apk' -ErrorAction SilentlyContinue |
        Sort-Object { [version]($_.BaseName -replace '^Tab2Monitor_v', '') } | Select-Object -Last 1 -ExpandProperty FullName
}
if (-not $Apk -or -not (Test-Path $Apk)) { Say 'APK non trovato in apk\.' 'APK not found in apk\.' Red; exit 1 }
Say "Installo $(Split-Path $Apk -Leaf) ..." "Installing $(Split-Path $Apk -Leaf) ..."
$out = (AS install -r $Apk) -join "`n"
if ($out -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
    Say 'Sul tablet c''e'' gia'' Tab2Monitor firmata con un''altra chiave: per installare questa versione va disinstallata (le impostazioni dell''app si perdono).' `
        'Tab2Monitor is already on the tablet, signed with another key: to install this version it must be uninstalled (the app settings are lost).' Yellow
    if (-not $Yes -and (Ask-T2YesNo (T 'Disinstallare e reinstallare?' 'Uninstall and reinstall?') $false)) {
        AS shell dpm remove-active-admin "$Pkg/.AdminReceiver" | Out-Null
        AS uninstall $Pkg | Out-Null
        $out = (AS install $Apk) -join "`n"
    }
}
if ($out -notmatch 'Success') { Write-Host $out; Say 'Installazione non riuscita.' 'Installation failed.' Red; exit 1 }
Say 'APK installato.' 'APK installed.' Green

# ------------------------------------------------------------------- configurazione
if ($sdk -ge 23) { AS shell pm grant $Pkg android.permission.CAMERA | Out-Null }
AS shell settings put secure immersive_mode_confirmations confirmed | Out-Null
$port = Get-T2Port
AS reverse "tcp:$port" "tcp:$port" | Out-Null
if (-not $Url) { $ip = Get-T2LanIp; if ($ip) { $Url = "http://${ip}:$port" } }
$extra = @()
if ($Url) { $extra = @('--es', 'url', $Url) }
# Claude spento sul PC (default dei PC nuovi): pannello Claude spento anche sul tablet
if (-not (Get-T2Config).claude) { $extra += @('--ez', 'p_claude', 'false') }
AS shell am start -n "$Pkg/.MainActivity" @extra | Out-Null
Say "App avviata. Indirizzo del PC salvato sul tablet: $Url (in USB usa 127.0.0.1 via adb reverse)." "App started. PC address saved on the tablet: $Url (over USB it uses 127.0.0.1 via adb reverse)." Green

$wantHome = $SetHome -or ((-not $Yes) -and (Ask-T2YesNo (T "Usare Tab2Monitor come schermata Home? (consigliato se il tablet serve solo da display$(if ($sdk -ge 29) { '; da Android 10 e'' l''unico modo per ripartire da sola dopo un riavvio' }))" "Use Tab2Monitor as the Home screen? (recommended if the tablet is only a display$(if ($sdk -ge 29) { '; from Android 10 it is the only way to restart by itself after a reboot' }))") ($sdk -ge 29)))
if ($wantHome) {
    # l'app attiva da se' il suo HomeAlias (--ez home true): da Android 11 circa la shell adb non
    # puo' piu' farlo con "pm enable" (SecurityException vista sull'emulatore API 34)
    AS shell am start -n "$Pkg/.MainActivity" --ez home true | Out-Null
    Start-Sleep 2
    $r = if ($sdk -ge 29) { (AS shell cmd role add-role-holder android.app.role.HOME $Pkg) -join ' ' }
         elseif ($sdk -ge 24) { (AS shell cmd package set-home-activity "$Pkg/.HomeAlias") -join ' ' } else { 'manual' }
    if ($r -match 'Error|Exception|manual') {
        Say 'Premere il tasto Home sul tablet e scegliere Tab2Monitor ("Sempre").' 'Press Home on the tablet and choose Tab2Monitor ("Always").' Yellow
    } else {
        Say 'Tab2Monitor e'' la schermata Home (per tornare indietro: Impostazioni > App > App predefinite > Home).' 'Tab2Monitor is the Home screen (to undo: Settings > Apps > Default apps > Home).'
        # visto sull'emulatore API 34: con un riavvio subito dopo, Android non aveva ancora salvato
        # ne' il componente attivato ne' il ruolo Home (tornava il launcher di sistema)
        Say 'Attendere almeno 30 secondi prima di riavviare il tablet (Android salva la scelta con ritardo).' 'Wait at least 30 seconds before rebooting the tablet (Android saves the choice with a delay).'
    }
}
$wantAdmin = $Admin -or ((-not $Yes) -and (Ask-T2YesNo (T 'Attivare lo spegnimento reale dello schermo (amministratore dispositivo, serve alla funzione presenza)?' 'Enable real screen off (device administrator, used by the presence feature)?') $false))
if ($wantAdmin) {
    $r = (AS shell dpm set-active-admin "$Pkg/.AdminReceiver") -join ''
    if ($r -match 'Success') { Say 'Amministratore dispositivo attivo. Prima di disinstallare l''app: adb shell dpm remove-active-admin it.tab2revamp.monitor/.AdminReceiver' 'Device administrator active. Before uninstalling the app: adb shell dpm remove-active-admin it.tab2revamp.monitor/.AdminReceiver' }
    else { Say "Non riuscito ($r): si puo' attivare dalle impostazioni dell'app." "Failed ($r): it can be enabled from the app settings." Yellow }
}
Say 'Fatto.' 'Done.' Green
