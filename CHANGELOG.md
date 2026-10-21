# CHANGELOG — Tab2revamp
Versione documento: v0.1.1 — 2026-10-21 19:05 UTC

## 2. v0.1.1 — 2026-10-21 19:05 UTC (sessione cloud)
- CONTEXT.md: aggiunte istruzioni e prompt di handoff per la sessione locale con adb
  (la sessione cloud non puo' avviare sessioni sul PC dell'utente).

## 1. v0.1.0 — 2026-10-21 18:45 UTC (sessione cloud)
- Analisi: scartati spacedesk e postmarketOS, scelta Android leggero + dashboard web.
- Aggiunto `windows/agent/tab2monitor_server_v0.1.0_20261021-1845.py`
  (CPU/core/RAM/swap/dischi/I-O/rete/uptime/top processi; GPU e temperature da LibreHardwareMonitor).
- Corretto in sviluppo: euristica GPU che scambiava CPU AMD Ryzen per GPU (ora usa icona LHM).
- Aggiunto `windows/agent/start_tab2monitor_v0.1.0_20261021-1845.cmd`, `requirements.txt`.
- Aggiunto `dashboard/dashboard_v0.1.0_20261021-1845.html` (ES5, 1280x800, tema scuro).
- Aggiunti `android/diagnose_v0.1.0_20261021-1845.sh` e `android/tune_v0.1.0_20261021-1845.sh`.
- Aggiunti CLAUDE.md, CONTEXT.md, CHANGELOG.md, MANUAL.md.
