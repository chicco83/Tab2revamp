# CHANGELOG — Tab2revamp
Versione documento: v0.1.9 — 2026-10-22 01:00

## 10. (2) v0.1.9 — 2026-10-22 01:15 (sessione locale, APK v0.4.1)
- APK **v0.4.1**: provata con i **dati reali** del server v0.2.2 (Claude, I/O per disco, ventole e
  temperature di scheda madre Nuvoton, Ryzen, RAM, SSD/HDD). Scartate le voci che sono soglie fisse e
  non misure ("... Limit", "... Resolution", "Warning/Critical Temperature").
- Aggiunto `android/tab2monitor_app/build_tab2monitor_v0.4.1_20261022-0110.sh` (versionCode 5).

## 10. v0.1.9 — 2026-10-22 01:00 (sessione locale, APK v0.4.0)
- APK **Tab2Monitor v0.4.0**: pagine a rotazione ("Sistema" / "GPU e sensori") con timer nelle
  impostazioni; tocco = pagina successiva; icona ingranaggio → impostazioni; una scheda per ogni GPU
  (campo `gpus` del server v0.2.x) con indicatori a lancetta di carico e saturazione VRAM, temperatura,
  ventola, potenza; temperature e ventole raggruppate per componente (campo `sensors.hw`) con icona
  termometro e ventola animata (velocità ∝ RPM, livello `FanOverlay` separato); indicatori a lancetta
  anche su CPU e RAM; I/O per disco (campi `r`/`w` in `disks`, da aggiungere al server); pagina GPU più
  lunga se una GPU è sotto carico (`gpu_busy_pct`, `gpu_busy_sec`); interruttore "Ventole animate".
- Aggiunto `android/tab2monitor_app/build_tab2monitor_v0.4.0_20261022-0045.sh` (versionCode 4).
- Provata sul tablet con il server reale v0.2.0 e con un mock (porta 8091) per RPM, dischi e Claude.
  CPU dell'app: 2% senza animazione, 17–21% con ventole animate.
- Richiesto alla sessione «Sorgente dati Windows»: I/O per disco (`disks[].r/w`).
- Rollback: `git checkout 5050dbb -- android/tab2monitor_app` e build v0.3.0 (poi `adb uninstall` +
  install, il versionCode scende).

## 9. v0.1.8 — 2026-10-22 00:30 (sessione locale, APK v0.3.0)
- APK **Tab2Monitor v0.3.0**: pannello "Claude - utilizzo" (blocco 5 h, oggi, output, cache letta,
  7 giorni, modello principale), interruttore `p_claude` nelle impostazioni. Legge il campo opzionale
  `claude` di `/api/metrics` (schema in CONTEXT.md), richiesto alla sessione «Sorgente dati Windows».
- Aggiunto `android/tab2monitor_app/build_tab2monitor_v0.3.0_20261022-0025.sh` (versionCode 3).
- Installata e provata con dati finti (mock locale); dati reali in attesa del server. Rollback: `git checkout 2e6ed56 --
  android/tab2monitor_app/src` e build v0.2.0.

## 8. v0.1.7 — 2026-10-22 00:15 (sessione locale, APK v0.2.0)
- APK **Tab2Monitor v0.2.0**: pannello impostazioni (`SettingsActivity`, pressione lunga sulla
  dashboard) con interruttori per le sorgenti USB / Wi‑Fi (+ URL), per ognuno dei 9 pannelli, per
  l'avvio al boot e l'intervallo di aggiornamento. `MonitorView`: layout a pannelli attivabili
  (colonne vuote eliminate, pannelli elastici), schermate "nessuna sorgente" e "pannelli spenti".
  `MainActivity`: `--ez` accetta tutte le chiavi (`src_usb`, `p_gpu`, ...). Vecchio layout e righe
  sostituite lasciati come commento datato.
- Aggiunto `android/tab2monitor_app/build_tab2monitor_v0.2.0_20261022-0010.sh` (versionCode 2).
- Provata sul tablet. Rollback: `git checkout 62832f3 -- android/tab2monitor_app/src android/tab2monitor_app/AndroidManifest.xml`,
  build con `build_tab2monitor_v0.1.0_*`, poi `adb uninstall it.tab2revamp.monitor` e reinstallare
  (il versionCode scende, `install -r` non basta).
- Aperta una sessione parallela per la sorgente dati lato Windows.

## 8. (3) v0.2.2 — 2026-10-22 00:40 (sessione locale, server Windows)
- Nuovo `windows/agent/tab2monitor_server_v0.2.2_20261022-0040.py`: `disks[].r`, `disks[].w` (byte/s) e
  `disks[].busy` (% tempo attivo = 100 − % Idle Time) per volume, contatori PDH `LogicalDisk`
  (chiesto dalla sessione APK per l'APK v0.4.0). Volumi senza contatori (G: Google Drive): campi omessi.
- Provato: 4 s di scritture su C: → `C:\ r 143 MB/s w 338 MB/s busy 15,9%`, coerente con `disk_io`
  totale; 8090 riavviata sulla v0.2.2 dal supervisore.
- Rollback: spostare `tab2monitor_server_v0.2.2_*.py` fuori dalla cartella e chiudere il server.

## 8. (2) v0.2.1 — 2026-10-22 00:30 (sessione locale, server Windows)
- Nuovo `windows/agent/tab2monitor_server_v0.2.1_20261022-0130.py`: campo `claude` (token di Claude Code
  di oggi, del blocco di 5 h e degli ultimi 7 giorni, modelli) contato dai log locali
  `%USERPROFILE%\.claude\projects\**\*.jsonl`, scansione incrementale ogni 45 s, dedup per
  message.id+requestId. Si leggono solo usage/model/timestamp; niente testo, niente credenziali.
  Chiesto dalla sessione APK, approvato dall'utente.
- Provato: `curl` sulla 8093 e poi sulla 8090 (riavvio fatto dal supervisore), prima scansione 1,5 s.
- ⏳ Dati dell'account (limiti di piano): l'utente li vuole, ma leggere `.credentials.json` è stato
  bloccato dal controllo dei permessi → decide l'utente (permesso esplicito o rinuncia).
- Rollback: chiudere il server; il supervisore riparte con la versione più alta, quindi spostare
  `tab2monitor_server_v0.2.1_*.py` fuori dalla cartella per tornare alla v0.2.0.

## 8. v0.2.0 — 2026-10-22 00:20 (sessione locale, server Windows)
- Nuovo `windows/agent/tab2monitor_server_v0.2.0_20261022-0015.py`: HTTP/1.1 keep-alive, `adb reverse`
  automatico ogni 5 s, frequenza reale per core e coda CPU (PDH), carico/VRAM/temperatura per GPU
  (PDH + DXGI + D3DKMT, anche la MI50 "Radeon Pro VII" a 35 °C, senza admin), tutte le temperature e
  ventole raggruppate per hardware con abbinamento ventola→temperatura (`sensors.pairs`, `fan_map_*.json`).
- Sensori via `Tab2Sensors.exe` (`windows/agent/sensors/Tab2Sensors.cs`, compilato con csc di Windows)
  che usa `LibreHardwareMonitorLib.dll` v0.9.6 in `windows/lhm/` (scaricata da GitHub, SHA256
  `086d9f1b…c001` verificato, esclusa da git): nessuna app LHM da tenere aperta.
- Avvio automatico: `install_tab2monitor_service_v0.2.0_20261022-0055.cmd` (attività pianificata
  all'accesso, privilegi elevati, supervisore `tab2monitor_service_v0.2.0_*.py`). **Non ancora installata.**
- Provato dal vivo: `/api/metrics` v0.2.0 sulla 8090, keep-alive (curl "Reusing existing connection"),
  `adb reverse --remove-all` → rifatto da solo in ≤5 s, tablet connesso. Campi v0.1.0 invariati.
- FanControl valutato e scartato: closed source, interfaccia sopra la stessa LibreHardwareMonitorLib,
  i plugin aggiungono sensori a FanControl ma non li esportano; servirebbe un altro client sempre aperto.
- Rollback: `start_tab2monitor_v0.1.0_20261021-1845.cmd` (il server v0.1.0 resta nella cartella;
  per farlo scegliere dal .cmd v0.2.0 spostare il file v0.2.0).

## 7. v0.1.6 — 2026-10-22 00:10 (sessione locale, modifica /system approvata dall'utente)
- HAL sensori disattivato: `/system/lib/hw/sensors.omap4.so` → `sensors.omap4.so.off`
  (backup `backup/lineage14.1_20200623/sensors.omap4.so.orig`, MD5 `b46bfa9921fa7a8ccf09a29d7b251539`).
- Verificato dopo il riavvio: "No Sensors on the device", nessun crash, SensorService non più in `top`,
  Tab2Monitor avviata al boot via Wi‑Fi.
- Rollback: `adb root; adb remount; adb shell mv /system/lib/hw/sensors.omap4.so.off /system/lib/hw/sensors.omap4.so; adb reboot` (oppure `adb push backup/lineage14.1_20200623/sensors.omap4.so.orig /system/lib/hw/sensors.omap4.so`, MD5 `b46bfa99…1539`).

## 6. (2) v0.1.5 — 2026-10-22 00:05 (sessione locale)
- Avvio al boot dell'APK **verificato**: dopo il riavvio Tab2Monitor è in primo piano e legge i dati
  via Wi‑Fi. adb era solo lento a ricomparire (~3–4 min dal riavvio), non un guasto.
- SensorService a 0% in questo boot (il bug dell'HAL dipende da memoria non inizializzata).

## 6. v0.1.5 — 2026-10-21 23:55 (sessione locale, APK nativa)
- Aggiunta APK nativa **Tab2Monitor v0.1.0** in `android/tab2monitor_app/` (package
  `it.tab2revamp.monitor`, minSdk 25): `MainActivity` (schermo intero immersive, schermo sempre
  acceso, polling `/api/metrics` USB → Wi‑Fi), `MonitorView` (Canvas, stesso layout della dashboard
  web), `BootReceiver` (avvio al boot), `HomeAlias` opzionale (kiosk, disattivato).
- Aggiunti `android/tab2monitor_app/build_tab2monitor_v0.1.0_20261021-2350.sh` (build senza Gradle,
  JDK di Android Studio, nessun download) e `android/install_tab2monitor_v0.1.0_20261021-2350.sh`.
- Provata sul tablet: USB ✅, ripiego Wi‑Fi ✅, PSS 14,5 MB (Jelly 64 MB). Avvio al boot: richieste
  dal tablet viste sul PC dopo il riavvio, ma adb non ha più visto il tablet (da verificare).
- Wi‑Fi del tablet riacceso (`svc wifi enable`), IP 192.168.1.234.
- SensorService al 50%: causa trovata con strace (pollfd non inizializzata nell'HAL
  `sensors.omap4.so` → `ppoll` in loop con POLLNVAL); fix proposto, non eseguito.
- `.gitignore`: esclusi `android/tab2monitor_app/build/`, `dist/`, `*.apk`.

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
