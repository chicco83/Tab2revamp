# CHANGELOG — Tab2revamp
Versione documento: v0.1.4 — 2026-10-21 23:45 UTC

## 5. v0.1.4 — 2026-10-21 23:45 UTC (sessione locale, flash eseguito)
- Backup completo stock (EFS incluso, MD5 verificati) in `backup/` (fuori da git).
- TWRP 3.4.0-0 flashato dall'utente con Odin; LineageOS 14.1 andi34 installata
  (1° tentativo E1001 per `/cache` non montata, poi OK). Tablet avviato, adb ok.
- Aggiunto `android/tune_v0.1.3_20261021-2315.sh`: Jelly non piu' disattivato (era l'unico
  browser senza GApps), apertura esplicita in Jelly, lista debloat LineageOS (21 app).
- `/system/etc/init/rild.rc`: servizio `ril-daemon` disattivato (loop al 50% CPU).
  Originale salvato in `backup/lineage14.1_20200623/rild.rc.orig`.
- Aperto: SensorService al 50% dopo il boot. Proposta APK nativa da valutare.

## 4. v0.1.3 — 2026-10-21 22:15 UTC (sessione locale)
- Decisione utente: niente root dello stock, si passa direttamente alla ROM custom.
- Scelta ROM: LineageOS 14.1 unofficial andi34 `lineage-14.1_espresso3g-7.1.2-20200623-1945.zip`
  (motivi e alternative scartate in CONTEXT.md). Nessun flash eseguito.
- TWRP 3.4.0-0 fornito dall'utente: SHA256 verificato contro twrp.me. Odin 3.14.4 non firmato.
- Aggiunto `.gitignore` di radice: binari di flash (`*.tar`, `*.img`, `*.zip`, `Odin3*/`, `backup/`) fuori dal repo.

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
