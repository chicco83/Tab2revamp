#!/usr/bin/env bash
# =============================================================================
# Build di Tab2Sensors.exe (lettore sensori basato su LibreHardwareMonitorLib)
# Versione: v0.1.0 - 2026-10-22 00:40 (ora locale)
# Nessun download: usa csc.exe del .NET Framework 4.x incluso in Windows.
# L'exe viene messo accanto a LibreHardwareMonitorLib.dll (cartella LHM nel
# progetto, esclusa da git) cosi' trova le DLL senza copiarle.
# Uso: bash windows/agent/sensors/build_tab2sensors_v0.1.0_20261022-0040.sh
# =============================================================================
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
LHM="$(ls -d "$HERE"/../../lhm/LibreHardwareMonitor_v* | sort | tail -1)"
CSC=/c/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe
[ -f "$LHM/LibreHardwareMonitorLib.dll" ] || { echo "LibreHardwareMonitorLib.dll non trovata in $LHM"; exit 1; }
cd "$LHM"
MSYS_NO_PATHCONV=1 "$CSC" /nologo /optimize+ /platform:x64 /target:exe /out:Tab2Sensors.exe \
  /reference:LibreHardwareMonitorLib.dll /reference:System.Memory.dll \
  "$(cygpath -w "$HERE/Tab2Sensors.cs")"
# stessi bindingRedirect di LibreHardwareMonitor.exe (System.Memory & co.)
cp LibreHardwareMonitor.exe.config Tab2Sensors.exe.config
echo "OK: $LHM/Tab2Sensors.exe"
