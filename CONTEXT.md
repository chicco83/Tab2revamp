# CONTEXT — Tab2revamp
Versione: v0.1.14 — 2026-10-22 01:35 (ora del PC)

## Obiettivo
Riusare un Galaxy Tab 2 10.1 (GT-P5100, lentissimo: ogni tocco richiede secondi)
come **display secondario fisso** accanto a un PC Windows, che mostra il carico
delle risorse (CPU, core, RAM, GPU, temperature, disco, rete, top processi).

## Decisione architetturale (2026-10-21)
| Opzione | Esito | Motivo |
|---|---|---|
| spacedesk (desktop esteso) | scartata | streaming H.264 continuo su 1 GB RAM / OMAP4430 = lag; inutile per una dashboard |
| postmarketOS / mobile Linux | scartata (per ora) | port espresso in stato "testing", SGX540 senza accelerazione -> browser in rendering software, piu' lento di Android |
| **Android leggero + dashboard web** | **scelta** | ROM custom senza GApps + pagina HTML minimale che legge un JSON dal PC |

L'utente ha confermato: dashboard dedicata (non serve trascinare finestre).

## Architettura
```
Windows PC                                       Galaxy Tab 2
LibreHardwareMonitor (web :8085, opz.) ─┐
psutil ── tab2monitor_server (:8090) ───┼── USB: adb reverse tcp:8090 ──► browser -> http://localhost:8090
                                        └── oppure Wi-Fi http://<IP-PC>:8090
```
- `windows/agent/` server Python (stdlib + psutil), endpoint `/api/metrics` e `/`.
- `dashboard/` pagina unica, polling 1 s, aggiornamento DOM minimo.
- `android/` script adb: `diagnose` (sola lettura) e `tune` (impostazioni, debloat, apertura dashboard).
- `android/tab2monitor_app/` **APK nativa Tab2Monitor** (dalla v0.1.5 sostituisce Jelly + dashboard web):
  stessa API `/api/metrics`, disegno su Canvas, prova USB (`127.0.0.1:8090` via `adb reverse`)
  poi l'URL Wi‑Fi salvato. Build senza Gradle con `build_tab2monitor_*.sh`, installazione con
  `android/install_tab2monitor_*.sh`.

## Stato (verificato sul tablet il 2026-10-21, sessione locale)
| Voce | Stato | Dettaglio |
|---|---|---|
| Diagnosi adb | ✅ | stock **4.2.2 JDQ39.P5100XXDMJ2**, SDK 17, CSC ITV, kernel 3.0.31 (ott 2013), **niente root**; log in `android/logs/` (ignorato da git: contiene seriale) |
| Server Windows | ✅ | `start_tab2monitor` avviato, `/api/metrics` ok; LHM non attivo (GPU/temperature "--") |
| Dashboard via Wi-Fi | ✅ | tablet 192.168.1.226 → PC 192.168.1.237:8090 (rete "Pubblica", regola firewall python.exe presente); Chrome 71 ok, browser stock con artefatti |
| Tuning impostazioni | ✅ | animazioni 0, stay-on 3, luminosità 90, landscape (`user_rotation 0`), wifi scan off |
| Debloat via adb | ⛔ senza root | su 4.2.2 `pm disable-user` si blocca; richiede root o disattivazione manuale da Impostazioni |
| Root dello stock | ⛔ scartato | l'utente (2026-10-21) ha scelto di passare direttamente alla ROM custom: il root dello stock sarebbe una deviazione |
| TWRP 3.4.0-0 | ✅ file verificato | `twrp-3.4.0-0-espresso3g.img.tar` nella cartella repo (non versionato), SHA256 `7e90f863…f789d` = quello ufficiale twrp.me |
| Odin 3.14.4 | ⚠️ | fornito dall'utente, exe **non firmato**, non verificabile; lo esegue l'utente |
| Backup stock | ✅ | `backup/stock_P5100XXDMJ2_20261021/` (escluso da git): EFS, SBL1/2, PARAM, KERNEL, MODEM, FACTORYFS, HIDDEN (MD5 tablet = PC, `MD5SUMS.txt`) + 665 file utente (517 MB) |
| TWRP 3.4.0-0 | ✅ installato | flash con Odin dall'utente 2026-10-21 |
| ROM custom | ✅ installata | LineageOS 14.1 `lineage_espresso3g-userdebug 7.1.2 NJH47F` (MD5 zip verificato); 1° tentativo E1001 cause 101 (`/cache` non montata dopo il wipe) → `twrp mount /cache` e reinstall OK |
| Debug USB / adb root | ✅ | attivato a mano dall'utente; build userdebug: `adb root` disponibile (accesso root "solo ADB") |
| Debloat LineageOS | ✅ | 21 app disattivate con `tune_v0.1.3` (Jelly tenuto come browser) |
| Dashboard via USB | ✅ | `adb reverse tcp:8090` + Jelly (WebView 81), landscape; va rifatto `adb reverse` a ogni riavvio del tablet |
| rild (RIL 3G) | ✅ disattivato | consumava ~50% CPU in un loop (`ReaderLoop IOCTL_MODEM_STATUS = 4`); aggiunto `disabled` in `/system/etc/init/rild.rc`. Rollback: `adb root; adb remount; adb push backup/lineage14.1_20200623/rild.rc.orig /system/etc/init/rild.rc` (MD5 orig `5ab3fa76…f462`) |
| SensorService | ✅ risolto (HAL sensori disattivato, 2026-10-22) | thread `SensorService` di system_server al ~50% (un core) con **0 connessioni attive**. `strace -p <tid>` (2026-10-21): loop di `ppoll(..., timeout 0)` su 6 fd, il 5° è **non inizializzato** (`fd=539771469`, eventi spazzatura) → ritorna subito `POLLNVAL` e il ciclo non dorme mai. Bug dell'HAL `/system/lib/hw/sensors.omap4.so` ("Galaxy Tab 2 Sensor Module", MD5 `b46bfa99…1539`); il valore dipende da memoria non inizializzata, per questo a volte era 0%. **Fix approvato dall'utente ed eseguito**: HAL rinominato in `sensors.omap4.so.off`, backup in `backup/lineage14.1_20200623/sensors.omap4.so.orig` (MD5 uguale). Dopo il riavvio: boot ~40 s, `dumpsys sensorservice` = "No Sensors on the device", nessun crash, nessun thread SensorService in `top`, Tab2Monitor riavviata da sola. Si perdono rotazione/luminosità automatiche (voluto). Rollback: `adb root; adb remount; adb shell mv /system/lib/hw/sensors.omap4.so.off /system/lib/hw/sensors.omap4.so; adb reboot` (oppure `adb push backup/lineage14.1_20200623/sensors.omap4.so.orig /system/lib/hw/sensors.omap4.so`, MD5 `b46bfa99…1539`) |
| APK Tab2Monitor v0.1.0 | ✅ installata e provata | build senza Gradle (JDK di Android Studio, nessun download); via USB ✅, ripiego Wi‑Fi dopo `adb reverse --remove-all` ✅, PSS **14,5 MB** (Jelly con dashboard 64 MB PSS / ~105 MB RSS), CPU app ~3% |
| APK Tab2Monitor v0.2.0 — pannello impostazioni | ✅ installata e provata (2026-10-22) | pressione lunga sulla dashboard → `SettingsActivity`: interruttori per le sorgenti (USB, Wi‑Fi + URL) e per ognuno dei 9 pannelli, avvio al boot, intervallo. Provato: apertura con pressione lunga, 3 pannelli spenti → colonne ricomposte, nessun crash. APK 25 KB |
| APK Tab2Monitor v0.3.0 — pannello Claude | ✅ installata, provata con dati finti | pannello "Claude - utilizzo" (id 9, chiave `p_claude`, colonna 2 tra GPU e temperature), disattivabile come gli altri; senza campo `claude` dal server mostra "dati non disponibili". Provata 2026-10-22 00:16 con un mock locale (porta 8091 + `adb reverse tcp:8090 tcp:8091`): 6 righe corrette, nessun crash. **Dati reali ⏳**: dipendono dal server (sessione «Sorgente dati Windows»). Anche la schermata "Nessuna sorgente dati attiva" (v0.2.0) è stata verificata. adb sparisce per minuti dopo `adb root` |
| APK Tab2Monitor v0.4.0 — pagine, GPU, icone | ✅ installata e provata (2026-10-22 00:30–00:55, dati veri + mock per ventole/dischi/Claude) | 2 pagine a rotazione ("Sistema", "GPU e sensori"), tocco = pagina successiva, **ingranaggio** in alto a destra → impostazioni; una scheda per **ogni GPU** con 2 indicatori a lancetta (carico + saturazione VRAM); temperature per componente con **termometro**; **ventole animate** con velocità proporzionale agli RPM; **I/O per disco** con pallino verde sul disco attivo (serve il server: vedi sotto); pagina GPU più lunga se una scheda supera la soglia di carico. CPU app: 2% senza animazione, **17–21% con ventole animate** (pagina 2) → interruttore per spegnerle |
| Avvio al boot | ✅ verificato | dopo `adb reboot` (2026-10-22 00:00): `mResumedActivity = it.tab2revamp.monitor/.MainActivity`, screenshot con dati via **Wi‑Fi** (adb reverse perso). PSS 13,9 MB. Il boot completo + ricomparsa di adb ha richiesto ~3–4 min: adb resta assente per minuti anche se Windows vede già il dispositivo USB |
| Wi‑Fi tablet | ✅ acceso | era spento dopo il setup LineageOS; `svc wifi enable` → si collega da solo (2 reti salvate), IP **192.168.1.234** (DHCP; sullo stock era .226) |

Misure (am start -W, TotalTime ms, avvio a freddo):
| | prima del tuning | dopo |
|---|---|---|
| Impostazioni | 1472 / 1409 | 3258 / 1902 |
| Browser stock | 1820 / 1110 | 3132 / 1642 |
| Chrome con dashboard | — | 3277 |
Nessun miglioramento misurabile: il "dopo" è stato preso con Chrome+dashboard in RAM
(MemFree ~33–50 MB su 800 MB, swap zram in uso). Il collo di bottiglia è la **RAM**
(GMS ~80 MB, Chrome ~125 MB, decine di servizi Samsung), non la CPU (idle ~90%).
Le animazioni a 0 migliorano la percezione ma non il tempo di avvio.

eMMC: MAG2GA, CID PRV 0x07 (non è la revisione 0x19 del "brick bug" Samsung) — rischio basso,
comunque usare solo TWRP 3.x per i wipe.

## Root e flash — proposta (NON eseguita, serve conferma esplicita)
Fonti verificate il 2026-10-21:
- TWRP ufficiale espresso3g (p3100/p5100): https://twrp.me/samsung/samsunggalaxytab2gmsunified.html,
  file `twrp-3.4.0-0-espresso3g.img.tar` (7,2 MB, 2020-06-11) da https://eu.dl.twrp.me/espresso3g/
  (recovery = `/dev/block/mmcblk0p6`).
- Strumento di flash: **Odin** non ha una fonte ufficiale (tool Samsung trapelato);
  Heimdall (open source) non ha release su GitHub → scelta della fonte da fare con l'utente.
- Root su 4.2.2: SuperSU 2.82 (Chainfire) — sito originale non più attivo, fonte da concordare.
  Magisk attuale non supporta Android < 5.0.
- ROM: Unlegacy-Android (AOSP 7.1/espresso3g) — builds.unlegacy-android.org **non raggiungibile**
  il 2026-10-21; LineageOS 14.1 unofficial citato su community.e.foundation, link non verificati.

Procedura proposta: Download mode → flash TWRP (contatore download +1) → avvio diretto in
recovery (altrimenti lo stock la sovrascrive) → **backup EFS** in TWRP + `adb pull` sul PC →
(root) SuperSU zip, oppure (refactor) wipe + ROM senza GApps.
Alternativa senza flash per il debloat: Impostazioni → Gestione applicazioni → Disattiva.

## ROM scelta (2026-10-21)
**LineageOS 14.1 (Android 7.1.2) unofficial di Android-Andi (andi34)**, ultima build:
`lineage-14.1_espresso3g-7.1.2-20200623-1945.zip`, 283,3 MB, MD5 `b6df6ac6041d68a4ae3ac9a65b32027b`
- Catena di fiducia: andi34 è il manutentore del TWRP ufficiale espresso3g (gerrit.twrp.me);
  la sua pagina https://andi34.github.io/roms_tab2_lineage.html rimanda alla cartella
  AndroidFileHost `espresso3g-LineageOS-14.1` (flid 209384), uploader "Android-Andi".
- Perché questa e non altre:
  | Opzione | Esito |
  |---|---|
  | LineageOS 14.1 andi34 (2020) | **scelta**: maintainer noto, 7.1 → `adb reverse`, WebView/Chrome recenti, nessuna repartition |
  | LineageOS 16.0 unofficial (XDA, 2024-25) | scartata: "ALPHA / UNSTABLE", non daily-driver |
  | /e/OS Oreo/Pie custom (SourceForge) | scartata: build di terzi, segnalazioni di boot bloccato |
  | Unlegacy-Android 7.1 | scartata: sito build irraggiungibile il 2026-10-21 |
  | LineageOS 13 (6.0.1) andi34 | riserva se la 14.1 non avvia |
- Limiti noti: **niente RIL** (3G/SMS non funzionano: irrilevante per un display Wi‑Fi),
  fotocamera incerta; se non avvia, provare il kernel userdebug della stessa data
  (`lineage-14.1-espresso3g-userdebug-kernel-20200421.zip`) o la build precedente.
- Niente GApps (meno RAM occupata: oggi i servizi Google pesano ~80 MB).

Misure su LineageOS (am start -W, ms):
| | stock 4.2.2 | LineageOS prima del tuning | LineageOS dopo tuning |
|---|---|---|---|
| Impostazioni | 1409–3258 | 698 / 698 | 799 / 679 |
| Browser (Jelly) | 1110–3132 (stock) | 2073 / 7896 | 1148 / 1265 |
RAM: MemTotal 937 MB (stock 819), Jelly con dashboard ~105–120 MB RSS.

## APK nativa Tab2Monitor (v0.1.5, 2026-10-21)
| Scelta | Perché |
|---|---|
| Java puro, nessuna libreria (no AppCompat/AndroidX/Kotlin) | APK 21 KB, PSS 14,5 MB: il collo di bottiglia del tablet è la RAM |
| Una sola `View` che disegna su Canvas, niente layout XML | gerarchia minima, un ridisegno al secondo costa quasi nulla sull'OMAP4430 |
| Build **senza Gradle** (aapt2 → javac -source 8 → d8 → zipalign → apksigner) | Gradle/AGP scaricherebbero centinaia di MB; bastano SDK + JDK già presenti |
| JDK = `C:\Program Files\Android\Android Studio\jbr` (OpenJDK 25) | già installato: **nessun download** (l'utente voleva essere interpellato prima) |
| d8/apksigner via `java -cp/-jar`, non i `.bat` | i `.bat` spezzano i percorsi con spazi ("VIBE CODING") |
| `minSdk = targetSdk = 25` | gira solo su LineageOS 14.1; nessun vincolo di background/cleartext delle API successive |
| Orientamento `landscape` fisso | non serve il sensore di rotazione |
| URL USB fisso + URL Wi‑Fi via `am start --es url` | v0.1.0: niente schermata impostazioni; dopo un riavvio `adb reverse` è perso e l'app passa da sola al Wi‑Fi |
| v0.4.0: **pagine** a rotazione (timer `page_sec`, default 15 s, 0 = fermo) invece di stringere tutto in una | richiesta utente: servono spazio per temperature e ventole e una scheda per ognuna delle 2 GPU |
| v0.4.0: tocco breve = pagina successiva, ingranaggio = impostazioni, pressione lunga = impostazioni | richiesta utente (icona ingranaggio); il tocco breve prima non faceva nulla |
| v0.4.0: pagina GPU tenuta `gpu_busy_sec` (60 s) se una GPU ≥ `gpu_busy_pct` (30%) | richiesta utente: la pagina GPU deve restare di più se una scheda lavora |
| v0.4.0: icone disegnate in codice (lancetta, termometro, ventola) | nessuna risorsa grafica da caricare; colori a soglie come le barre |
| v0.2.0: pannello impostazioni **richiesto dall'utente** (2026-10-22), aperto con **pressione lunga** | il tocco breve non fa nulla: su un display fisso evita aperture accidentali. Activity separata con View create in codice (tema Material di sistema), distrutta all'uscita: non pesa sulla dashboard |
| "Sorgente" = connessione (USB / Wi‑Fi) **e** pannello (CPU, core, storico, RAM, GPU, temperature, rete, disco, processi) | interpretazione della richiesta "attivare o disattivare ogni sorgente"; chiavi `src_*` e `p_*` nelle SharedPreferences, impostabili anche con `am start --ez <chiave> false` |
| Pannelli spenti: si salta il disegno, i dati arrivano comunque dal server | il server (sessione separata "Sorgente dati Windows") potrebbe in futuro saltare il calcolo delle sezioni spente, es. `?skip=procs,lhm`: da concordare con quella sessione |
| Avvio al boot con `BOOT_COMPLETED`; modalità Home (`HomeAlias`) **opzionale, disattivata** | diventare la Home sostituisce Trebuchet: scelta lasciata all'utente (`install_* -home`) |
| Versione dei sorgenti Java solo in testa al file e nel versionName | Java impone nome file = classe pubblica (eccezione alla regola 1 di CLAUDE.md); versionati nel nome: script di build/installazione e APK in `dist/` |
| APK e cartella build fuori da git | firmata con la chiave di debug locale (`~/.android/debug.keystore`), si rigenera in pochi secondi |

Rollback: `adb uninstall it.tab2revamp.monitor`, poi la dashboard web con
`bash android/tune_v0.1.3_20261021-2315.sh` (apre Jelly).

## API /api/metrics — campi aggiunti in v0.2.0 (server 2026-10-22)
I campi v0.1.0 restano identici. `lhm` non è più null senza LHM: viene riempito con la GPU principale
da Windows (`lhm.src` = `lib` Tab2Sensors / `lhm` app LHM / `win` solo Windows).
| Campo | Contenuto |
|---|---|
| `cpu.mhz_cores[]`, `cpu.mhz_now`, `cpu.mhz_max` | MHz reali per core (base × % Processor Performance) |
| `cpu.queue` | coda processore |
| `sys.procs`, `sys.threads` | numero processi e thread |
| `gpus[]` | `name, luid, load, mem_mb, mem_tot_mb, mem_pct, temp, fan_rpm, power_pct, src` per scheda |
| `sensors.hw[]` | `id, name, kind (cpu/gpu/mb/disk/ram/other), temps[{name,c}], fans[{name,rpm,pct}]` |
| `sensors.pairs[]` | `fan, rpm, pct, fan_hw, temp, c, temp_hw, how (map/same_hw/cpu/cpu_default/mb), g`; dalla v0.2.3 `fans[].on` (rpm > 0) e niente ventole ferme della scheda madre — stesso `g` = stesso colore sull'APK |
| `srv` | `lhm` (fonte sensori), `adb` {seriale: ok/errore}, `http` |
| `hidden` (v0.2.5) | `true` su `fans[]`/`temps[]` nascosti dall'utente in `/config`; se manca la voce si mostra |
| `id`, `orig` (v0.2.4) | su `sensors.hw[]`, `temps[]`, `fans[]`: id stabile; se l'utente ha dato un nome, `name` è quello e `orig` l'originale. Nomi/abbinamenti da `http://localhost:8090/config` → `fan_config.json` (decisione: lato server, non APK) |
| `disks[].r`, `disks[].w`, `disks[].busy` (v0.2.2) | byte/s lettura/scrittura e % tempo attivo per volume (PDH LogicalDisk); omessi se il volume non ha contatori (G:) |
| `claude` (v0.2.1) | `today_tok` (input+output+cache_creation, senza cache_read), `today_out`, `today_cache_r`, `today_msgs`, `block_tok`, `block_start`/`block_end` (epoch, solo se il blocco di 5 h è aperto), `week_tok`, `models[{m,tok}]` (oggi, max 3), `scan_ts`; null senza `~/.claude/projects` o con `--no-claude` |

Stato server: ✅ attività `Tab2Monitor` installata (all'accesso, elevata) · ✅ sensori CPU/scheda madre/
ventole verificati con admin (vedi CHANGELOG v0.2.3) · ✅ v0.2.6 in servizio sulla 8090 (orfano chiuso
dall'utente) · ⏳ l'utente dà nomi e abbinamenti alle ventole dalla pagina `/config` · ⏳ APK: disegnare `sensors.pairs`
(file della sessione impostazioni APK) · ✅ CPU del server ~3% di un core con admin (v0.2.6: processi da PDH;
la causa era `psutil.process_iter`, non `GPU Engine`) · ⛔ avvio prima dell'accesso: il progetto sta su G: (Google
Drive), montato solo dopo l'accesso. ⛔ `claude`: limiti di piano dell'account non disponibili: `.credentials.json` non contiene il login Claude (solo connettori MCP). ⛔ FanControl: vedi CHANGELOG v0.2.0.

## Punti aperti
- SensorService: ✅ risolto disattivando l'HAL (vedi tabella Stato). Scartate: patch binaria dell'HAL (rischiosa, da rifare a ogni flash) e conviverci (un core su due sprecato). Attenzione: un reflash della ROM ripristina l'HAL → rifare il rename.
- `adb reverse` va rifatto a ogni riavvio del tablet: con l'APK non è più bloccante (ripiego Wi‑Fi).
- Il server Python risponde in HTTP/1.0 (niente keep-alive): una connessione TCP per richiesta.
  Accettabile a 1 Hz; eventualmente `protocol_version = "HTTP/1.1"` nel server.
- Facoltativo: `install_tab2monitor_*.sh -home` per usarla come Home (kiosk).
- APK v0.2.0: una colonna senza pannelli "elastici" (storico/temperature/processi) lascia spazio vuoto
  sotto; valutare se allungare i pannelli rimasti.
- **Pannello Claude** (richiesta utente 2026-10-22): APK pronta; il campo `claude` di `/api/metrics`
  è chiesto alla sessione «Sorgente dati Windows» (messaggio del 2026-10-22 00:20). Fonte: log locali
  di Claude Code `%USERPROFILE%\.claude\projects\**\*.jsonl` (`message.usage` per risposta,
  deduplicati per `message.id`). **Scelte**: niente credenziali/OAuth (`.credentials.json` non va letto),
  niente API non documentate → si vedono i **token usati da Claude Code su quel PC**, non la % del
  limite del piano claude.ai (non esposta da API pubbliche) né l'uso da claude.ai web/app. Schema:
```
"claude": {                      // null / assente se il server non ha la fonte
  "today_tok": int,              // input+output+cache_creation di oggi (ora locale), senza cache_read
  "today_out": int, "today_cache_r": int, "today_msgs": int,
  "block_tok": int, "block_start": epoch, "block_end": epoch,   // finestra 5 h corrente
  "week_tok": int,               // ultimi 7 giorni
  "models": [{"m": "claude-opus-5-5", "tok": int}],             // oggi, max 3
  "scan_ts": epoch
}
```
- **I/O per disco** (richiesta utente 2026-10-22 ~00:50): APK pronta (campi `r`, `w` byte/s in ogni
  elemento di `disks`; senza, mostra i totali come prima). Chiesto alla sessione «Sorgente dati Windows»
  (PDH `\LogicalDisk(X:)\Disk Read/Write Bytes/sec`). ✅ server v0.2.2 li invia (verificato con curl
  2026-10-22 01:00: C/D/E con `r`, `w`, `busy`; G:\ senza → "--"). Anche il campo `claude` arriva dal
  server v0.2.1 (dati reali: today_tok, blocco 5 h, ecc.). ✅ APK v0.4.1 verificata con screenshot sui
  dati reali (2026-10-22 00:37): Claude, dischi (G: "--"), 7 ventole Nuvoton + GPU Fan, temperature di
  scheda madre/CPU/RAM/GPU/SSD/HDD; scartate le soglie fisse (Limit/Resolution/Warning/Critical).
- ✅ Pagina 2 (APK v0.5.0, 2026-10-22): scelta dell'utente = **un box per hardware con le sue
  temperature e ventole** (CPU, scheda madre, RAM, dischi, altri), GPU con tutti i propri sensori nella
  loro scheda. Verificato con i dati reali: tutto visibile. Le ventole seguono `sensors.pairs` (con il
  server v0.2.3+ quelle che girano passano al box CPU: ✅ verificato con il server v0.2.6, Fan #2/#6/#7
  nel box CPU con "(scheda madre)", quelle ferme nel box della scheda madre).
  Se in futuro i box non ci stanno, l'ultimo mostra solo le righe che entrano.
- **Presenza con la fotocamera frontale** (APK v0.6.0, 2026-10-22) ✅ provata: spegnimento reale con
  `DevicePolicyManager.lockNow()` (app amministratore dispositivo, solo policy force-lock), riaccensione
  con wakelock ACQUIRE_CAUSES_WAKEUP + finestra SHOW_WHEN_LOCKED. Scelte:
  | Scelta | Perché |
  |---|---|
  | movimento (differenza fotogrammi) **o** volto (hardware HAL TI, max 35 volti) | il volto da solo non scatta se si passa di lato; il movimento da solo non vede chi sta fermo davanti |
  | 160x120 a 5 fps, griglia 40x30 | costo minimo: 3% CPU app + 1% cameraserver |
  | servizio in primo piano + PARTIAL_WAKE_LOCK | a schermo spento l'Activity è in pausa; il tablet è sempre in carica |
  | lockNow (amministratore) e non solo luminosità 0 | l'utente ha chiesto di spegnere lo schermo; senza amministratore resta il ripiego "oscurato" |
  | 5 s di pausa dopo lo spegnimento + 2 fotogrammi consecutivi | falso allarme osservato: il cambio di luce allo spegnimento sembrava movimento |
  Limiti: al buio il movimento non si vede (la fotocamera non ha IR). ✅ Riaccensione al passaggio di
  una persona verificata dall'utente (2026-10-22 01:10; log: spegne dopo 120 s, riaccende al movimento).
  L'utente (2026-10-22): "le risorse sul tablet non mi interessano, è dedicato solo all'APK" → il costo in
  CPU di animazioni/fotocamera non è un vincolo.
- **Grafica v0.7.0** ispirata ai pannelli commerciali (fonti nel CHANGELOG v0.1.12). ✅ screenshot.
- **Carico e allarmi v0.8.0** ✅ provati. Scelte:
  | Scelta | Perché |
  |---|---|
  | il servizio fa un proprio polling ogni 2 s | a schermo spento l'Activity (e il suo polling) è in pausa |
  | carico = presenza (riaccende e tiene acceso) | richiesta utente: vedere il PC quando lavora |
  | soglie default CPU 60, GPU 50, RAM 90, VRAM 90 % | ⚠️ oggi la VRAM della 9070 XT resta al ~93% (modello di llama-server caricato) → lo schermo **non si spegne** finché resta così: alzare la soglia VRAM (es. 99) se non è voluto |
  | voce Pico italiana sul flusso "allarme" | unico motore TTS senza GApps; il flusso allarme suona anche con volume media a 0 |
  | soglia per sensore con chiave `thr|<hardware>|<sensore>` | i nomi arrivano già rinominati dal server (/config): rinominare un sensore azzera la sua soglia personale |
  | allarmi spenti di default | evitare voci notturne non volute |
- **Animazioni v0.9.0** ✅: valori interpolati + grafici a scorrimento continuo; disegno a due livelli
  (Bitmap fissa + parte animata). ⏳ Ancora non a 60 fps: ~28 fps mediani sul OMAP4430/SGX540; margini
  possibili: niente gradienti nuovi a ogni frame (shader in cache), meno anti-aliasing sulle aree.
  Gauge check all'accensione ✅ (avvio verificato). Carico del tablet nell'intestazione ✅.
- **Batteria / carica** (2026-10-22): sulla USB del PC il tablet si scarica a schermo acceso (−730 mA) e si
  carica piano a schermo spento (+150 mA). Il kernel non permette di sospendere la carica. ⏳ Decisione
  dell'utente sulle opzioni (caricatore 2 A + presa smart comandata dall'app, oppure USB del PC + presenza).
  Ricerca hub/prese comandabili fatta: `RICERCA_HUB_USB_v0.1.0_20261022-0142.md` → raccomandata presa Shelly Plug S Gen3
  (~24 €, API HTTP locale) + caricatore Samsung; hub (YKUSH3 ecc.) sconsigliati. ⏳ Da provare sull'hardware, nessun acquisto fatto.
- **Rotazione 180°** (APK v0.9.3): opzione manuale `flip180` ✅ provata (decisione utente 2026-10-22: "se non
  abbiamo il sensore, opzione manuale"). Automatica ⛔ per ora: l'accelerometro si legge da
  `/sys/class/sensors/accelerometer_sensor/raw_data` ma SELinux lo vieta alle app. Strade possibili, non fatte:
  (a) il server legge il file via `adb shell` e lo passa nel JSON (solo con USB); (b) script di avvio in /system
  che ne cambia l'etichetta SELinux (modifica di sistema, serve conferma).
- **Limiti del piano Claude** (APK v0.9.5 ✅ con mock): schema atteso dal server
  `"claude_plan": {"plan": "Pro", "session": {"pct": 4, "reset": <epoch>}, "weekly": {"pct": 1, "reset": <epoch>}, "ts": <epoch>}`.
  ⏳ lato server: serve il token OAuth di `~/.claude/.credentials.json` + endpoint non documentato → decisione utente.
- **Installer** per altri PC/tablet: pianificato nella sessione dedicata «Piano installer Tab2Monitor»
  (2026-10-22): vedere il suo `PIANO_INSTALLER_*.md` quando pronto.
- Nomi e abbinamenti delle ventole: decisi dall'utente nella pagina `http://localhost:8090/config`
  (server v0.2.4); l'APK mostra `name` e usa `pairs`. Per "non mostrare" l'APK v0.5.1 legge
  `"hidden": true` su fans[]/temps[] ⏳ campo da aggiungere al server (richiesto 2026-10-22 01:40).
- Limiti del piano Claude (% di /usage): richiederebbero di leggere `~/.claude/.credentials.json`
  (token OAuth) e un endpoint non documentato. **Non fatto**: decide l'utente (permesso negato alla
  sessione Windows, che ha chiesto di non aggirarlo).
- **Ventole animate**: ridisegnare tutta la dashboard a 10–15 fps costava ~29% CPU; ora le icone stanno
  in una View trasparente separata (`MonitorView.FanOverlay`) → 17–21%. Se serve meno: interruttore
  "Ventole animate" spento (2%), oppure ridurre a 5 fps.
- Le ventole reali oggi: il server v0.2.x vede solo "GPU Fan" della RX 9070 XT (0 RPM a riposo) e nessuna
  ventola di scheda madre/CPU; le prove sono state fatte con RPM finti.
- Altre funzioni APK richieste dall'utente il 2026-10-22: «aggiungere altre funzioni», la prima era il
  pannello impostazioni (fatto); le prossime da definire.
- Sessione parallela «Sorgente dati Windows per Tab2Monitor» (server `windows/agent/`) aperta il
  2026-10-22: vincolo concordato = lo schema di `/api/metrics` si può solo estendere.

## Installer per altri PC e altri tablet (piano, 2026-10-22)
Sessione «Piano installer Tab2Monitor». Piano completo: `PIANO_INSTALLER_v0.1.0_20261022-0135.md`.
| Voce | Stato |
|---|---|
| Piano (inventario, opzioni, sicurezza, passi, domande) | ✅ scritto e committato |
| Risposte dell'utente alle 11 domande (sezione 9 del piano) | ⏳ |
| Implementazione (config server + cartella dati, scoperta UDP, APK release/minSdk, pacchetto, installer PC e tablet, prove) | ⏳ solo dopo il via esplicito dell'utente |
Proposte del piano (da confermare): Python **embeddable** incluso (non PyInstaller: falsi positivi
antivirus), zip + `install.ps1` con Inno Setup facoltativo, installazione in `C:\Program Files\Tab2Monitor`
+ dati in `C:\ProgramData\Tab2Monitor`, Wi‑Fi con **scoperta UDP** come default e adb solo opzionale,
APK con **chiave di release** fuori repo e **minSdk 21**, targetSdk 25 invariato, sideload da `/apk`.
Dipendenze nascoste trovate: driver **PawnIO** (sensori CPU/scheda madre), LHM 0.9.6 + `Tab2Sensors.exe`
fuori da git, adb dell'Android SDK. Rischio trovato: l'attività elevata esegue `.py` da G: (scrivibile
dall'utente) → risolto dall'installazione in Program Files. Android 10+: avvio al boot solo come Home.
Decisioni di *non* fare: Play Store/F-Droid/MSI (⛔ fuori ambito), QR code (⏸️ lettura senza librerie),
ottimizzazioni GT-P5100 nell'installer generico (⛔ restano specifiche), HTTPS in LAN (⛔ sproporzionato).

## Handoff sessione locale (v0.1.1 — 2026-10-21 19:05 UTC)
La sessione cloud non può raggiungere il PC/USB: la sessione locale va avviata dall'utente.
1. PC Windows con tablet in USB (Debug USB autorizzato), `adb` e Git for Windows installati.
2. `git clone https://github.com/chicco83/Tab2revamp` → `cd Tab2revamp` → `git checkout ccr-734baad4-t02ry6`
3. Nella cartella: `claude remote-control` (o Claude Desktop sulla cartella).
4. Prompt da incollare:
```
Leggi CLAUDE.md, CONTEXT.md, MANUAL.md. Tablet GT-P5100 collegato via adb.
1) adb devices
2) bash android/diagnose_v0.1.0_20261021-1845.sh e analizza il log
3) avvia windows/agent/start_tab2monitor_v0.1.0_20261021-1845.cmd
4) URL=http://<IP-PC>:8090 bash android/tune_v0.1.0_20261021-1845.sh --debloat  (stock 4.x -> Wi-Fi), misura la reattivita'
5) NON flashare nulla senza mia conferma: proponi TWRP/ROM esatte per espresso3g con fonti verificate e procedura di backup EFS.
Aggiorna CONTEXT/CHANGELOG/MANUAL, commit per nome dei file toccati, pull --rebase, push.
```
