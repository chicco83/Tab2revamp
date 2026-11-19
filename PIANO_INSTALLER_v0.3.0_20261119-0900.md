# PIANO INSTALLER — Tab2Monitor su altri PC Windows e altri tablet Android
Versione: v0.3.0 — 2026-11-19 09:00 (ora locale del PC) · deriva da `PIANO_INSTALLER_v0.2.0_20261022-0545.md`
(provenienza; questo file è completo da solo). Branch: **`installer-tab2monitor`** (worktree
`../tab2revamp-installer`); il branch principale `ccr-734baad4-t02ry6` non è toccato: il 2026-11-19 è
stato **unito nel branch installer** (merge di `origin/ccr-734baad4-t02ry6` fino a `4ce1807`), non il contrario.

## 0. Stato in breve (2026-11-19)
- Pacchetto portabile **v0.2.0** = server **v0.4.4** (v0.4.3 del branch principale + parti portabili) +
  APK **v0.12.0** del branch principale (com'è, verificata sul GT-P5100) + Python embeddable + sensori LHM 0.9.6.
- Zip: `%LOCALAPPDATA%\Tab2Monitor-build\dist\Tab2Monitor_v0.2.0.zip` (12,9 MB; 25,8 MB estratto).
- Ricerca automatica: protocollo del branch principale, **UDP 8091**, "TAB2MON?" →
  `{"app":"tab2monitor","name","port","version"}`. Il firewall del Setup apre TCP 8090 e UDP 8091.
- APK del branch installer v0.10.0 (minSdk 21, inglese, Home dall'app, ricerca su UDP 8090) **ritirata**:
  le sue funzioni da riportare sulla v0.12.x sono in §7 (⏳, sessione APK).

## 1. Decisioni dell'utente (2026-10-22, risposte alle 11 domande del piano iniziale)
| # | Domanda | Risposta | Come è applicata oggi (v0.3.0 del piano) |
|---|---|---|---|
| 1 | Chi userà l'installer | **altre persone** | testi semplici IT/EN, `LEGGIMI-README.txt`, menu Setup numerato |
| 2 | Formato | **possibilmente portabile** | zip da estrarre e avviare (`Tab2Monitor.cmd`) senza installare; Setup facoltativo. Inno Setup ⏸️ |
| 3 | Avvio del server | **a scelta dell'utente** | Setup: all'accesso con admin / all'avvio del PC (SYSTEM) / all'accesso senza admin / nessuno; attività con priorità 4 |
| 4 | Driver PawnIO | **l'installer lo propone** | Setup chiede, scarica 2.2.0 dal rilascio ufficiale, verifica SHA256 + firma, apre l'installer di PawnIO |
| 5 | Tablet da supportare | **non definiti** | oggi Android **7.1+** (APK v0.12.0, minSdk 25). Con l'APK v0.10.0 era 5.0+: da riportare (§7) |
| 6 | Wi‑Fi + ricerca automatica come default | **sì** | UDP 8091 (server v0.4.3/v0.4.4 + APK v0.12.0); USB/adb opzionale |
| 7 | Token o solo firewall | **basta limitare** | regole firewall: solo `python.exe` del pacchetto, TCP 8090 + UDP 8091, profilo Privato, sottorete locale. Niente token |
| 8 | Claude spento sui PC nuovi | **sì** | `data\config.json` del pacchetto `"claude": false` (spegne log di Claude Code e limiti del piano); l'installer del tablet spegne il pannello (`--ez p_claude false`) |
| 9 | Chiave di release | **"perché?"** → §1.9 | per ora chiave di debug di questo PC; decisione ⏳ |
| 10 | Lingua | **anche inglese** | script e README IT/EN; **APK v0.12.0 solo italiano** (⏳ §7); pagina `/config` solo italiano ⏳ |
| 11 | Download | **decidi tu** | solo fonti ufficiali (§6) |

### 1.9 Perché una chiave di release
Android accetta un aggiornamento solo se è firmato con **la stessa chiave** dell'app installata. Oggi l'APK
è firmata con la chiave di *debug* di questo PC (`%USERPROFILE%\.android\debug.keystore`, password pubblica
"android"): se il PC si perde, per aggiornare i tablet bisogna disinstallare l'app (impostazioni perse);
un'APK compilata su un altro PC non si installa sopra; chi copia la chiave può firmare un falso aggiornamento.
**Proposta**: usare la chiave di debug attuale come chiave ufficiale, copiata in un file a parte con una
password nuova (`keytool -importkeystore` + `-storepasswd`) e un backup fuori dal PC: il GT-P5100 si aggiorna
senza reinstallare. Lo script di build v0.12.0 del branch principale firma ancora solo con la chiave di debug
(la firma con variabili `T2M_KEYSTORE`/`T2M_KS_PASS`/`T2M_KEY_ALIAS` esisteva nello script v0.10.0 del branch
installer, ritirato): da riportare quando l'utente decide (⏳).

## 2. Confronto con l'installer del fork (richiesta della sessione APK, 2026-11-19)
Fork di un'altra IA: `C:\Users\chicco\Desktop\tab pc monitor\windows\installer\` (`setup_v0.1.0_20261112-1800.ps1`,
`install_tablet_v0.1.0_*`, `start_v0.1.0_*`, `LEGGIMI_v0.1.0_*`). **Solo letto, mai eseguito**: il suo setup
cancella e ricrea l'attività `Tab2Monitor` di questo PC (`schtasks /Delete /TN "Tab2Monitor" /F`).
| Punto | Fork | Pacchetto portabile (questo branch) |
|---|---|---|
| (1) Avvio | `pythonw tab2monitor_server.py` all'accesso, **senza supervisore**, priorità predefinita 7; cancella sempre `Tab2Monitor` | supervisore `tab2monitor_service_v0.3.0` (riavvia il server, log), 4 modi a scelta, priorità 4, nome attività scelto (`-TaskName`), `-DryRun` |
| (2) File e dipendenze | copia solo il `.py` (rinominato), dashboard e pagina config; Tab2Sensors da `..\lhm` (fuori da git, quindi assente su altri PC); **nessun Python né psutil** | Python embeddable 3.13.15 + psutil 7.2.2 inclusi; Tab2Sensors + LibreHardwareMonitorLib 0.9.6 in `sensors\`; pagine in `web\`; APK in `apk\` |
| (3) Configurazione | scrive `C:\ProgramData\Tab2Monitor\config.json` (port, data_dir, log_file, fan_config) che **il server non legge**; il server scrive `fan_config.json` accanto allo script (Program Files, non scrivibile senza admin) | server v0.4.4 con `--data`: legge `config.json`, scrive `fan_config.json` e i log nella cartella dati (`data\` portabile o `%ProgramData%\Tab2Monitor`) |
| Firewall | TCP 8090 per tutti i programmi, profilo Privato; **UDP 8091 no** | solo `python.exe` del pacchetto, TCP 8090 + UDP 8091, Privato, sottorete locale |
| Tablet | APK cercata in `build\outputs\apk\release` (percorso Gradle che il progetto non usa) | APK inclusa; adb trovato o scaricato (Platform Tools ufficiali); firma diversa gestita; Home e amministratore a richiesta |
| Disinstallazione | solo istruzioni manuali | `Setup.cmd` voce 6 (`-KeepData` per tenere i dati) |
**Cosa si prende dal fork**: nel LEGGIMI le tre modalità di collegamento spiegate per esteso (USB con
`adb reverse` / Wi‑Fi con ricerca / Wi‑Fi con IP a mano) e la riga "alcuni router bloccano il broadcast UDP:
usare l'IP a mano" → riportate nel `LEGGIMI-README_v0.2.0` (rete ospiti, isolamento client). La domanda
"porta HTTP" del wizard del fork **no**: la porta si cambia in `data\config.json` e una porta diversa da 8090
non serve a nessuno dei casi visti. Il resto del fork non aggiunge nulla al pacchetto.

## 3. Cosa c'è nel branch (2026-11-19)
| Voce | File | Stato |
|---|---|---|
| Server **v0.4.4** = v0.4.3 + `--data`, `config.json` (port, bind, gpu, claude, claude_dir, adb auto/off/percorso, sensors, discovery, name), `adb_ips` vuoto su PC nuovo (la v0.4.3 mette `192.168.1.234`, il tablet di questo PC), `/api/info`, `/apk`, cartelle del pacchetto (`../web`, `../sensors`, `../apk`), log in `<dati>\logs`, `--no-discovery`, indirizzi per il tablet nel log | `windows/agent/tab2monitor_server_v0.4.4_20261119-0900.py` | ✅ provato da pacchetto estratto (porta 8190) |
| Supervisore **v0.3.0**: `--data`, log in `<dati>\logs\tab2monitor_service.log`, figlio con `python.exe` accanto a `pythonw.exe` | `windows/agent/tab2monitor_service_v0.3.0_20261022-0200.py` | ✅ provato il 2026-10-22 (attività di prova) |
| APK **v0.12.0** del branch principale, ricompilata qui con lo stesso script (stessa chiave di debug) | `android/tab2monitor_app/build_tab2monitor_v0.12.0_20261119-1000.sh` | ✅ build (61 982 byte); verificata sul tablet dalla sessione APK |
| Pacchetto portabile **0.2.0** | `windows/installer/build_portable_v0.2.0_20261119-0900.ps1` | ✅ 25,8 MB, zip 12,9 MB |
| Setup **v0.2.0**: firewall TCP 8090 + UDP 8091, priorità 4 | `windows/installer/setup_v0.2.0_20261119-0900.ps1` | ✅ `-DryRun`; ⏳ esecuzione reale con admin |
| Installazione tablet **v0.2.0**: Android 7.1+, Home con `pm enable` (avviso se Android lo vieta) | `windows/installer/install_tablet_v0.2.0_20261119-0900.ps1` | ✅ sintassi; ⏳ prova su un tablet/emulatore con APK v0.12.0 |
| README **v0.2.0** IT/EN | `windows/installer/LEGGIMI-README_v0.2.0_20261119-0900.txt` | ✅ |
| Avvio portabile, common, launcher, terze parti | `start_v0.1.0`, `common_v0.1.0`, `launcher_*_v0.1.0`, `THIRD-PARTY_v0.1.0` (immutati) | ✅ 2026-10-22 |

## 4. Prove fatte
| Data | Prova | Esito |
|---|---|---|
| 2026-11-19 | Pacchetto 0.2.0 estratto in `%LOCALAPPDATA%\Tab2Monitor-build\test020`, `data\config.json` con porta 8190, `adb: off`, `discovery: false` (la 8091 è del server vero), server dal Python incluso con `--data` | ✅ `/api/info` = `{"name":"PROVA-PACCHETTO","port":8190,"version":"v0.4.4"}`, `/apk` 200 (61 982 byte, `application/vnd.android.package-archive`), `/config` 200, dashboard 200, `srv.lhm` = `lib` (Tab2Sensors avviato e chiuso col server), `adb_ips` = `[]`, log in `data\logs\server.log` e nessuna riga nel log del server vero |
| 2026-11-19 | Ricerca UDP verso il server vero (v0.4.3, sola lettura): "TAB2MON?" a `127.0.0.1:8091` | ✅ `{"app":"tab2monitor","name":"DESKTOP","port":8090,"version":"v0.4.3"}` |
| 2026-11-19 | Setup `-Action Install -Autostart Logon -TaskName Tab2MonitorTest -DryRun` | ✅ regole "TCP 8090" e "UDP 8091" solo per `python.exe` del pacchetto, Private, LocalSubnet; attività `Tab2MonitorTest` (non creata: dry-run); `Tab2Monitor` dell'utente intatta (Running) |
| 2026-11-19 | Parser PowerShell sui tre script v0.2.0 | ✅ 0 errori; script solo ASCII |
| 2026-10-22 | Pacchetto 0.1.0 (server v0.3.0, APK v0.10.0): emulatori API 21 e 34 (installazione, USB e Wi‑Fi, IT/EN, Home + riavvio), Setup Status/LogonUser/Uninstall con attività di prova, PawnIO SHA256 `1F519A22…3032` firma `CN=namazso.eu`, URL Platform Tools 200 (7,7 MB) | ✅ (valgono per le parti rimaste uguali: Setup, start, PawnIO, adb) |

## 5. Problemi trovati e risolti
| Data | Problema | Causa | Soluzione |
|---|---|---|---|
| 2026-11-19 | Due "v0.3.0" del server (branch installer 20261022-0200 e branch principale 20261023-0010) e due "v0.10.0" dello script APK | i due branch sono andati avanti in parallelo | tolti dal branch installer il server v0.3.0 e lo script APK v0.10.0 suoi (restano nel commit `2d2fc0b`); il codice portabile è ora nel server v0.4.4 |
| 2026-11-19 | La copia di prova scriveva nel log del server vero | la v0.4.3 scrive sempre in `%LOCALAPPDATA%\Tab2Monitor\server.log` | v0.4.4: con `--data` il log va in `<dati>\logs\server.log` |
| 2026-11-19 | Su un altro PC il server tenterebbe `adb connect 192.168.1.234:5555` ogni 60 s | default `adb_ips` della v0.4.3 | v0.4.4: con `--data` la lista parte vuota (si riempie da `/config`) |
| 2026-11-19 | `claude_dir` di `config.json` ignorato | `ClaudeUsage(root=CLAUDE_DIR)`: valore predefinito fissato all'import | `ClaudeUsage(CLAUDE_DIR)` passato esplicitamente |
| 2026-11-19 | Sensori del pacchetto dalla cartella `LibreHardwareMonitor_v0.9.6-mi50` | lo script prendeva la cartella con nome più alto | build v0.2.0: solo `LibreHardwareMonitor_v<numero>` (release ufficiale); la `-mi50` è una build di prova di una PR, usata solo su questo PC |
| 2026-10-22 | install_tablet fermo al primo `adb install` | PowerShell 5.1: `2>&1` su un eseguibile con `ErrorActionPreference=Stop` rende fatale lo stderr di adb | funzioni `A`/`AS` con `Continue` locale e righe convertite in testo |
| 2026-10-22 | Android 14: `pm enable …/.HomeAlias` → `SecurityException` | da Android 11 circa la shell non cambia i componenti di app non debuggable | con l'APK v0.10.0 l'app lo attivava da sé; con la v0.12.0 non può: l'installer avvisa (⏳ §7) |
| 2026-10-22 | Dopo il riavvio tornava il launcher di sistema | Android salva componenti e ruoli in ritardo | l'installer avvisa di attendere 30 s |
| 2026-10-22 | "accesso negato" su `python.exe` costruendo il pacchetto | Google Drive blocca i file appena scritti | pacchetto in `%LOCALAPPDATA%\Tab2Monitor-build\dist` |
| 2026-10-22 | Errore di sintassi `$data:` nel Setup | `$nome:` in una stringa è un'unità | `${data}` |
| 2026-10-22 | Emulatore API 21 fermo | `-gpu swiftshader_indirect` | `-gpu host` |
| 2026-10-22 | Il server di prova ha fatto `adb reverse tcp:8190` sul tablet vero | AdbReverse agisce su tutti i dispositivi | rimosso subito; prove con `"adb": "off"` |

## 6. Limiti noti
- APK v0.12.0: **Android 7.1+**, **solo italiano**, Home impostabile dall'esterno solo fino ad Android 10.
- Ricerca dal tablet su rete vera: verificata dalla sessione APK sul GT-P5100 con il server v0.4.3; con il
  pacchetto su un **altro PC** ⏳ (serve la rete Privata + regola UDP 8091).
- La ricerca risponde sempre sulla 8091 anche se la porta HTTP è cambiata (la risposta dice la porta giusta).
- Due server sullo stesso PC: il secondo non riesce ad aprire la 8091 (riprova ogni 5 s, nessun danno).
- Rete Wi‑Fi del PC di sviluppo **Pubblica**: il tablet funziona oggi grazie a 4 regole generiche "python.exe"
  create da Windows; con il pacchetto serve la rete Privata (il Setup la propone).
- Setup con amministratore provato solo con `-DryRun` ⏳. Pagina `/config` solo italiano ⏳.
- Android 14: avviso "app per una versione precedente di Android" (targetSdk 25), basta OK.

## 7. Passi successivi
| # | Passo | Stato |
|---|---|---|
| 1 | Utente: decisione chiave (§1.9) | ⏳ |
| 2 | Utente: prova reale di `Setup.cmd` (UAC), meglio su un secondo PC | ⏳ |
| 3 | Pacchetto su un altro PC + tablet in Wi‑Fi: ricerca automatica UDP 8091 | ⏳ |
| 4 | Sessione APK: riportare sulla v0.12.x le funzioni dell'APK v0.10.0 del branch installer (commit `2d2fc0b`): minSdk 21 (Android 5.0+, con `Build.VERSION` sulle 2 chiamate API 23), testi IT/EN (classe `L`, chiave `lang`), Home attivata dall'app (`--ez home true` + interruttore: unico modo da Android 11), fotocamera alla risoluzione minima ≥160 px, firma di release opzionale nello script di build | ⏳ proposta |
| 5 | Unione nel branch principale: il server v0.4.4 e il supervisore v0.3.0 entrerebbero nella cartella che usa l'attività `Tab2Monitor` di questo PC (il supervisore v0.2.0 sceglie il server con versione più alta → partirebbe la v0.4.4, che senza `--data` si comporta come la v0.4.3). Da concordare con la sessione «Sorgente dati Windows»; se nel frattempo esce una v0.4.4 sul branch principale, rinumerare | ⏳ |
| 6 | Pagina `/config` bilingue | ⏳ |
| 7 | Firma del codice degli script (SmartScreen) | ⏸️ serve un certificato a pagamento |
| 8 | targetSdk recente | ⏸️ non richiesto |
| 9 | Inno Setup / MSI | ⏸️ / ⛔ il pacchetto portabile copre la richiesta |

## 8. Download fatti (fonti ufficiali, cache fuori dal repo `%LOCALAPPDATA%\Tab2Monitor-build\cache`)
| File | Fonte | Dimensione |
|---|---|---|
| `python-3.13.15-embed-amd64.zip` | python.org/ftp | 11,0 MB |
| `PawnIO_setup_2.2.0.exe` (solo per hash/firma, non incluso) | github.com/namazso/PawnIO.Setup | 3,4 MB |
| Android SDK cmdline-tools 16111833 | dl.google.com | 155 MB → `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest` |
| `platforms;android-21`, `system-images;android-21;default;x86_64`, `…android-34…` | sdkmanager | 102 MB + ~0,4 + 1,6 GB |
Nessuna nuova licenza accettata. AVD `t2m_api21` (1280x800), `t2m_api34` (pixel_tablet): `avdmanager delete avd -n …` per toglierle.
Il 2026-11-19 nessun nuovo download.

## 9. Rollback
- Tutto è nel branch `installer-tab2monitor`; il branch principale non cambia. Scartare il lavoro del
  2026-11-19 tenendo quello del 2026-10-22: `git reset --hard 2d2fc0b` **nel worktree del branch installer**
  (poi `git push --force-with-lease`). Scartare tutto: `git worktree remove ../tab2revamp-installer`,
  `git branch -D installer-tab2monitor`, `git push origin --delete installer-tab2monitor`.
- Sul PC: cache e pacchetti in `%LOCALAPPDATA%\Tab2Monitor-build` cancellabili.

## Storico modifiche
| Data | Prima | Ora | Perché |
|---|---|---|---|
| 2026-11-19 (v0.3.0) | pacchetto 0.1.0 con server v0.3.0 (ricerca su UDP **8090**, chiave `ver`) e APK v0.10.0 del branch installer (minSdk 21, IT/EN, Home dall'app); firewall TCP+UDP 8090; attività con priorità predefinita 7 | pacchetto 0.2.0 con server v0.4.4 (base v0.4.3) e APK v0.12.0 del branch principale; ricerca **UDP 8091**, chiave `version`; firewall TCP 8090 + UDP 8091; priorità 4; sensori solo dalla release LHM ufficiale; §2 confronto con il fork | richiesta della sessione APK: un solo protocollo di ricerca, quello già verificato sul tablet; la priorità 7 bloccava il server (2026-11-12, Citrix al 100%) |
| 2026-10-22 (v0.2.0) | piano senza codice (v0.1.0: inventario, opzioni, 11 domande) | risposte dell'utente applicate, pacchetto 0.1.0 costruito e provato | richiesta utente "procedi fino a che non è tutto funzionante" |
