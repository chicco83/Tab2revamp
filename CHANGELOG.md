# CHANGELOG — Tab2revamp
Versione documento: v0.1.2 — 2026-10-21 21:50 UTC

## 3. v0.1.2 — 2026-10-21 21:50 UTC (sessione locale, tablet su adb)
- Prima prova reale sul tablet: GT-P5100 stock **4.2.2 JDQ39.P5100XXDMJ2** (SDK 17), senza root.
  Server + dashboard via Wi-Fi (`http://192.168.1.237:8090`) **funzionanti** in Chrome 71.
- Aggiunto `android/diagnose_v0.1.2_20261021-2150.sh`: `MSYS_NO_PATHCONV=1` (Git Bash
  riscriveva `/sys/...` in `C:/Program Files/Git/...`), `head`/`which` spostati sul PC
  (assenti sullo stock), aggiunti CID eMMC, kernel, pacchetti disattivati.
- Aggiunto `android/tune_v0.1.2_20261021-2150.sh`: `user_rotation 0` (con 1 lo schermo
  andava in verticale), timeout su ogni `adb shell` (su 4.2.2 `pm disable-user` si blocca
  senza errore), debloat saltato senza root su SDK < 18 e via `su -c pm disable` se c'e'
  root, lista debloat allargata ai pacchetti Samsung trovati, apertura dashboard in Chrome.
- I file v0.1.0 restano come versione precedente. Rollback: usare gli script v0.1.0
  (oppure `git revert <commit>`).

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
