#!/usr/bin/env bash
# =============================================================================
# Tab2revamp - diagnosi Galaxy Tab 2 via adb (SOLO LETTURA, non modifica nulla)
# Versione: v0.1.0 - 2026-10-21 18:45 UTC
# Uso: bash diagnose_v0.1.0_20261021-1845.sh   (Linux o Git Bash su Windows)
# Output: android/logs/diag_<data-ora>.txt  -> baseline prima/dopo il refactor
# =============================================================================
set -u
cd "$(dirname "$0")"
mkdir -p logs
OUT="logs/diag_$(date +%Y%m%d-%H%M%S).txt"

# Esegue un comando sul tablet e lo registra con intestazione
sec() { echo; echo "===== $1 ====="; shift; adb shell "$@" 2>&1 | tr -d '\r'; }

adb get-state >/dev/null 2>&1 || { echo "Nessun dispositivo adb: abilita Debug USB e autorizza il PC."; exit 1; }

{
  echo "Tab2revamp diagnose v0.1.0 - $(date)"
  # --- Identita' dispositivo/ROM: serve a scegliere TWRP/ROM giusti ---------
  sec "Modello / ROM" 'for p in ro.product.model ro.product.device ro.build.version.release ro.build.version.sdk ro.build.display.id ro.bootloader ro.baseband; do echo "$p=$(getprop $p)"; done'
  # --- Root / bootloader / recovery ----------------------------------------
  sec "Root?" 'which su; id'
  # --- Memoria: 1 GB totali, quanto resta libero dopo il boot? -------------
  sec "Memoria" 'cat /proc/meminfo | head -8'
  sec "Meminfo per processo (top)" 'dumpsys meminfo | head -40'
  # --- CPU: chi la occupa a riposo ------------------------------------------
  sec "Top CPU (3 campioni)" 'top -n 3 -m 10 -d 2 2>/dev/null || top -n 1'
  sec "Governor / frequenze" 'cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies 2>&1'
  # --- Storage: eMMC quasi piena = tablet lentissimo -----------------------
  sec "Spazio" 'df'
  sec "eMMC" 'cat /sys/block/mmcblk0/device/name /sys/block/mmcblk0/device/date /sys/block/mmcblk0/queue/scheduler 2>&1'
  # --- Temperatura batteria / stato carica ----------------------------------
  sec "Batteria" 'dumpsys battery'
  # --- App installate: base per il debloat ---------------------------------
  sec "Pacchetti abilitati (sistema)" 'pm list packages -s -e'
  sec "Pacchetti terze parti" 'pm list packages -3'
  # --- Animazioni e impostazioni utili -------------------------------------
  sec "Impostazioni animazioni" 'settings get global window_animation_scale; settings get global transition_animation_scale; settings get global animator_duration_scale'
  # --- Partizioni (utile per backup EFS/IMEI e scelta ROM) ------------------
  sec "Partizioni" 'ls -l /dev/block/platform/*/by-name/ 2>/dev/null || cat /proc/partitions'
  sec "IMEI presente?" 'getprop gsm.version.baseband; getprop ril.serialnumber'
} | tee "$OUT"

echo; echo "Salvato in android/$OUT"
