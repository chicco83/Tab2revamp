# PIANO INSTALLER — Tab2Monitor su altri PC Windows e altri tablet Android
Versione: v0.1.0 — 2026-10-22 01:35 (ora locale del PC)
Sessione: «Piano installer Tab2Monitor» · Stato: **solo pianificazione**, nessuna implementazione
(si parte dalla Fase 1 solo dopo il via esplicito dell'utente e le risposte alla sezione 9).

Obiettivo dell'utente: installare il server e l'APK su **un altro PC Windows** e su **altri tablet
Android**, non solo sul PC attuale e sul GT-P5100.

Base dell'analisi (verificata il 2026-10-22 leggendo i file e lo stato del PC, commit `22024c8`):
server `windows/agent/tab2monitor_server_v0.2.6_20261022-0120.py`, supervisore
`tab2monitor_service_v0.2.0_20261022-0050.py`, installazione attività `install_tab2monitor_service_v0.2.0_*`,
`Tab2Sensors.cs`, APK v0.9.0 (`build_tab2monitor_v0.9.0_20261022-0310.sh`, `AndroidManifest.xml`,
sorgenti Java), `android/install_tab2monitor_v0.1.0_20261021-2350.sh`, regole firewall e servizi del PC.

---
## 1. Inventario — cosa serve oggi lato PC

| Componente | Oggi sul PC attuale | Dove / come | Portabile così com'è? |
|---|---|---|---|
| Python | 3.13 nel PATH (`python`, `pythonw`) | installato dall'utente | ❌ un altro PC può non averlo; il server richiede 3.8+ |
| psutil | da `pip` (`requirements.txt`: `psutil>=5.9`) | il `.cmd` lo installa se manca (download da PyPI) | ⚠️ serve Internet al primo avvio |
| Server v0.2.6 | un solo file, stdlib + psutil + ctypes (PDH, DXGI, D3DKMT) | `windows/agent/`, sceglie il file con versione più alta | ✅ nessun percorso assoluto, tutto relativo allo script |
| Dashboard web `/` | `dashboard/dashboard_v*.html` | letta dal server | ✅ |
| Pagina `/config` + `POST /api/config` | `config_page_v0.1.1_*.html` → `fan_config.json` accanto allo script | POST accettato solo da 127.0.0.1 | ⚠️ `fan_config.json` è **per PC** (id dei sensori di quel PC); va scritto in una cartella dati, non tra i programmi |
| `fan_map_*.json` | abbinamenti manuali (oggi solo commenti) | accanto allo script | ⚠️ per PC, come sopra |
| Sensori (CPU, scheda madre, ventole) | `Tab2Sensors.exe` (C# compilato con `csc.exe` del .NET Framework 4.x, x64) accanto a `LibreHardwareMonitorLib.dll` 0.9.6 | `windows/lhm/LibreHardwareMonitor_v0.9.6/` (12 MB, **fuori da git**: zip scaricato da GitHub) | ❌ un clone del repo non ha né la DLL né l'exe; va ricompilato o distribuito già compilato |
| Driver **PawnIO** | servizio kernel `PawnIO` **RUNNING**, installato in `C:\Program Files\PawnIO` | installato a parte (dall'app LHM o dal suo setup) | ❌ **dipendenza nascosta**: senza PawnIO niente temperature CPU/scheda madre né ventole |
| Avvio automatico | attività pianificata `Tab2Monitor`, **all'accesso**, elevata (RunLevel Highest), `pythonw.exe` del Python nel PATH | `install_tab2monitor_service_v0.2.0_*.ps1` | ⚠️ cablata su "progetto in G: (Google Drive) montato dopo l'accesso" e sul Python di sistema |
| Log | `windows/agent/logs/tab2monitor_service.log` (ruotato a 1 MB) | accanto agli script | ⚠️ in una cartella programmi andrebbe in `%ProgramData%` |
| adb + `adb reverse` automatico | il server cerca `adb.exe` in PATH → `%LOCALAPPDATA%\Android\Sdk\platform-tools` → `%ANDROID_HOME%`; ogni 5 s rifà `adb reverse tcp:8090 tcp:8090` | Android SDK di Android Studio | ❌ su un PC "normale" adb non c'è; per alcuni tablet servono anche i **driver USB** del produttore |
| Firewall | 2 regole "python.exe" **Consenti, profilo Pubblico, qualsiasi porta** (create dal prompt di Windows) | Windows Defender Firewall | ⚠️ troppo larga (ogni script Python, ogni porta); l'installer deve creare una regola precisa |
| Campo `claude` | legge `%USERPROFILE%\.claude\projects\**\*.jsonl` | profilo dell'utente che esegue il server | ✅ opzionale (`--no-claude`); su un PC senza Claude Code resta `null` |
| Opzioni | `--port 8090 --bind 0.0.0.0 --adb --no-adb --no-sensors --no-claude --gpu` | riga di comando | ⚠️ manca un file di configurazione: l'installer non ha dove salvarle |
| GPU | PDH/DXGI/D3DKMT, `--gpu <nome>` per la principale | Windows | ✅ generico (D3DKMT per temperatura/ventola GPU richiede driver WDDM recenti: da verificare su NVIDIA/Intel) |

**Requisiti minimi PC (proposta)**: Windows 10/11 **x64** (Tab2Sensors compilato x64, .NET Framework
4.7.2+ già incluso in Windows 10 1803+), diritti di amministratore per l'installazione e per i sensori.
Windows su ARM e Windows 7/8 fuori ambito.

## 2. Inventario — cosa serve oggi lato tablet

| Componente | Oggi | Portabile? |
|---|---|---|
| APK v0.9.0, pacchetto `it.tab2revamp.monitor` | `minSdk = targetSdk = 25` (solo Android **7.1+**), Java puro, Canvas, nessuna libreria | ⚠️ esclude Android 4.x–7.0 |
| Firma | chiave di **debug** `~/.android/debug.keystore` del PC che compila (password pubblica "android") | ❌ ogni PC ha una chiave diversa → un aggiornamento da un altro PC fallisce (firma diversa); chiave di debug inadatta a distribuire |
| Permessi | INTERNET, RECEIVE_BOOT_COMPLETED, **CAMERA** (runtime), WAKE_LOCK; `usesCleartextTraffic="true"` | ✅ |
| Amministratore dispositivo | `AdminReceiver`, solo policy `force-lock` (`lockNow()` per spegnere lo schermo) | ✅ su Android recenti (la policy force-lock non è deprecata) |
| Presenza | `android.hardware.Camera` (API vecchia), frontale, 160x120, rilevamento volti dell'HAL | ⚠️ API deprecata ma funzionante; molti tablet non hanno il rilevamento volti hardware → resta solo il movimento; 160x120 può non essere supportata (va scelta la risoluzione minima disponibile) |
| Voce allarmi | TextToSpeech con `Locale.ITALIAN`; sul GT-P5100 motore **Pico** (niente GApps) | ⚠️ altri tablet: motore Google/Samsung; serve controllare che l'italiano sia installato |
| Avvio al boot | `BootReceiver` avvia `MainActivity` | ❌ **Android 10+ blocca l'avvio di Activity dal background**: al boot non parte (funziona solo come Home/kiosk `HomeAlias` o con "mostra sopra altre app") |
| Schermo | layout in unità `u = altezza/800`, landscape fisso, pensato per 1280x800 (16:10) | ⚠️ 1920x1200 ok (stesso rapporto); 4:3 (es. 2048x1536) e 16:9/20:9 da provare (colonne più strette/larghe) |
| Testi | tutti in italiano, nel codice | ⏸️ inglese solo se richiesto |
| Sorgente dati | USB `http://127.0.0.1:8090` (fisso, via `adb reverse`) poi URL Wi‑Fi salvato (suggerimento nel campo: `192.168.1.237`) | ⚠️ l'URL Wi‑Fi va scritto a mano o passato con `am start --es url`: nessuna scoperta automatica |
| Installazione | `android/install_tab2monitor_v0.1.0_*.sh` (Git Bash + adb): install, reverse, primo avvio, URL, `-home` | ⚠️ richiede Git Bash e adb sul PC |
| Ottimizzazioni GT-P5100 | `tune_v0.1.4` (debloat), `rild.rc`, HAL sensori disattivato, ROM LineageOS | ⛔ specifiche del GT-P5100: **non** fanno parte dell'installer generico (restano come "profilo GT-P5100" facoltativo) |

**Comportamenti con `targetSdk 25` su Android recenti** (da verificare in emulatore, Fase 6):
- installazione: Android 14 rifiuta APK con targetSdk < 23 (25 passa); Play Protect può avvisare
  "app per versione vecchia di Android"; sideload con "origini sconosciute".
- l'app resta in "modalità compatibilità": niente tipi di servizio in primo piano obbligatori, niente
  POST_NOTIFICATIONS; **ma** il blocco di avvio Activity dal background (Android 10+) e il limite
  fotocamera in background (Android 9+: solo da servizio in primo piano, che c'è) valgono comunque.
- alzare `targetSdk` (≥ 26/28/31/33/34) obbliga a: `startForegroundService`, canale notifiche,
  `android:exported` espliciti, permesso notifiche, `foregroundServiceType="camera"` +
  `FOREGROUND_SERVICE_CAMERA`. Da fare solo se servisse (es. Play Store, non richiesto).

## 3. Cose cablate sul PC/tablet attuale (da sganciare)

| Cablatura | Dove | Proposta |
|---|---|---|
| Progetto su **G: Google Drive**, avvio solo dopo l'accesso | `install_tab2monitor_service_*.ps1`, supervisore (attende la cartella) | installare in `C:\Program Files\Tab2Monitor` (programmi) + `C:\ProgramData\Tab2Monitor` (config, log, `fan_config.json`) → si può partire **all'avvio del PC** |
| Python di sistema (`Get-Command python.exe`) | `.ps1`, `.cmd` | Python **incluso** nell'installer (vedi 4.1) |
| Cartella LHM `windows/lhm/LibreHardwareMonitor_v*` relativa allo script | server `find_tab2sensors`, build `Tab2Sensors` | sottocartella `sensors\` del pacchetto con DLL + exe già compilato |
| `fan_config.json` / `fan_map_*.json` accanto allo script | server | cartella dati (`%ProgramData%\Tab2Monitor`), configurabile |
| Porta 8090 | server (default), APK (`USB_URL`), `adb reverse`, script | resta default; diventa configurabile nel file di configurazione e annunciata dalla scoperta (5) |
| IP `192.168.1.237` | solo come esempio/suggerimento in `SettingsActivity`, `MainActivity` (commenti), MANUAL, script | sostituire con la scoperta automatica; il campo resta per l'inserimento manuale |
| Nomi hardware (Nuvoton, RX 9070 XT, MI50, S5K6A1, "Fan #3") | commenti, `fan_map` di esempio, documenti | nessun nome nel codice: già tutto da `sensors.hw[]`; i nomi utente stanno in `fan_config.json` per PC |
| adb in `%LOCALAPPDATA%\Android\Sdk\platform-tools` | server, script | file di configurazione `adb_path`; adb **opzionale** (Wi‑Fi è la modalità di default per altri tablet) |
| Chiave di debug del PC | build APK | chiave di **release** unica (4.3) |
| `minSdk 25` | build + manifest | decisione utente (4.3) |
| JDK `C:\Program Files\Android\Android Studio\jbr`, SDK in `%LOCALAPPDATA%` | build APK | resta così: si **compila** solo sul PC di sviluppo; l'installer distribuisce l'APK già firmato |
| Firewall "python.exe, Pubblico, qualsiasi porta" | Windows | regola precisa (6) |

## 4. Opzioni con pro e contro

### 4.1 Python sul PC di destinazione
| Opzione | Pro | Contro |
|---|---|---|
| **A. Python embeddable** (zip ufficiale python.org ~11 MB) + wheel psutil nella cartella | file `.py` leggibili e aggiornabili come oggi (regola "versione più alta per nome" invariata); niente falsi positivi antivirus tipici degli exe impacchettati; non tocca un Python già installato | più file; il pacchetto va assemblato con uno script; `pythonw.exe` incluso |
| B. PyInstaller (onedir/onefile) | un solo `tab2monitor.exe` | **falsi positivi antivirus** frequenti; richiede installare PyInstaller (download); ogni modifica = ricompilazione; si perde la scelta del file per versione |
| C. Python installato dall'utente + `pip install psutil` | zero lavoro | dipende da cosa c'è sul PC; Internet al primo avvio; versioni diverse |
| D. riscrivere il server in C# (come Tab2Sensors) | un exe, .NET già in Windows | riscrittura di ~1300 righe: fuori proporzione |

**Proposta: A.**

### 4.2 Pacchetto di installazione Windows
| Opzione | Pro | Contro |
|---|---|---|
| **A. Inno Setup** → `Tab2Monitor_Setup_vX.Y.Z.exe` | un exe, procedura guidata, voce "Disinstalla" in Impostazioni, pagine per porta/opzioni, esegue PowerShell per attività+firewall+PawnIO | Inno Setup **non è installato** su questo PC (download ~5 MB, va chiesto); setup non firmato → SmartScreen "editore sconosciuto" |
| B. zip + `install.ps1` / `install.cmd` (UAC) | nessuno strumento nuovo, coerente con gli script attuali | meno guidato; la disinstallazione va scritta a mano (script `-Remove`); ExecutionPolicy da aggirare con `-ExecutionPolicy Bypass` |
| C. MSI (WiX) | standard aziendale (GPO, Intune) | molto più lavoro; WiX da scaricare; inutile per uso personale |

**Proposta: B subito (riusa gli script esistenti), A come involucro sopra B** se l'utente vuole un setup
"da doppio clic". Il contenuto del pacchetto è lo stesso:
```
Tab2Monitor\                      (C:\Program Files\Tab2Monitor)
  python\                         embeddable + psutil
  server\  tab2monitor_server_vX.py, tab2monitor_service_vX.py, config_page_vX.html, dashboard_vX.html
  sensors\ Tab2Sensors.exe + LibreHardwareMonitorLib.dll e dipendenze (LHM 0.9.6, licenza MPL-2.0)
  drivers\ setup PawnIO (se la licenza lo consente, altrimenti download guidato)
  adb\     platform-tools (opzionale, vedi 4.4)
  apk\     Tab2Monitor_vX.apk firmato release
  install.ps1 / uninstall.ps1
C:\ProgramData\Tab2Monitor\  config.json (porta, bind, gpu, claude, adb, token), fan_config.json, logs\
```

### 4.3 Avvio del server
| Opzione | Pro | Contro |
|---|---|---|
| **A. Attività pianificata all'avvio del PC, account SYSTEM**, dalla cartella programmi | parte prima dell'accesso; admin per i sensori; nessun UAC; riusa il supervisore | il campo `claude` deve sapere quale profilo leggere (`claude_dir` in config); niente `adb` dell'utente (adb come SYSTEM funziona ma con un proprio server adb e chiavi RSA diverse → nuova autorizzazione sul tablet) |
| B. Attività all'accesso, utente, elevata (come oggi) | comportamento già provato; adb e `~/.claude` dell'utente | parte solo dopo l'accesso |
| C. Vero servizio Windows (NSSM, pywin32, sc + wrapper) | gestione da `services.msc` | NSSM/pywin32 = dipendenze in più; stessi problemi di profilo di A |

**Proposta: B come default** (coerente con l'uso "PC acceso e utente collegato", adb e Claude funzionano),
A come opzione dell'installer. Decisione all'utente (domanda 3).

### 4.4 adb sul PC (solo per la modalità USB)
| Opzione | Pro | Contro |
|---|---|---|
| **A. Wi‑Fi di default, USB opzionale** con adb già presente o scaricato su richiesta da `dl.google.com/android/repository/platform-tools-latest-windows.zip` | nessun binario Google da ridistribuire; la maggior parte dei tablet è in Wi‑Fi | la modalità USB richiede un passo in più |
| B. platform-tools incluso nel pacchetto | USB subito | **licenza dell'SDK Android da verificare** per la ridistribuzione; ~13 MB; driver USB del produttore comunque a parte |

**Proposta: A.**

### 4.5 APK: firma e versioni Android
| Tema | Opzioni | Proposta |
|---|---|---|
| Firma | chiave di debug (oggi) / **chiave di release** dedicata | keystore `tab2monitor-release.jks` creato una volta, **fuori dal repo** (es. `%USERPROFILE%\.tab2monitor\`), password solo nel gestore password dell'utente, copia di backup (persa la chiave = niente aggiornamenti, solo disinstalla+reinstalla). Sul GT-P5100 il passaggio dalla chiave di debug richiede **una** disinstallazione (prima `dpm remove-active-admin`), le impostazioni si perdono |
| minSdk | 25 (oggi, Android 7.1+) / **21** (Android 5.0+, `adb reverse` disponibile) / 16–19 (4.1–4.4, solo Wi‑Fi) / build multiple | **21**: le API usate sono quasi tutte ≤ 21 (`TextToSpeech.speak(…, Bundle, …)` 21, immersive sticky 19, `postInvalidateOnAnimation` 16, Camera/face detection 14); poche verifiche `Build.VERSION`. Sotto 21: più rami di codice e WebView/TLS vecchi; solo se l'utente ha davvero tablet 4.x (domanda 5) |
| targetSdk | 25 (oggi) / 34+ | **resta 25** finché non serve il Play Store (vedi sezione 2) |
| Build multiple | una per minSdk | ⛔ non serve: un solo APK con minSdk 21 copre tutto |

### 4.6 Scoperta automatica del server dal tablet
| Opzione | Pro | Contro |
|---|---|---|
| **A. UDP broadcast** (es. porta 8091): il tablet invia "TAB2MON?" sulla LAN, ogni server risponde con nome PC, IP, porta, versione | stdlib Python (`socket`), Java puro sul tablet, funziona da Android 4 in poi; più PC → elenco tra cui scegliere | i router con "isolamento client" o reti ospiti lo bloccano; serve `WifiManager.MulticastLock` su alcuni tablet; altra regola firewall (UDP) |
| B. mDNS / DNS-SD (`_tab2monitor._tcp`) | standard, `NsdManager` Android (API 16) | lato Python senza librerie bisogna implementare mDNS a mano (o aggiungere `zeroconf`, dipendenza); NsdManager è instabile su Android vecchi |
| C. QR code | nessun traffico broadcast; funziona anche tra reti diverse | generare il QR sulla pagina `/config` richiede un generatore (JS incluso nella pagina, ~10 KB); **leggerlo** dal tablet senza librerie è il problema (o app fotocamera di sistema che apre un link `tab2monitor://…`, non garantito) |
| D. manuale (oggi) | già fatto | scomodo; IP DHCP cambia (consigliare IP fisso/riservato nel router) |

**Proposta: A + D come ripiego**, con l'IP mostrato in grande nella pagina `/config` e nel vassoio/log del
server. QR rinviato (⏸️). L'APK ricorda il server scelto (per nome PC, non solo IP: se l'IP cambia, la
scoperta lo ritrova).

### 4.7 Installazione sul tablet
| Opzione | Pro | Contro |
|---|---|---|
| **A. Da PC con adb** (script PowerShell, niente Git Bash): install, `pm grant CAMERA`, `dpm set-active-admin`, primo avvio, URL, `-home` | tutto in un colpo, come oggi | Debug USB e driver USB sul tablet/PC; adb sul PC |
| **B. Sideload dal server**: `http://<PC>:8090/apk` scarica l'APK firmato; l'utente lo apre e consente "origini sconosciute" | nessun cavo né adb; la pagina `/` può mostrare il link | permessi (fotocamera, amministratore) dati dall'utente dall'app (la schermata impostazioni ha già "Attiva spegnimento reale" e chiede CAMERA); il primo avvio manuale serve comunque |
| C. Store (Play / F-Droid) | aggiornamenti automatici | targetSdk recente obbligatorio, account sviluppatore, revisione: fuori ambito ⛔ |

**Proposta: B per i tablet "normali", A per chi vuole kiosk/USB** (e per il GT-P5100).

## 5. Requisiti minimi dei tablet (proposta)
| Voce | Minimo | Note |
|---|---|---|
| Android | 7.1 con l'APK attuale; **5.0** se minSdk 21 | 4.x solo con build dedicata (domanda 5) |
| RAM | 1 GB | l'APK usa ~15–30 MB PSS; il GT-P5100 è già il caso peggiore |
| Schermo | ≥ 7", landscape; ottimale 16:10 | altri rapporti da provare (Fase 6) |
| Rete | Wi‑Fi sulla stessa LAN del PC (senza isolamento client) **oppure** USB con Debug USB | |
| Fotocamera frontale | facoltativa (presenza) | senza: la presenza resta spenta; carico e allarmi funzionano |
| Motore TTS con italiano | facoltativo (allarmi vocali) | |
| Avvio automatico | Android ≤ 9: `BOOT_COMPLETED`; Android 10+: solo come **Home** (`HomeAlias`) | da scrivere nella guida |
| Produttori con "risparmio aggressivo" (Samsung recenti, Xiaomi, Huawei) | togliere l'app dall'ottimizzazione batteria | guida per marca |

## 6. Sicurezza
| Rischio oggi | Peso | Proposta |
|---|---|---|
| Server su `0.0.0.0:8090` **senza autenticazione**: chiunque in LAN legge processi, hardware, token Claude e `GET /api/config` | medio in casa, alto su reti condivise/aziendali | (1) regola firewall **solo profilo Privato + "LocalSubnet"** e solo la porta 8090 TCP (+ UDP della scoperta); (2) **token facoltativo**: generato all'installazione, il tablet lo riceve con la scoperta solo dopo conferma sul PC (abbinamento) o lo inserisce a mano, poi header `X-Tab2-Token`; (3) `--bind 127.0.0.1` se si usa solo USB |
| Regole firewall esistenti "python.exe, Pubblico, qualsiasi porta" | medio | l'installer **non** le tocca (sono dell'utente) ma la guida suggerisce di toglierle; il Python incluso ha la sua regola precisa |
| `POST /api/config` solo da 127.0.0.1 | ok | tenere; attenzione: anche il traffico `adb reverse` arriva da 127.0.0.1 (accettabile: è il tablet dell'utente via cavo) |
| **Attività elevata che esegue `.py` da una cartella scrivibile dall'utente** (oggi Google Drive G:) | **alto**: un file sostituito in G: (anche da sincronizzazione Drive) gira come amministratore all'accesso successivo | installando in `C:\Program Files\Tab2Monitor` (scrivibile solo da admin) il rischio sparisce; il supervisore non deve più scegliere "il file con versione più alta" in cartelle scrivibili da tutti |
| Driver kernel PawnIO | basso (firmato) | installato solo con consenso esplicito; disinstallabile (`C:\Program Files\PawnIO\uninstall.exe`) |
| APK con `usesCleartextTraffic` (HTTP in chiaro) | basso in LAN | accettato (⛔ niente HTTPS: certificati in LAN = complessità sproporzionata) |
| Chiave di release dell'APK | — | mai nel repo né nei documenti; backup offline |
| Campo `claude` | dato personale | default **spento** sui PC nuovi (attivabile), acceso solo dove c'è Claude Code e lo si vuole |

## 7. Passi in ordine
| # | Passo | Stato |
|---|---|---|
| 0 | Risposte dell'utente alle domande (sezione 9) e via all'implementazione | ⏳ |
| 1 | **Server: configurazione e cartelle** — `config.json` in `%ProgramData%\Tab2Monitor` (porta, bind, gpu, claude, claude_dir, adb_path, token, cartella dati); `fan_config.json` e log nella cartella dati; percorso sensori configurabile. Nuova versione server v0.3.0 (sessione «Sorgente dati Windows», da coordinare) | ⏳ |
| 2 | **Scoperta UDP** nel server (risposta con nome PC/IP/porta/versione) + token facoltativo | ⏳ |
| 3 | **APK**: chiave di release, minSdk (21?), scoperta + elenco server nelle impostazioni, token, risoluzione fotocamera minima disponibile, controllo TTS italiano, avviso "Android 10+: usare modalità Home per l'avvio automatico"; versione APK nuova (coordinare con la sessione APK, che modifica gli stessi file) | ⏳ |
| 4 | **Script di assemblaggio** `build_package_vX.ps1` sul PC di sviluppo: Python embeddable + psutil, server, pagine, Tab2Sensors compilato + LHM, APK firmato → cartella `dist\Tab2Monitor_vX\` + zip (download Python/psutil/PawnIO **solo con consenso**) | ⏳ |
| 5 | **Installer Windows** `install.ps1`/`uninstall.ps1` (+ eventuale Inno Setup): copia in Program Files, `config.json`, attività (accesso o avvio), regola firewall precisa, PawnIO facoltativo, adb facoltativo, apre `http://localhost:8090/config` | ⏳ |
| 6 | **Installer tablet**: `install_tablet_vX.ps1` (adb, senza Git Bash) + pagina `/apk` del server per il sideload | ⏳ |
| 7 | **Prove**: Windows Sandbox o VM pulita (installazione, avvio, disinstallazione, niente sensori in VM); secondo PC reale (sensori, PawnIO); emulatori Android API 21, 25, 30, 34 (immagini da scaricare, ~1 GB ciascuna: consenso); un secondo tablet reale; il GT-P5100 aggiornato alla chiave di release | ⏳ |
| 8 | **Documenti**: `MANUAL` sezione installer, `RUNBOOK_INSTALLER_*.md` (procedura, problemi), CONTEXT/CHANGELOG | ⏳ |
| — | QR code | ⏸️ rinviato (lettura senza librerie) |
| — | Play Store / F-Droid, MSI | ⛔ fuori ambito |
| — | ottimizzazioni GT-P5100 (ROM, debloat, rild, HAL) nell'installer generico | ⛔ restano specifiche del GT-P5100 |

Rollback: ogni passo crea file **nuovi** versionati (regola 1 di CLAUDE.md); l'installazione attuale
(attività `Tab2Monitor` da G:, APK con chiave di debug) resta funzionante finché l'utente non la
sostituisce. Disinstallazione del nuovo: `uninstall.ps1` (attività, regola firewall, cartelle;
PawnIO solo se richiesto).

## 8. Coordinamento con le altre sessioni
- Server (`windows/agent/`): sessione «Sorgente dati Windows» → passi 1–2. Schema `/api/metrics` si
  può solo estendere (vincolo già concordato); la scoperta UDP e il token non cambiano lo schema.
- APK (`android/tab2monitor_app/`): sessione APK, ancora aperta → passo 3 da fare lì o dopo la sua
  chiusura, mai in parallelo sugli stessi file.
- Questa sessione: passi 4–8 (file nuovi: `installer/` o `windows/installer/`, script tablet).

## 9. Domande aperte per l'utente
1. **Chi userà l'installer?** Solo tu su un altro PC tuo, oppure altre persone (serve un setup "da
   doppio clic" con Inno Setup e testi più semplici)?
2. **Formato**: zip + script PowerShell (nessuno strumento nuovo) o anche `Setup.exe` con Inno Setup
   (download di Inno Setup ~5 MB sul PC di sviluppo)?
3. **Avvio del server sul nuovo PC**: all'**accesso** dell'utente (come oggi, adb e Claude funzionano)
   o all'**avvio del PC** come SYSTEM (prima dell'accesso)?
4. **Sensori CPU/scheda madre**: installare il driver **PawnIO** con l'installer (kernel driver, admin)
   o lasciarlo facoltativo (senza: solo GPU e Windows)?
5. **Tablet da supportare**: quali modelli/versioni Android hai in mente? Se ce ne sono con Android
   4.x–6.x serve abbassare minSdk (21 = Android 5.0; sotto serve più lavoro).
6. **Connessione**: Wi‑Fi con scoperta automatica come default e USB/adb solo opzionale — va bene?
7. **Sicurezza**: vuoi il **token** (abbinamento tablet↔PC) o basta la regola firewall solo-LAN privata?
8. **Campo Claude** sui PC nuovi: spento di default?
9. **Chiave di release**: ok a creare una chiave nuova (fuori repo, password tua) sapendo che il
   GT-P5100 andrà disinstallato e reinstallato una volta (impostazioni da rifare)?
10. **Lingua**: solo italiano o anche inglese (testi APK, pagina /config, installer)?
11. **Download** necessari durante l'implementazione (Python embeddable ~11 MB, wheel psutil, eventuale
    Inno Setup, PawnIO, immagini emulatore): ok a chiederli uno per uno al momento?
