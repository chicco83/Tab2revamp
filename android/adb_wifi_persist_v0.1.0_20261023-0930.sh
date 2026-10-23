#!/usr/bin/env bash
# =============================================================================
# Tab2revamp - ADB via Wi-Fi sempre attivo dopo ogni riavvio (LineageOS 14.1)
# Versione: v0.1.0 - 2026-10-23 09:30
# Come funziona:
#   adbd, all'avvio, legge "service.adb.tcp.port" e, se manca, "persist.adb.tcp.port".
#   Le proprieta' "persist.*" sopravvivono al riavvio (/data/property), quindi basta
#   impostarla una volta: nessun servizio o script resta in esecuzione sul tablet.
#   Il toggle "Debug ADB in rete" di LineageOS scrive solo service.adb.tcp.port
#   (non persistente): per questo si spegneva a ogni riavvio.
#   Serve "adb root" (build userdebug, "Accesso root: solo ADB" nelle Opzioni sviluppatore).
#   Sicurezza: la porta 5555 accetta solo PC gia' autorizzati (chiave RSA in adb_keys).
# Uso (tablet gia' collegato via USB o via Wi-Fi):
#   bash adb_wifi_persist_v0.1.0_20261023-0930.sh            -> attiva
#   bash adb_wifi_persist_v0.1.0_20261023-0930.sh --off      -> disattiva (torna come prima)
#   SERIAL=192.168.1.234:5555 bash adb_wifi_persist_...sh    -> sceglie il dispositivo
# =============================================================================
set -u
export MSYS_NO_PATHCONV=1
PORT="${PORT:-5555}"
A() { if [ -n "${SERIAL:-}" ]; then adb -s "$SERIAL" "$@"; else adb "$@"; fi; }

# --- 1. adbd come root (necessario per scrivere persist.adb.tcp.port) --------
A root >/dev/null 2>&1
sleep 4
# via Wi-Fi il riavvio di adbd chiude la connessione: la riapro
case "${SERIAL:-}" in *:*) adb connect "$SERIAL" >/dev/null 2>&1; sleep 2 ;; esac
if [ "$(A shell id -u | tr -d '\r')" != "0" ]; then
  echo "ERRORE: adb root non riuscito (abilitare 'Accesso root: solo ADB')" >&2
  exit 1
fi

# --- 2. Proprieta' persistente -----------------------------------------------
if [ "${1:-}" = "--off" ]; then
  A shell setprop persist.adb.tcp.port ""
  echo "Disattivato: dal prossimo riavvio ADB solo via USB."
else
  A shell setprop persist.adb.tcp.port "$PORT"
  echo "Attivato: persist.adb.tcp.port=$(A shell getprop persist.adb.tcp.port | tr -d '\r')"
  echo "Dopo il riavvio: adb connect <IP-tablet>:$PORT"
fi
