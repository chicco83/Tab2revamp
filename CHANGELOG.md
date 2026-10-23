# CHANGELOG — Tab2revamp
Versione documento: v0.1.14 — 2026-10-22 01:35 (ora del PC)


## 20. v0.1.17 (3) — 2026-10-23 09:10 (sessione locale)
- Analisi della carica dopo una notte sul caricatore da muro 3 A (67% → 13%). Kernel installato = andi34 `3.0/common`:
  imposta 1,8 A per ogni caricatore; registri SMB347 letti via I2C = configurazione corretta, carica rapida attiva,
  nessun errore. Nessun bug del driver trovato. Corrente batteria −660…−800 mA a schermo acceso, +150 mA spento:
  arriva circa 0,5 A. Sospetti: cavo/connettore/caricatore (da provare). Dettagli in CONTEXT, Batteria / carica.
- Nuovo `android/tools/smbdump_v0.1.0_20261023-0905.c`: lettore I2C di sola lettura (compilato con l'NDK, serve `adb root`).
- Correzione della voce 19: con questo kernel `plug=usb` non limitava a 500 mA.

## 19. v0.1.17 (2) — 2026-10-22 13:50 (sessione locale)
- Tablet spento per batteria scarica mentre era sul caricatore da muro: dallo storico batteria risulta 62% → 18% in ~2 h 50 min
  con il cavo attaccato, poi spento a 3,05 V con il 18% indicato. Il caricatore era riconosciuto come `plug=usb`
  (~500 mA), non `ac`: a schermo acceso non basta (CONTEXT, Batteria / carica). Dopo il riavvio risultava `ac`. Ricollegato in Wi-Fi su `192.168.1.234:5555` con il caricatore a muro.
  Nota: `192.168.1.206:5555` è la Mi Box 4, non il tablet. Nessun codice toccato.
- 23:44: dopo un altro riavvio, anche con `plug=ac` la carica si ferma dopo 40 s e il livello scende (67% → 61% in 20 min,
  schermo acceso). La percentuale salta tra un riavvio e l'altro (18 → 39 → 67%): non è affidabile.
  "Debug ADB in rete" si era spento ed è stato riacceso a mano.

## 18. v0.1.17 — 2026-10-22 12:05 (sessione locale)
- Ricerca online sulla sostituzione del connettore 30 pin con USB-C: nessun kit/guida esistente; raccolti pinout e punti aperti in
  `RICERCA_USBC_v0.1.0_20261022-1205.md`. Foto del pinout salvata in `docs/pinout_30pin_galaxy_tab_droidangel.jpg`. Nessun codice toccato.
- Seguito della ricerca: schema di riconoscimento del caricatore (corto D+/D− o partitore ~1,2 V, fonti forum) e piano di prova nella stessa nota.
- Aggiunti lista componenti, schema e sequenza di prova per un cavo di prova USB-C → 30 pin (progetto, non costruito).
- Idea «alimentare dai contatti batteria»: dal service manual risultano SMB347S (carica), MAX17042G (fuel gauge), TWL6032 (PMIC), segnali VF/CONTACT/BAT_REMOVAL; pinout del connettore ancora da trovare nello schema (Badcaps / s-manuals).
- Scaricato lo schema (s-manuals.com, non nel repo): connettore batteria BTC700 = pin 1-2 V_BATT, 3 VF (ADC TWL6032 + MAX17042 AIN), 4-5 GND, 6-9 NC; ritagli in `docs/p5100_schema_pag7_*.png`; piano di prova con alimentatore da banco nella nota.

## 17. (6) v0.1.16 — 2026-10-22 23:58 (sessione locale, verifica APK v0.9.6)
- APK **v0.9.6 installata** sul GT-P5100 (via ADB Wi-Fi 192.168.1.234:5555) e **verificata con i dati reali**
  del server v0.2.9: "aggiornato alle 19:23", Limite di sessione 19%, Settimanale 16% "si azzera gio 09:00".
- Il ripiego si era fermato alle 17:55: l'app Claude Desktop ha spostato i dati in
  `%LOCALAPPDATA%\Claude-Data\<pacchetto>\` (resta solo `.user-data-left` nella vecchia cartella);
  corretto lato server (v0.2.9, cerca prima in Claude-Data e usa il file più recente).

## 17. (5) v0.1.16 — 2026-10-22 11:35 (sessione locale, APK v0.9.6)
- Ricerca del token dell'app Claude Desktop (richiesta utente): `%APPDATA%\Claude\config.json`, chiavi
  `oauth:tokenCache`/`oauth:tokenCacheV2`, **cifrate con DPAPI** (prefisso v10, Electron safeStorage):
  non usate (valori non letti). Trovato invece `%APPDATA%\Claude\plan-usage-history.json` con le
  percentuali del piano senza credenziali (`fh` = 5 h, `sd` = 7 giorni; ultimo campione 10:49 = 4% / 1%),
  campionate in modo irregolare e senza orari di reset. **Decisione utente**: usarlo come ripiego quando
  manca il token della CLI (richiesto alla sessione «Sorgente dati Windows»).
- APK **v0.9.6**: `claude_plan.src` ("oauth"/"history"), `ts` e `session.stale` → "aggiornato alle HH:MM" e
  "Limite di sessione (dato vecchio)". Aggiunto `build_tab2monitor_v0.9.6_20261022-1130.sh` (versionCode 17).
  ⏳ da provare con i dati reali del server.

## 17. (4) v0.1.16 — 2026-10-22 11:10 (sessione locale, APK v0.9.5)
- APK **v0.9.5** (richiesta utente con screenshot di claude.ai): pannello Claude con i **limiti del piano**
  — barre "Limite di sessione" (5 h, "si ripristina tra X h Y min") e "Settimanale - tutti i modelli"
  ("si azzera gio 09:00"), percentuale, tacca bianca = tempo trascorso nella finestra; sotto i token di
  Claude Code. Legge il campo opzionale `claude_plan` (schema in CONTEXT.md); senza, il pannello è come prima.
- Provato con un mock (porta 8091) con i valori dello screenshot: resa come nello screenshot.
- ⏳ **Dati reali**: richiedono che il server legga il token OAuth in `~/.claude/.credentials.json` e
  chiami un endpoint non documentato: in attesa della decisione esplicita dell'utente.
- Batteria del tablet al 25% alle 11:07 (61% alle 01:30) sulla USB del PC.
- Aggiunto `android/tab2monitor_app/build_tab2monitor_v0.9.5_20261022-0900.sh` (versionCode 16).

## 17. (3) v0.1.16 — 2026-10-22 08:50 (sessione locale, APK v0.9.4)
- APK **v0.9.4**: gauge check più lungo (salita 1,0 s + discesa 1,3 s; era 0,7 + 0,9) e log `Tab2Gauge`.
- Verificato sul tablet: 2 cambi pagina → nessun gauge check (log vuoto, valori reali a schermo);
  spegnimento + riaccensione → un gauge check. Lo avviano solo SCREEN_ON, la fine dell'oscuramento e il
  primo avvio dell'app.
- Aggiunto `android/tab2monitor_app/build_tab2monitor_v0.9.4_20261022-0845.sh` (versionCode 15).

## 17. (2) v0.1.16 — 2026-10-22 08:30 (sessione locale, APK v0.9.3)
- APK **v0.9.3**: rotazione manuale **provata** (`flip180` → `mCurrentRotation=2`, spenta → 0).
- Tentata la rotazione automatica leggendo l'accelerometro BMA254 da
  `/sys/class/sensors/accelerometer_sensor/raw_data` (`x, y, z`, ~1024 = 1 g; da adb funziona:
  `-16, -20, 1016` col tablet in piano) con interruttore `auto_rot` e pulsante di calibrazione: **SELinux
  la blocca** per le app (`avc: denied { read } ... untrusted_app ... sysfs`). Il codice resta (mostra
  "Accelerometro non leggibile dall'app: usa l'opzione manuale"); per la decisione dell'utente
  ("se non abbiamo il sensore, opzione manuale") vale l'opzione manuale.
- Aggiunto `android/tab2monitor_app/build_tab2monitor_v0.9.3_20261022-0425.sh` (versionCode 14).

## 17. v0.1.16 — 2026-10-22 04:10 (sessione locale, APK v0.9.2)
- APK **v0.9.2**: opzione "Capovolgi l'interfaccia di 180 gradi" (`flip180`, impostazioni o
  `am start --ez flip180 true`), orientamento REVERSE_LANDSCAPE. Rotazione automatica non possibile
  finché il driver dei sensori resta disattivato (scelta v0.1.6 per SensorService al 50%); in verifica
  se l'accelerometro BMA254 è leggibile direttamente dal kernel.
- Aggiunto `android/tab2monitor_app/build_tab2monitor_v0.9.2_20261022-0410.sh` (versionCode 13).
- ⏳ Compilata, **non ancora installata né provata**: tablet non raggiungibile via adb.
## 16. v0.1.15 — 2026-10-22 01:45 ora del PC (sessione «Ricerca hub USB comandabile», solo documenti)
- Aggiunto `RICERCA_HUB_USB_v0.1.0_20261022-0142.md`: confronto di 7 opzioni per limitare la carica del tablet
  (presa Shelly/Tasmota, relè USB LCUS-1, Yepkit YKUSH3, Acroname, Cambrionix, uhubctl) con fonti.
- **Raccomandazione**: Shelly Plug S Gen3 (~24 €, API HTTP locale) + caricatore Samsung 2 A attuale; hub professionali sconsigliati
  (costo alto e corrente/firma di carica del Tab 2 su porta dati non documentata). YKUSH3 ha `ykushcmd.exe` per Windows (verificato dalla guida Yepkit).
- Non verificato sull'hardware; nessun acquisto, nessun codice modificato. Rollback: `git rm RICERCA_HUB_USB_v0.1.0_20261022-0142.md`.

## 15. v0.1.14 — 2026-10-22 01:35 ora del PC (sessione «Piano installer Tab2Monitor», solo documenti)
- Aggiunto `PIANO_INSTALLER_v0.1.0_20261022-0135.md`: piano (non implementato) per usare server e APK
  su **altri PC Windows** e **altri tablet Android**. Contiene inventario PC/tablet, cablature da
  sganciare, opzioni con pro/contro (Python embeddable + script/Inno Setup, avvio all'accesso o
  all'avvio, adb opzionale, chiave di release, minSdk 21, scoperta UDP, sideload da `/apk`),
  requisiti minimi dei tablet, sicurezza, 8 passi ⏳ e 11 domande per l'utente.
- Trovato durante l'analisi (verificato sul PC): il driver **PawnIO** (servizio kernel, in
  `C:\Program Files\PawnIO`) è una dipendenza non documentata dei sensori CPU/scheda madre; le regole
  firewall esistenti sono "python.exe, profilo Pubblico, qualsiasi porta"; l'attività elevata esegue
  `.py` da Google Drive (cartella scrivibile dall'utente → rischio di esecuzione come admin).
- Trovato nel codice: su Android 10+ `BootReceiver` non può avviare l'Activity (blocco avvii dal
  background) → avvio automatico solo in modalità Home.
- Nessun file di codice modificato. Rollback: `git rm PIANO_INSTALLER_v0.1.0_20261022-0135.md`.

## 14. (2) v0.1.13 — 2026-10-22 03:55 (sessione locale, APK v0.9.1 + misure batteria)
- APK **v0.9.1**: nell'intestazione la batteria del tablet (livello, freccia carica/scarica, temperatura);
  rossa se il cavo è collegato ma la batteria scende. Aggiunto `build_tab2monitor_v0.9.1_20261022-0350.sh`.
- Temperature del tablet: l'unica in gradi è quella della batteria (32 °C); il kernel non espone la CPU
  OMAP4 (nessun thermal_zone, niente omap_temp_sensor), la EMIF dà solo un livello (3 = normale).
- **Batteria sulla USB del PC**: schermo acceso −730 mA (~ −10%/h), schermo spento +80…+156 mA
  (~ +2%/h); stato sempre "Discharging" (USB 500 mA). In pari solo con schermo spento ≥ 83% del tempo.
- Nessun nodo del kernel per sospendere la carica (solo nodi Samsung `charging_source`,
  `batt_lp_charging`, scrivibili da root, non pensati per questo): l'app non può limitare la carica da sola.

## 14. v0.1.13 — 2026-10-22 03:35 (sessione locale, APK v0.9.0)
- APK **v0.9.0** (richieste utente):
  - **animazioni fluide**: anelli, numeri, colonnine dei core e indicatori GPU passano al nuovo valore
    con easeInOut nell'intervallo di polling; storico e mini-grafici scorrono a sinistra in modo continuo;
    ridisegno a vsync (`postInvalidateOnAnimation`) solo durante l'animazione; interruttore `anim_smooth`.
  - **due livelli di disegno**: la parte fissa (schede, testi, elenchi, intestazione) su una Bitmap
    ridisegnata una volta per campione, la parte animata sopra a ogni frame. Misure (gfxinfo, 10 s):
    tutto ridisegnato a ogni frame 53 ms/frame mediani, 47% CPU → due livelli 36 ms (pag. 1, 32% CPU)
    e 34 ms (pag. 2, 19% CPU).
  - **carico del tablet** (CPU da `/proc/stat`, RAM da ActivityManager) in piccolo accanto a "USB".
  - **gauge check** all'accensione dello schermo (SCREEN_ON e fine oscuramento): indicatori a 100% in
    0,7 s e ritorno al valore reale in 0,9 s; interruttore `gauge_check`. Verificato l'avvio
    dell'animazione (fotogramma a +0,5 s con tutti i valori ~0); il picco al 100% non è catturabile con
    screencap (troppo lento), da vedere dal vivo.
  - anelli: il gradiente torna verde alla fine, così il cappuccio di un anello quasi vuoto non è rosso.
- Aggiunto `android/tab2monitor_app/build_tab2monitor_v0.9.0_20261022-0310.sh` (versionCode 11).
- Aperta la sessione «Piano installer Tab2Monitor (PC + tablet)» (richiesta utente: installer per altri
  PC e altri tablet, in sessione dedicata).
- Rollback: `git checkout 09cbb05 -- android/tab2monitor_app` e build v0.8.0 (uninstall + install).

## 13. v0.1.12 — 2026-10-22 02:55 (sessione locale, APK v0.7.0 + v0.8.0)
- **APK v0.7.0 — grafica**: ricerca online dei pannelli hardware commerciali (AIDA64 SensorPanel,
  NZXT Kraken LCD, Corsair Xeneon Edge, Lian Li Universal Screen, Thermalright Trofeo Vision, temi
  Turing Smart Screen). Ripresi: un colore per componente con striscia laterale e titolo colorato,
  anelli spessi a gradiente verde→giallo→rosso con estremi arrotondati, numero grande + unità piccola,
  temperatura CPU al centro dell'anello (come il Kraken), mini-grafici con area sfumata (CPU, RAM,
  storico). Provata con screenshot.
- **APK v0.8.0 — carico e allarmi** (richiesta utente): il servizio legge `/api/metrics` ogni 2 s anche
  a schermo spento; **carico oltre soglia** (CPU 60%, GPU 50%, RAM 90%, VRAM 90% — configurabili) conta
  come presenza: riaccende lo schermo e lo tiene acceso finché dura. **Allarme vocale** (TextToSpeech
  Pico, italiano, flusso "allarme") quando una temperatura supera la soglia: soglia generale (85 °C) e
  soglia per ogni sensore nelle impostazioni (vuoto = generale, 0 = nessun allarme); ripetuto ogni 60 s,
  isteresi 3 °C, riaccende lo schermo; pulsante "Prova voce". Allarmi spenti di default.
- Provato: rilevamento carico ("sotto carico: AMD Radeon RX 9070 XT VRAM 93%"); allarme reale con
  soglia 65 °C → voce "Attenzione. GPU Memory, AMD Radeon RX 9070 XT, 68 gradi." (poi soglia 85 e
  allarmi spenti).
- **Pico era disattivato dal debloat** (`tune_v0.1.3`): riattivato (`pm enable com.svox.pico`,
  `tts_default_synth`); aggiunto `android/tune_v0.1.4_20261022-0250.sh` che non lo disattiva più.
- Aggiunti `build_tab2monitor_v0.7.0_20261022-0230.sh` (versionCode 9) e
  `build_tab2monitor_v0.8.0_20261022-0245.sh` (versionCode 10).
- Rollback: `git checkout 4fb4032 -- android/tab2monitor_app` e build v0.6.0 (uninstall + install).

## 12. (2) v0.1.11 — 2026-10-22 01:15 (sessione locale, verifiche)
- Presenza: **l'utente conferma** che lo schermo si riaccende quando passa davanti al tablet.
- Con il server v0.2.6 (servizio con admin) le ventole che girano sono nel box CPU (screenshot).

## 12. v0.1.11 — 2026-10-22 02:05 (sessione locale, APK v0.6.0)
- APK **v0.6.0**, richiesta utente: **schermo acceso/spento in base alla presenza** rilevata dalla
  fotocamera frontale, come opzione configurabile (spenta di default). Nuovi `PresenceService`
  (servizio in primo piano: fotocamera frontale S5K6A1 a 160x120 / 5 fps, movimento per differenza di
  luminanza + volti con il rilevamento hardware dell'HAL TI), `AdminReceiver` + `res/xml/device_admin.xml`
  (solo `force-lock`, per `lockNow()`), finestra con SHOW_WHEN_LOCKED/DISMISS_KEYGUARD/TURN_SCREEN_ON,
  oscuramento di ripiego senza amministratore. Impostazioni: interruttore, ritardo (default 120 s),
  sensibilità, pulsante "Attiva spegnimento reale", stato. Manifest: CAMERA, WAKE_LOCK.
- Correzione dopo la prova: spegnendo lo schermo cambia la luce e la fotocamera "vedeva" movimento
  (riaccensione dopo 5 s) → 5 s di pausa dopo lo spegnimento e 2 fotogrammi consecutivi.
- Provato sul tablet: fotocamera aperta (CPU app 3% + cameraserver 1%), spegnimento reale dopo il
  ritardo ✅, riaccensione su movimento con dashboard sopra il blocco schermo in ~30 ms ✅ (evento
  `screen_toggled 1` + `am_resume_activity`), nessun falso allarme dopo la correzione ✅.
- Sul tablet: permesso CAMERA concesso (`pm grant`), amministratore attivato
  (`dpm set-active-admin`), opzione presenza **attiva** con ritardo 120 s.
- Aggiunto `android/tab2monitor_app/build_tab2monitor_v0.6.0_20261022-0200.sh` (versionCode 8).
- Rollback: `adb shell dpm remove-active-admin it.tab2revamp.monitor/.AdminReceiver`, poi
  `git checkout 0e78597 -- android/tab2monitor_app` e build v0.5.1 (uninstall + install).

## 11. (2) v0.1.10 — 2026-10-22 01:45 (sessione locale, APK v0.5.1)
- APK **v0.5.1**: salta temperature e ventole con `"hidden": true` (scelta "non mostrare" nella pagina
  `http://<PC>:8090/config` del server v0.2.4, che gestisce anche i nomi personalizzati: l'APK mostra
  sempre `name`). Campo `hidden` chiesto alla sessione «Sorgente dati Windows»: finché manca, si
  mostra tutto. Installata, avvio ok; **filtro non ancora provato** (il server non invia `hidden`).
- Aggiunto `android/tab2monitor_app/build_tab2monitor_v0.5.1_20261022-0145.sh` (versionCode 7).

## 11. v0.1.10 — 2026-10-22 01:35 (sessione locale, APK v0.5.0)
- APK **v0.5.0**, richiesta utente: temperature **in box per hardware assieme alle relative ventole**.
  Pagina 2 = schede GPU (con tutti i loro sensori: GPU Core/Memory/Hot Spot, ventola, potenza) +
  box su due sotto-colonne: "CPU - <nome>", "Scheda madre - <nome>", "RAM" (tutti i DIMM), "Dischi"
  (riga = nome del disco), altri hardware ciascuno nel suo box. Le ventole vanno nel box dell'hardware
  collegato secondo `sensors.pairs` (es. ventole della scheda madre → CPU con il server v0.2.3).
  Raggruppamento fatto nel thread di polling (`Metrics.buildBoxes`), non in `onDraw`.
- Scheda GPU alta fino a 360u (era 280u: la 9070 XT perdeva ventola e potenza).
- Aggiunto `android/tab2monitor_app/build_tab2monitor_v0.5.0_20261022-0130.sh` (versionCode 6).
- Provata sul tablet con i dati reali (server v0.2.2): tutti i sensori visibili, nessun taglio.
- Rollback: `git checkout 690ff87 -- android/tab2monitor_app` e build v0.4.1.

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

## 8. (10) v0.2.9 — 2026-10-23 00:00 (sessione locale, percorso Claude Desktop e adb reverse)
- Problema (segnalato dalla sessione APK): dalle 17:55 del 2026-10-22 Claude Desktop tiene i dati in
  `%LOCALAPPDATA%\Claude-Data\claude_pzs8sxrjxfjjc\` (nella cartella MSIX resta solo
  `.user-data-left.claude_pzs8sxrjxfjjc`) → `srv.plan` = `no_token+no_history (FileNotFoundError 2)`.
- Nuovo `windows/agent/tab2monitor_server_v0.2.9_20261023-0000.py`: `plan-usage-history.json` cercato in
  `Claude-Data\*\`, poi `%APPDATA%\Claude\` e la cartella MSIX; si usa il più recente; `srv.plan` riporta
  il percorso con `~` al posto della cartella utente.
- `adb reverse`: saltati i dispositivi via rete (`ip:porta`, stato `rete`: il tablet in Wi‑Fi raggiunge già il
  PC); dopo un errore nuovo tentativo solo dopo 5 min; log solo ai cambi di stato. Prima: tentativo ogni 5 s
  su `192.168.1.234:5555` con una riga di log a ogni errore (724 righe in 6 h). Visto anche un secondo tablet
  in USB (Galaxy Tab 4 SM‑T530, `matissewifi`): reverse applicato anche a lui (innocuo, non filtrato per modello).
- Provato in servizio: `claude_plan` = sessione 19%, settimanale 16%, campione 19:23, `adb` =
  `{USB: ok, 192.168.1.234:5555: rete}`, nessuna riga di errore adb nel log.
- Nota versioni: la prossima è **v0.3.0**, non v0.2.10 (il supervisore sceglie per ordine di nome e
  "v0.2.10" verrebbe prima di "v0.2.2").

## 8. (9) v0.2.8 — 2026-10-22 12:10 (sessione locale, ripiego claude_plan senza credenziali)
- Nuovo `windows/agent/tab2monitor_server_v0.2.8_20261022-1150.py`: se l'oauth non dà dati, `claude_plan`
  viene dall'ultimo campione di `plan-usage-history.json` di Claude Desktop (solo percentuali `fh`/`sd`,
  nessun token; `config.json` cifrato escluso): `src` = `history`, `ts` = ora del campione,
  `session{pct, stale?}` (`stale` se il campione ha più di 5 h), `weekly{pct, reset}` col reset calcolato
  dal giorno/ora scelti in `/config` (default giovedì 09:00). `srv.plan` = `ok (oauth)` /
  `ok (history HH:MM)` / `no_token+no_history (errore)`. Chiesto dall'utente via sessione APK, confermato
  qui dall'utente prima della messa in servizio.
- Nuova pagina `windows/agent/config_page_v0.1.2_20261022-1150.html`: giorno e ora del reset settimanale.
- **Problema trovato**: Claude Desktop è un'app MSIX: il file sta davvero in
  `%LOCALAPPDATA%\Packages\Claude_pzs8sxrjxfjjc\LocalCache\Roaming\Claude\`; il percorso
  `%APPDATA%\Claude\` lo vedono solo i processi figli dell'app (Claude Code), non il servizio
  (`FileNotFoundError`). Il server cerca in entrambi e usa il più recente.
- Provato: file finti (stale, t in ms/s, reset su altro giorno, file assente) e, in servizio sulla 8090,
  `claude_plan` = sessione 4%, settimanale 1%, campione 10:49, reset gio 29/10 09:00 (uguale a claude.ai).

## 8. (8) v0.2.7 — 2026-10-22 11:30 (sessione locale, limiti del piano Claude)
- Nuovo `windows/agent/tab2monitor_server_v0.2.7_20261022-1130.py`: campo `claude_plan`
  `{plan, session{pct,reset}, weekly{pct,reset}, weekly_opus?, ts}`, autorizzato dall'utente (sessione APK,
  2026-10-22 ~11:10). Token `claudeAiOauth.accessToken` letto da `~/.claude/.credentials.json` a ogni
  interrogazione (ogni 5 min), mai copiato né scritto in log/JSON/config/repository; endpoint **non
  documentato** `api.anthropic.com/api/oauth/usage`. Errore (401, formato, rete) → campo omesso e nessun nuovo
  tentativo finché il file credenziali non cambia. `srv.plan` = stato (ok / no_token / errore …).
  `--no-claude-plan` per disattivarlo.
- Provato con endpoint e token finti in locale: lettura ok (Pro, 4%, 1%, reset in epoch), 401 → sospeso,
  stesso file → nessuna chiamata, file cambiato → riprende. Nel log non compare il token.
- ⛔ Oggi il campo **manca**: `.credentials.json` contiene solo i token dei connettori MCP, nessun
  `claudeAiOauth` (l'accesso a Claude è dell'app desktop, salvato altrove; altri archivi di credenziali non
  esplorati). In servizio sulla 8090: `srv.plan` = `no_token`, nessuna chiamata di rete.

## 8. (7) v0.2.6 — 2026-10-22 01:20 (sessione locale, consumo CPU del server)
- Servizio verificato dopo la chiusura del server orfano (fatta dall'utente da amministratore): v0.2.5
  partita da sola, sensori con admin ok (CPU 52 °C, scheda madre, RAM, dischi, GPU).
- Causa del ~31% di un core: `psutil.process_iter` con `num_threads` (1265 ms di CPU ogni 3 s, misurato).
- Nuovo `windows/agent/tab2monitor_server_v0.2.6_20261022-0120.py`: top processi, `sys.procs` e
  `sys.threads` dai contatori PDH `Process(*)` e `System` (5 ms); psutil resta come ripiego.
  Formato di `procs` invariato (nomi con ".exe", % CPU divisa per le CPU logiche, mb = working set).
- Provato: stessi processi in cima della v0.2.5 (python 2,6%, claude 1,0%…), CPU del server **1,0%** (senza admin) e **3,1%** (servizio con admin, più sensori) di un
  core (era 31%). Messo in servizio con `schtasks /End` + `/Run`: il server v0.2.5 si è chiuso insieme al
  supervisore (correttivo della v0.2.3 verificato dal vivo).

## 8. (6) v0.2.5 — 2026-10-22 01:10 (sessione locale, voci nascoste)
- Nuovo `windows/agent/tab2monitor_server_v0.2.5_20261022-0110.py`: `"hidden": true` su `sensors.hw[].fans[]`
  (ventola impostata su "non mostrare") e su `temps[]` (casella "nascondi"); in `fan_config.json` la
  nuova chiave `hidden` (lista di id). Chiesto dalla sessione APK: l'APK v0.5.x disegna da `hw[].fans`,
  non da `pairs`, e non può dedurre "nascosta" dall'assenza in `pairs` (anche le ventole ferme ne mancano).
- Nuova pagina `windows/agent/config_page_v0.1.1_20261022-0110.html` con la casella "nascondi".
- Provato sulla 8093: GPU Memory nascosta e GPU Fan "non mostrare" → `hidden: true` su entrambe, `pairs`
  vuoto; dati di prova cancellati.

## 8. (5) v0.2.4 — 2026-10-22 01:00 (sessione locale, nomi e abbinamenti utente)
- Decisione: nomi e abbinamenti ventola→temperatura **lato server**, modificati da una pagina sul PC
  (`http://localhost:8090/config`, file `windows/agent/config_page_v0.1.0_20261022-0100.html`) e salvati in
  `windows/agent/fan_config.json` (dati locali, fuori da git). Perché non sull'APK: ogni tocco sul tablet
  richiede secondi e l'APK non ha input di testo; il server li applica una volta sola per APK e dashboard;
  la configurazione sopravvive alla reinstallazione dell'APK.
- Nuovo `windows/agent/tab2monitor_server_v0.2.4_20261022-0100.py`: `id` stabile (SensorId LHM, `win/<luid>/…`
  per le GPU lette da Windows) su ogni hardware/temperatura/ventola; con un nome utente `name` = nome
  scelto, `orig` = originale; `pairs` con `how` = `user` (+ `fan_id`, `temp_id`), "non mostrare" esclude la
  ventola. `GET /config`, `GET /api/config`, `POST /api/config` (solo da 127.0.0.1: dal Wi‑Fi → 403).
- Provato sulla 8093: rinomina MI50 e ventola 9070, abbinamento alla temperatura MI50 → JSON e pairs
  corretti, POST dall'IP di rete 403. Dati di prova cancellati.
- ⏳ Sulla 8090 risponde ancora il server v0.2.2 orfano (PID 28692, elevato): finché non viene chiuso
  da un terminale amministratore il servizio non passa alla v0.2.4.

## 8. (4) v0.2.3 — 2026-10-22 00:50 (sessione locale, servizio installato dall'utente)
- Attività `Tab2Monitor` installata dall'utente (elevata): **sensori verificati con admin**: CPU Ryzen 5 9600X
  (Tctl 55 °C, CCD1), scheda madre ASUS ROG STRIX B650-A (SuperIO Nuvoton NCT6799D: 5 temperature,
  7 ventole; attive Fan #2 678 RPM, #6 571, #7 3061 al 100%), 2 DIMM Kingston, NVMe Samsung 990 EVO Plus,
  2 HDD, RX 9070 XT (fan 0 RPM = modalità zero-RPM), MI50 da Windows (35 °C).
- Nuovo `windows/agent/tab2monitor_server_v0.2.3_20261022-0050.py`: scartate le soglie (Warning/Critical/
  Limit/Resolution) dalle temperature; RAM come kind `ram`; `fans[].on`; ventole della scheda madre a 0 RPM
  escluse da `pairs`, le altre abbinate di default alla CPU (`how` = `cpu_default`, curva ASUS predefinita);
  niente SO_REUSEADDR; il server esce quando si chiude lo stdin del supervisore.
- Problema trovato: con SO_REUSEADDR (default di Python) su Windows il servizio e il server avviato a mano
  ascoltavano **entrambi** sulla 8090; e `schtasks /End` chiude il supervisore ma lascia il server orfano.
  Entrambi corretti nella v0.2.3 (provato: 10048 "indirizzo già in uso" invece del doppio ascolto; stdin
  chiuso → uscita in < 1 s, Tab2Sensors compreso).
- Account Claude: `~/.claude/.credentials.json` letto con l'autorizzazione dell'utente (solo i nomi delle
  chiavi): contiene solo token OAuth dei connettori MCP, **nessun `claudeAiOauth`** → i limiti di piano non
  si leggono da lì (accesso fatto dall'app desktop). Non cercati altri archivi di credenziali.
- Riavvio senza admin: `schtasks /End /TN Tab2Monitor` poi `schtasks /Run /TN Tab2Monitor`.

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
