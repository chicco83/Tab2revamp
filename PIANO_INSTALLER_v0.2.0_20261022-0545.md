# PIANO INSTALLER — Tab2Monitor su altri PC Windows e altri tablet Android
Versione: v0.2.0 — 2026-10-22 05:45 (ora locale del PC) · sostituisce `PIANO_INSTALLER_v0.1.0_20261022-0135.md`
(che resta come storico: inventario iniziale, opzioni e motivi). Branch: **`installer-tab2monitor`**
(worktree `../tab2revamp-installer`), il branch principale `ccr-734baad4-t02ry6` non è toccato.

## 1. Decisioni dell'utente (2026-10-22, risposte alle domande della v0.1.0)
| # | Domanda | Risposta | Come è stata applicata |
|---|---|---|---|
| 1 | Chi userà l'installer | **altre persone** | testi semplici IT/EN, `LEGGIMI-README.txt`, menu Setup numerato |
| 2 | Formato | **possibilmente portabile** | zip da estrarre e avviare (`Tab2Monitor.cmd`) senza installare; Setup facoltativo. Inno Setup ⏸️ non necessario |
| 3 | Avvio del server | **a scelta dell'utente** | Setup: all'accesso con admin / all'avvio del PC (SYSTEM) / all'accesso senza admin / nessuno |
| 4 | Driver PawnIO | **l'installer lo propone** | Setup chiede, scarica 2.2.0 dal rilascio ufficiale, verifica SHA256 + firma, apre l'installer di PawnIO |
| 5 | Tablet da supportare | **non definiti** | minSdk 21 (Android 5.0+): copre quasi tutti i tablet ancora in uso |
| 6 | Wi‑Fi + scoperta automatica come default | **sì** | UDP 8090 (server v0.3.0 + APK v0.10.0); USB/adb opzionale |
| 7 | Token o solo firewall | **basta limitare** | regola firewall: solo python.exe del pacchetto, TCP+UDP 8090, profilo Privato, sottorete locale. Niente token |
| 8 | Claude spento sui PC nuovi | **sì** | `data\config.json` del pacchetto: `"claude": false`; l'installer del tablet spegne anche il pannello |
| 9 | Chiave di release | **"perché?"** → vedi sotto | per ora resta la chiave di debug di questo PC; decisione ⏳ |
| 10 | Lingua | **anche inglese** | APK (lingua del sistema, o scelta nelle impostazioni), script, README; pagina `/config` ancora solo italiano ⏳ |
| 11 | Download | **decidi tu** | solo fonti ufficiali, elencati sotto con dimensione |

### 9. Perché una chiave di release (risposta alla domanda)
Android accetta un aggiornamento solo se è firmato con **la stessa chiave** dell'app già installata.
Oggi l'APK è firmato con la chiave di *debug* di questo PC (`%USERPROFILE%\.android\debug.keystore`,
password pubblica "android"):
- se il PC si rompe o si reinstalla, la chiave si perde → per aggiornare l'app sui tablet bisogna
  disinstallarla (impostazioni perse);
- un'APK compilata su un altro PC (es. da un'altra sessione o da un collaboratore) ha un'altra chiave
  di debug → non si installa sopra;
- la chiave di debug ha una password nota a tutti: chi la copia può firmare un'APK "Tab2Monitor"
  che il tablet accetterebbe come aggiornamento.

**Proposta (nessuna reinstallazione sul GT-P5100)**: usare come chiave "ufficiale" **la chiave di
debug attuale**, copiata in un file a parte con una password nuova scelta dall'utente
(`keytool -importkeystore` + `-storepasswd`), con copia di backup fuori dal PC. Così il GT-P5100 si
aggiorna senza disinstallare. Lo script di build v0.10.0 è già pronto (`T2M_KEYSTORE`,
`T2M_KS_PASS`, `T2M_KEY_ALIAS`); oggi senza variabili firma con la chiave di debug come prima.
In alternativa una chiave nuova (GT-P5100 da reinstallare una volta). **Decide l'utente** (⏳).

## 2. Cosa è stato fatto (branch `installer-tab2monitor`)
| Voce | File | Stato |
|---|---|---|
| Server **v0.3.0**: `--data` (cartella dati), `config.json`, scoperta UDP, `/api/info`, `/apk`, cartelle del pacchetto, indirizzi per il tablet nel log | `windows/agent/tab2monitor_server_v0.3.0_20261022-0200.py` | ✅ provato (pacchetto estratto, porta 8190) |
| Supervisore **v0.3.0**: log in `<data>\logs` | `windows/agent/tab2monitor_service_v0.3.0_20261022-0200.py` | ✅ provato (attività LogonUser di prova) |
| APK **v0.10.0**: minSdk 21, scoperta + "Cerca PC", lingua IT/EN, Home attivata dall'app, risoluzione fotocamera adattiva | `android/tab2monitor_app/…`, `build_tab2monitor_v0.10.0_20261022-0230.sh` | ✅ emulatori API 21 e 34; ⏳ scoperta su tablet reale |
| Pacchetto portabile (Python embeddable 3.13.15 + psutil 7.2.2, sensori LHM 0.9.6, APK) | `windows/installer/build_portable_v0.1.0_20261022-0300.ps1` | ✅ 25,8 MB, zip 12,9 MB |
| Avvio portabile (UAC facoltativo, "già attivo" → `/config`, avviso rete pubblica) | `windows/installer/start_v0.1.0_*.ps1` + `launcher_Tab2Monitor_*.cmd` | ✅ senza admin; ⏳ ramo con UAC (serve un clic) |
| Setup (installazione, avvio automatico 4 modi, firewall, PawnIO, tablet, disinstallazione) | `windows/installer/setup_v0.1.0_*.ps1` + `launcher_Setup_*.cmd` | ✅ stato, LogonUser, disinstallazione; ✅ `-DryRun` di Install; ⏳ esecuzione reale con admin |
| Installazione tablet via USB (PowerShell, senza Git Bash) | `windows/installer/install_tablet_v0.1.0_*.ps1` + `launcher_Install-Tablet_*.cmd` | ✅ emulatori API 21 e 34 (installazione, URL, Home, riavvio) |
| README IT/EN, terze parti | `LEGGIMI-README_v0.1.0_*.txt`, `THIRD-PARTY_v0.1.0_*.txt` | ✅ |

## 3. Prove fatte (2026-10-22, verificate)
| Prova | Esito |
|---|---|
| Pacchetto estratto in una cartella fuori dal repo, `Tab2Monitor.cmd -NoElevate` | ✅ server dal Python incluso, Tab2Sensors avviato, `/api/info`, `/api/metrics`, `/config`, `/apk` (61 983 byte) |
| Scoperta UDP dal PC (broadcast 255.255.255.255 e 192.168.1.255) | ✅ risposta `{"app":"tab2monitor","name":…,"port":…,"ver":"v0.3.0"}` |
| Controllo API 21 (`API_CHECK=1`, javac contro `platforms/android-21`) | ✅ solo le 2 chiamate API 23 protette da `Build.VERSION` |
| Emulatore **API 21** (Android 5.0.2, 1280x800 come il GT-P5100): install_tablet, dashboard USB (dati veri) e Wi‑Fi (server di prova 8190), italiano e inglese, schermata "Ricerca del PC…", impostazioni "Cerca PC" → "nessun PC trovato" (atteso: emulatore in NAT), nessun crash | ✅ |
| Emulatore **API 34** (Android 14, 2560x1600): installazione, scala del layout, pannello Claude spento dall'installer, Home + **riavvio → Tab2Monitor riparte da sola** | ✅ (dopo correzioni, vedi 4) |
| Setup `-Action Status`, `-Action Autostart -Autostart LogonUser` (attività di prova `Tab2MonitorTest`), `-Action Uninstall` | ✅ attività creata, server avviato dall'attività, rimossa; l'attività `Tab2Monitor` dell'utente intatta |
| Setup `-Action Install -Autostart Logon -DryRun` | ✅ sequenza corretta (copia, installed.txt, firewall, rete privata, PawnIO, attività, menu Start) |
| PawnIO 2.2.0: SHA256 `1F519A22…3032`, firma valida `CN=namazso.eu` | ✅ (file scaricato nella cache di build) |
| URL Platform Tools di Google | ✅ 200, 7,7 MB |

## 4. Problemi trovati e risolti durante le prove
| Problema | Causa | Soluzione |
|---|---|---|
| install_tablet si fermava al primo `adb install` | PowerShell 5.1: `2>&1` su un eseguibile con `ErrorActionPreference=Stop` trasforma lo stderr di adb (avanzamento) in errore fatale | funzioni `A`/`AS` con `Continue` locale e righe convertite in testo |
| Android 14: `pm enable …/.HomeAlias` → `SecurityException: Shell cannot change component state` | da Android 11 circa la shell non può cambiare i componenti di app non debuggable | l'app attiva da sé l'alias (`--ez home true`, interruttore "Usa come schermata Home"); poi `cmd role add-role-holder android.app.role.HOME` (API 29+) o `cmd package set-home-activity …/.HomeAlias` (API 24–28) |
| Dopo il riavvio tornava il launcher di sistema | riavvio pochi secondi dopo la scelta: Android salva componenti e ruoli in ritardo | l'installer avvisa di attendere 30 s; con l'attesa il riavvio apre Tab2Monitor ✅ |
| Build del pacchetto: "accesso negato" su `python.exe` | Google Drive blocca i file appena scritti in `G:` | pacchetto costruito in `%LOCALAPPDATA%\Tab2Monitor-build\dist` (fuori da Drive, niente 25 MB sincronizzati) |
| Setup: errore di sintassi `$data:` | in una stringa PowerShell `$nome:` è un riferimento a unità | `${data}`; tutti gli script controllati con il parser di PowerShell (0 errori) |
| Testo "(scheda madre)" in inglese | stringa nel codice dei box | `L.t(...)` |
| emulatore API 21 bloccato con `-gpu swiftshader_indirect` | — | `-gpu host`: avvio in 30 s |
| Il server di prova ha fatto `adb reverse tcp:8190` anche sul tablet reale | AdbReverse agisce su tutti i dispositivi | regola tolta subito (`adb reverse --remove tcp:8190`); prove successive con `"adb": "off"` |

## 5. Limiti noti
- **Scoperta UDP dall'APK non provata su una rete vera**: negli emulatori (NAT) i broadcast non
  escono. Il codice Java è lo stesso schema provato in Python dal PC. ⏳ prova su un tablet reale in Wi‑Fi.
- La scoperta usa la porta **8090**: con una porta diversa nel `config.json` il tablet va configurato
  a mano (indirizzo mostrato nella finestra del server).
- Android 14 mostra all'apertura "app creata per una versione precedente di Android" (targetSdk 25):
  solo un avviso con OK. Toglierlo = targetSdk ≥ 28/33/34 con le modifiche del piano v0.1.0 §2 (⏸️).
- "tablet CPU --" su Android 8+: `/proc/stat` non è leggibile dalle app (atteso).
- Pagina `/config` del server solo in italiano (file della sessione «Sorgente dati Windows») ⏳.
- Rete Wi‑Fi del PC di sviluppo è **Pubblica**: il tablet funziona oggi grazie alle regole generiche
  "python.exe"; con il pacchetto serve la rete privata (il Setup la propone).
- Setup con amministratore (copia in Program Files, firewall, attività Logon/Boot, PawnIO) provato
  solo con `-DryRun`: l'esecuzione reale richiede un clic UAC dell'utente ⏳.

## 6. Download fatti (fonti ufficiali, cache fuori dal repo `%LOCALAPPDATA%\Tab2Monitor-build\cache`)
| File | Fonte | Dimensione |
|---|---|---|
| `python-3.13.15-embed-amd64.zip` | python.org/ftp | 11,0 MB |
| `PawnIO_setup_2.2.0.exe` (solo per hash/firma, non incluso) | github.com/namazso/PawnIO.Setup | 3,4 MB |
| Android SDK cmdline-tools 16111833 | dl.google.com | 155 MB → `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest` |
| `platforms;android-21` | sdkmanager | 102 MB |
| `system-images;android-21;default;x86_64`, `…android-34…` | sdkmanager | ~0,4 + 1,6 GB |
Nessuna nuova licenza accettata (quella dell'SDK era già accettata in Android Studio).
AVD create: `t2m_api21` (1280x800), `t2m_api34` (pixel_tablet). Per toglierle: `avdmanager delete avd -n …`.

## 7. Passi successivi
| # | Passo | Stato |
|---|---|---|
| 1 | Utente: decisione chiave (§1.9) | ⏳ |
| 2 | Utente: prova reale di `Setup.cmd` (UAC) su un PC — ideale un **secondo PC** pulito | ⏳ |
| 3 | Prova della scoperta UDP su un tablet reale in Wi‑Fi (es. il GT-P5100 dopo l'unione, o un altro tablet) | ⏳ |
| 4 | Unione nel branch principale: server v0.3.0 da concordare con la sessione «Sorgente dati Windows» (altre versioni del server nel frattempo vanno riportate), APK v0.10.0 con la sessione APK (file comuni: `MainActivity`, `MonitorView`, `SettingsActivity`, `PresenceService`, manifest) | ⏳ |
| 5 | Pagina `/config` bilingue | ⏳ |
| 6 | Firma del pacchetto/script (SmartScreen, "editore sconosciuto") | ⏸️ serve un certificato di firma del codice (a pagamento) |
| 7 | targetSdk recente (niente avviso Android 14) | ⏸️ non richiesto |
| 8 | Inno Setup / MSI | ⏸️ / ⛔ il pacchetto portabile copre la richiesta |

## 8. Rollback
Tutto è nel branch `installer-tab2monitor`: il branch principale non cambia. Per scartarlo:
`git worktree remove ../tab2revamp-installer` e `git branch -D installer-tab2monitor` (anche remoto:
`git push origin --delete installer-tab2monitor`). Sul PC: cache `%LOCALAPPDATA%\Tab2Monitor-build`
cancellabile; AVD `t2m_api21`/`t2m_api34` cancellabili con `avdmanager delete avd`.
