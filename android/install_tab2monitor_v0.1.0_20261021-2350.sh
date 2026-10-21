#!/usr/bin/env bash
# =============================================================================
# Tab2Monitor - installazione e configurazione dell'APK sul tablet (adb)
# Versione: v0.1.0 - 2026-10-21 23:50
# =============================================================================
# Cosa fa:
#   1. adb install -r dell'APK piu' recente in android/tab2monitor_app/dist/
#   2. adb reverse tcp:8090 tcp:8090 (USB; va rifatto a ogni riavvio del tablet)
#   3. primo avvio dell'app (necessario: finche' non e' stata aperta una volta
#      Android non le consegna BOOT_COMPLETED), passando l'URL Wi-Fi di riserva
#   4. (opzione -home) Tab2Monitor come schermata Home: dopo il boot e dopo il
#      tasto Home torna sempre la dashboard
#
# Uso (Git Bash):
#   bash android/install_tab2monitor_v0.1.0_20261021-2350.sh                 # solo USB
#   URL=http://192.168.1.237:8090 bash android/install_tab2monitor_v0.1.0_20261021-2350.sh
#   bash android/install_tab2monitor_v0.1.0_20261021-2350.sh -home           # anche Home
#   bash android/install_tab2monitor_v0.1.0_20261021-2350.sh -nohome         # toglie la Home
# Ripristino: adb uninstall it.tab2revamp.monitor
# =============================================================================
set -euo pipefail
export MSYS_NO_PATHCONV=1            # Git Bash non deve riscrivere /sdcard/... ecc.

PKG=it.tab2revamp.monitor
HERE="$(cd "$(dirname "$0")" && pwd)"
ADB="${ADB:-adb}"
if ! command -v "$ADB" >/dev/null 2>&1; then
    ADB="$(cygpath -u "${LOCALAPPDATA:-}" 2>/dev/null)/Android/Sdk/platform-tools/adb.exe"
fi
APK="$(ls "$HERE"/tab2monitor_app/dist/Tab2Monitor_v*.apk 2>/dev/null | sort -V | tail -1 || true)"
[ -n "$APK" ] || { echo "APK non trovato: lanciare prima tab2monitor_app/build_tab2monitor_*.sh"; exit 1; }
# adb.exe vuole un percorso Windows (MSYS_NO_PATHCONV disattiva la conversione automatica)
APK_W="$(cygpath -m "$APK" 2>/dev/null || echo "$APK")"

echo "APK: $APK"
"$ADB" wait-for-device
"$ADB" install -r "$APK_W"
"$ADB" reverse tcp:8090 tcp:8090 || echo "adb reverse non riuscito (Android < 5?): usare URL Wi-Fi"

# niente fumetto "Visualizzazione a schermo intero - scorri per uscire" al primo avvio
"$ADB" shell settings put secure immersive_mode_confirmations confirmed
# Jelly (dashboard web) non serve piu': chiuderlo libera ~60-100 MB
"$ADB" shell am force-stop org.lineageos.jelly || true

EXTRA=""
[ -n "${URL:-}" ] && EXTRA="--es url ${URL}"
"$ADB" shell am start -n $PKG/.MainActivity $EXTRA

case "${1:-}" in
  -home)
    "$ADB" shell pm enable $PKG/.HomeAlias
    "$ADB" shell cmd package set-home-activity $PKG/.MainActivity || true
    echo "Tab2Monitor impostata come Home" ;;
  -nohome)
    "$ADB" shell pm disable $PKG/.HomeAlias
    echo "Home di Tab2Monitor disattivata (torna Trebuchet)" ;;
esac
echo "Fatto."
