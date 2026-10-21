#!/usr/bin/env bash
# =============================================================================
# Tab2revamp - ottimizzazione + modalita' display (via adb, senza root)
# Versione: v0.1.4 - 2026-10-22 02:50
#   v0.1.4: com.svox.pico NON piu disattivato (motore vocale degli allarmi di Tab2Monitor
#           v0.8.0); il resto e identico a v0.1.3
# Uso:
#   bash tune_v0.1.4_20261022-0250.sh            -> applica tuning + apre dashboard
#   bash tune_v0.1.4_20261022-0250.sh --debloat  -> in piu' disabilita le app inutili
#   URL=http://192.168.1.10:8090 bash tune_...sh -> usa Wi-Fi invece di adb reverse
# Tutto reversibile: "pm enable <pacchetto>" e ripristino scale animazioni a 1.
# =============================================================================
set -u
# v0.1.2: evita che Git Bash riscriva i percorsi "/..." passati ad adb shell
export MSYS_NO_PATHCONV=1
# v0.1.2: adb shell con timeout (su stock 4.2.2 "pm disable-user" resta appeso
# per sempre senza errore e lascia processi app_process orfani sul tablet)
T() { timeout "${TMO:-20}" adb shell "$@" | tr -d '\r'; }
URL="${URL:-http://localhost:8090}"
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
echo "Android SDK: $SDK"

# --- 1. Animazioni a zero: il guadagno percepito piu' grande su SGX540 -------
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "$k" 0
done

# --- 2. Display sempre acceso quando alimentato (AC=1 + USB=2 => 3) ---------
adb shell settings put global stay_on_while_plugged_in 3
adb shell settings put system screen_off_timeout 1800000
adb shell settings put system screen_brightness_mode 0
adb shell settings put system screen_brightness 90          # 0-255, basso = meno calore
adb shell settings put system accelerometer_rotation 0      # niente rotazione automatica
# 2026-10-21 v0.1.0 (errato: il Tab 2 10.1 e' nativamente landscape, 1 = 90 gradi = verticale):
# adb shell settings put system user_rotation 1
adb shell settings put system user_rotation 0                # landscape (orientamento naturale)

# --- 3. Meno lavoro in background -------------------------------------------
adb shell settings put global wifi_scan_always_enabled 0
adb shell settings put global ble_scan_always_enabled 0 2>/dev/null
adb shell settings put secure location_providers_allowed -gps,-network 2>/dev/null
# limite processi in background: impostarlo a mano in Opzioni sviluppatore
# ("Limite processi in background" = max 2). Non lo si fa via "service call"
# perche' il codice di transazione cambia tra versioni Android (rischioso).

# --- 4. Debloat opzionale (pm disable-user, reversibile con pm enable) ------
# v0.1.2: senza root il debloat via adb funziona solo da Android 4.3+ (SDK 18).
# Su stock 4.2.2 (SDK 17) "pm disable-user" si blocca: serve root (su) oppure
# disattivare a mano da Impostazioni > Gestione applicazioni > Disattiva.
ROOT=0; T su -c id 2>/dev/null | grep -q "uid=0" && ROOT=1
echo "Root: $ROOT"
if [ "${1:-}" = "--debloat" ] && [ "$ROOT" = 0 ] && [ "${SDK:-0}" -lt 18 ]; then
  echo "SALTO debloat: Android < 4.3 senza root (pm disable-user si blocca). Vedi MANUAL 2.3."
elif [ "${1:-}" = "--debloat" ]; then
  # Elenco prudente: Samsung/Google non necessari per il display.
  # Nei ROM custom (LineageOS senza GApps) la maggior parte non esiste: ignorato.
  # 2026-10-21 v0.1.2: l'elenco conteneva org.lineageos.jelly -> su LineageOS senza GApps
  # restava solo Lightning e la dashboard apriva il selettore. Jelly ora e' il browser kiosk.
  # 2026-10-22 v0.1.4: tolto com.svox.pico dall'elenco (motore vocale degli allarmi di Tab2Monitor);
  # in v0.1.3 la riga era "com.svox.pico com.android.dreams.basic ...".
  PKGS="com.sec.android.app.samsungapps com.sec.android.widgetapp.ap.hero.accuweather
  com.sec.android.app.music com.sec.android.app.videoplayer com.sec.android.gallery3d
  com.samsung.music com.sec.chaton com.sec.android.app.readershub com.sec.android.app.kieswifi
  com.sec.android.daemonapp.ap.yahoonews com.sec.android.daemonapp.ap.yahoostock
  com.sec.android.widgetapp.dualclockdigital com.sec.android.app.memo com.sec.android.app.popupcalculator
  com.sec.android.app.sns com.sec.android.app.snsaccount com.sec.android.app.voicerecorder
  com.google.android.youtube com.google.android.music com.google.android.videos com.google.android.apps.books
  com.google.android.apps.magazines com.google.android.talk com.google.android.apps.plus
  com.google.android.apps.maps com.google.android.gm com.google.android.googlequicksearchbox
  com.dropbox.android com.android.email com.android.calendar com.android.providers.calendar
  org.lineageos.eleven org.lineageos.snap
  acr.browser.lightning com.cyanogenmod.eleven com.android.email com.android.exchange
  com.android.messaging com.android.calendar com.android.gallery3d com.android.camera2
  com.cyanogenmod.lockclock org.cyanogenmod.weatherservice org.cyanogenmod.weather.provider
  com.android.dreams.basic com.android.dreams.phototable com.android.egg
  com.android.cellbroadcastreceiver org.lineageos.recorder org.lineageos.updater
  com.android.wallpaper.livepicker org.cyanogenmod.audiofx
  com.sec.android.widgetapp.at.hero.accuweather com.sec.android.app.sns3 com.samsung.videohub
  com.sec.android.app.samsungapps.una2 com.tgrape.android.radar com.sec.android.widgetapp.dualclockanalog
  com.sec.android.widgetapp.programmonitorwidget com.sec.android.pagebuddynotisvc com.sec.android.provider.snote
  com.sec.android.sCloudSync com.sec.android.sCloudSyncBrowser com.sec.android.sCloudSyncCalendar
  com.sec.android.sCloudSyncContacts com.sec.android.sCloudSyncSNote com.sec.android.sCloudRelayData
  com.sec.android.cloudagent com.sec.android.app.mobileprint com.epson.mobilephone.samsungprintservice
  com.hp.android.printservice com.sec.app.samsungprintservice com.samsung.shareshot
  com.sec.android.allshare.service.controlshare com.sec.android.allshare.service.fileshare
  com.sec.android.allshare.service.mediashare com.sec.android.nearby.mediaserver
  com.google.android.street com.google.android.voicesearch com.lifevibes.trimapp
  com.accesslane.livewallpaper.deep_space.lite com.hu1.wallpaper.aurora2 fishnoodle.clouds_samsung
  fishnoodle.silhouette_samsung com.android.noisefield com.android.phasebeam"
  for p in $PKGS; do
    # 2026-10-21 v0.1.0 (senza timeout, senza su; si bloccava su 4.2.2):
    # if adb shell pm list packages "$p" | tr -d '\r' | grep -qx "package:$p"; then
    #   echo "disable $p"; adb shell pm disable-user --user 0 "$p" >/dev/null 2>&1 || adb shell pm disable "$p"
    # fi
    T pm list packages -e "$p" | grep -qx "package:$p" || continue   # assente o gia' disattivato
    if [ "$ROOT" = 1 ]; then R=$(T su -c "pm disable $p"); else R=$(T pm disable-user "$p"); fi
    echo "disable $p -> ${R:-TIMEOUT}"
  done
fi

# --- 5. Collegamento al PC --------------------------------------------------
# adb reverse esiste solo da Android 5.0 (SDK 21). Sullo stock 4.x usare Wi-Fi.
if [ "${SDK:-0}" -ge 21 ] && [ "$URL" = "http://localhost:8090" ]; then
  adb reverse tcp:8090 tcp:8090 && echo "adb reverse attivo"
elif [ "$URL" = "http://localhost:8090" ]; then
  echo "ATTENZIONE: Android < 5.0, adb reverse non disponibile. Rilancia con URL=http://<IP-PC>:8090"
fi

# --- 6. Apertura dashboard a schermo intero ---------------------------------
# v0.1.2: preferisce Chrome se installato (il browser stock 4.2 ha artefatti di
# ridisegno sulle card); altrimenti l'intent generico.
# v0.1.3: ordine di preferenza Chrome (stock) -> Jelly (LineageOS, WebView di sistema)
# -> intent generico. Con il pacchetto esplicito non compare il selettore.
BROWSER=""
for b in com.android.chrome org.lineageos.jelly; do
  T pm list packages -e "$b" | grep -qx "package:$b" && { BROWSER=$b; break; }
done
echo "Browser: ${BROWSER:-predefinito}"
if [ -n "$BROWSER" ]; then
  adb shell am start -a android.intent.action.VIEW -d "$URL" "$BROWSER"
else
  adb shell am start -a android.intent.action.VIEW -d "$URL"
fi
echo "Fatto. Su Android 5+ fissa la schermata con 'Blocco schermo app' (Sicurezza) per un kiosk."
