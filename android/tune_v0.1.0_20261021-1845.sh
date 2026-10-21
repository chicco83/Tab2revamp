#!/usr/bin/env bash
# =============================================================================
# Tab2revamp - ottimizzazione + modalita' display (via adb, senza root)
# Versione: v0.1.0 - 2026-10-21 18:45 UTC
# Uso:
#   bash tune_v0.1.0_20261021-1845.sh            -> applica tuning + apre dashboard
#   bash tune_v0.1.0_20261021-1845.sh --debloat  -> in piu' disabilita le app inutili
#   URL=http://192.168.1.10:8090 bash tune_...sh -> usa Wi-Fi invece di adb reverse
# Tutto reversibile: "pm enable <pacchetto>" e ripristino scale animazioni a 1.
# =============================================================================
set -u
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
adb shell settings put system user_rotation 1                # landscape

# --- 3. Meno lavoro in background -------------------------------------------
adb shell settings put global wifi_scan_always_enabled 0
adb shell settings put global ble_scan_always_enabled 0 2>/dev/null
adb shell settings put secure location_providers_allowed -gps,-network 2>/dev/null
# limite processi in background: impostarlo a mano in Opzioni sviluppatore
# ("Limite processi in background" = max 2). Non lo si fa via "service call"
# perche' il codice di transazione cambia tra versioni Android (rischioso).

# --- 4. Debloat opzionale (pm disable-user, reversibile con pm enable) ------
if [ "${1:-}" = "--debloat" ]; then
  # Elenco prudente: Samsung/Google non necessari per il display.
  # Nei ROM custom (LineageOS senza GApps) la maggior parte non esiste: ignorato.
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
  org.lineageos.jelly org.lineageos.eleven org.lineageos.snap"
  for p in $PKGS; do
    if adb shell pm list packages "$p" | tr -d '\r' | grep -qx "package:$p"; then
      echo "disable $p"; adb shell pm disable-user --user 0 "$p" >/dev/null 2>&1 || adb shell pm disable "$p"
    fi
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
adb shell am start -a android.intent.action.VIEW -d "$URL"
echo "Fatto. Su Android 5+ fissa la schermata con 'Blocco schermo app' (Sicurezza) per un kiosk."
