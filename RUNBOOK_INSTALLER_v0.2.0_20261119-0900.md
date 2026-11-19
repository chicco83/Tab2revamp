# RUNBOOK INSTALLER — Tab2Monitor portabile
Versione: v0.2.0 — 2026-11-19 09:00 (ora locale del PC) · deriva da `RUNBOOK_INSTALLER_v0.1.0_20261022-0545.md`
(provenienza; questo file è completo da solo). Scelte e motivi: `PIANO_INSTALLER_v0.3.0_20261119-0900.md`.

## 1. Costruire il pacchetto (PC di sviluppo)
Ingredienti nella cache `%LOCALAPPDATA%\Tab2Monitor-build\cache` (già presenti su questo PC):
`python-3.13.15-embed-amd64.zip` (https://www.python.org/ftp/python/3.13.15/). psutil viene copiato dal
Python del PC (stessa versione 3.13). Tab2Sensors + LibreHardwareMonitorLib dalla cartella
`windows\lhm\LibreHardwareMonitor_v<numero>` (solo release ufficiale, non `-mi50`); se il checkout non la ha,
dal checkout principale `..\tab2revamp`, oppure `-LhmDir <cartella>`.
```
bash android/tab2monitor_app/build_tab2monitor_v0.12.0_20261119-1000.sh
powershell -ExecutionPolicy Bypass -File windows\installer\build_portable_v0.2.0_20261119-0900.ps1
```
Risultato: `%LOCALAPPDATA%\Tab2Monitor-build\dist\Tab2Monitor_v0.2.0.zip` (12,9 MB) con `python\`, `server\`
(server v0.4.4 + supervisore v0.3.0), `web\` (dashboard, pagina config), `sensors\`, `apk\` (APK v0.12.0),
`tools\`, `data\config.json`, `Tab2Monitor.cmd`, `Setup.cmd`, `Install-Tablet.cmd`, README, terze parti.
Lo script prende per ogni file la versione con nome più alto. Fuori da Google Drive apposta (Drive blocca
`python.exe` durante la ricostruzione). L'APK è firmata con la chiave di debug di questo PC (decisione
sulla chiave di release ⏳).

## 2. Usare il pacchetto (altro PC)
1. Estrarre lo zip, doppio clic su `Tab2Monitor.cmd` (UAC: sì = tutti i sensori).
2. Tablet (Android 7.1+): browser → `http://<IP-PC>:8090/apk`, oppure cavo USB + `Install-Tablet.cmd`.
3. L'app cerca il PC da sola (UDP 8091); impostazioni dell'app → "Cerca il PC in rete" o indirizzo a mano.
4. Facoltativo `Setup.cmd`: 1 installa in Program Files + avvio automatico (4 modi), 2 avvio all'accesso
   senza admin dalla cartella portabile, 3 firewall (TCP 8090 + UDP 8091, rete privata), 4 PawnIO, 5 tablet,
   6 disinstalla.
Dati: copia portabile `data\`; copia installata `%ProgramData%\Tab2Monitor` (`config.json`, `fan_config.json`,
`logs\server.log`, `logs\tab2monitor_service.log`). Chiavi di `config.json`: `port`, `bind`, `gpu`, `claude`
(false spegne anche i limiti del piano), `claude_dir`, `adb` (`auto`/`off`/percorso), `sensors`,
`discovery`, `name` (nome mostrato al tablet nella ricerca).

Script senza domande (prove, distribuzione):
```
powershell -ExecutionPolicy Bypass -File tools\setup.ps1 -Action Status
powershell -ExecutionPolicy Bypass -File tools\setup.ps1 -Action Install -Autostart Logon -Yes [-DryRun] [-TaskName <nome>]
powershell -ExecutionPolicy Bypass -File tools\setup.ps1 -Action Uninstall -Yes [-KeepData]
powershell -ExecutionPolicy Bypass -File tools\install_tablet.ps1 -Serial <id> -Yes [-SetHome] [-Admin] [-Url http://..]
```
Controlli dopo il Setup: `Get-NetFirewallRule -Group Tab2Monitor` (2 regole: TCP 8090, UDP 8091);
`(Get-ScheduledTask -TaskName Tab2Monitor).Settings.Priority` = 4; ricerca dal PC:
```
powershell -Command "$u=New-Object Net.Sockets.UdpClient;$u.Client.ReceiveTimeout=2000;$b=[Text.Encoding]::ASCII.GetBytes('TAB2MON?');[void]$u.Send($b,8,'127.0.0.1',8091);$e=New-Object Net.IPEndPoint([Net.IPAddress]::Any,0);[Text.Encoding]::UTF8.GetString($u.Receive([ref]$e))"
```

## 3. Provare senza toccare l'installazione reale (questo PC)
- **Mai** l'attività `Tab2Monitor` (è quella in uso): sempre `-TaskName Tab2MonitorTest`.
- **Porta e servizi**: nel `data\config.json` della copia di prova `"port": 8190, "adb": "off", "discovery": false`.
  `adb` spento, altrimenti il server rifà `adb reverse` anche sul tablet reale (successo il 2026-10-22);
  `discovery` spento perché la UDP 8091 è già del server vero.
- Avvio della copia senza UAC: `python\python.exe server\tab2monitor_server_v0.4.4_*.py --data data`.
- **Emulatori** (`%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe`): `t2m_api34` (Android 14) va bene per
  l'APK v0.12.0; `t2m_api21` (Android 5.0) non più (minSdk 25).
  ```
  emulator -avd t2m_api34 -port 5562 -no-snapshot -no-audio -gpu host
  ```
  Comandi adb sempre con `-s emulator-5562`. Dall'emulatore il PC è `10.0.2.2`; `127.0.0.1:8090` arriva al
  server reale perché il servizio fa `adb reverse` su ogni dispositivo. Negli emulatori (NAT) la ricerca
  UDP non trova nulla: è atteso.

## 4. Problemi noti e soluzioni
| Sintomo | Rimedio |
|---|---|
| Il tablet non trova il PC | rete Windows "Pubblica" → Setup voce 3 (rete privata + regole TCP 8090 / UDP 8091); router con isolamento client / rete ospiti → indirizzo a mano |
| "Questa versione di Android non permette di attivare la Home" (Android 11+) | limite dell'APK v0.12.0 (HomeAlias acceso solo con `pm enable`): aprire l'app a mano dopo un riavvio; soluzione futura: Home attivata dall'app (piano §7.4) |
| Dopo il riavvio la Home non è Tab2Monitor | riavviato troppo presto: aspettare 30 s dopo la scelta della Home |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | APK firmata con un'altra chiave: disinstallare (prima `dpm remove-active-admin`) o usare la stessa chiave |
| "Serve Android 7.1 o successivo" | APK v0.12.0 ha minSdk 25 |
| Script PowerShell bloccato su un comando adb | stderr di adb + `ErrorActionPreference=Stop` (PowerShell 5.1): usare le funzioni `A`/`AS` dello script |
| adb non risponde (`adb devices` fermo) | chiudere i client `adb.exe` appesi (non il server `fork-server`), poi riprovare |
| "ricerca UDP: errore, riprovo tra 5 s" nel log | un altro server tiene la UDP 8091 (es. copia di prova sullo stesso PC): innocuo |
| "accesso negato" ricostruendo il pacchetto | non costruire dentro Google Drive (default già in `%LOCALAPPDATA%`) |
| Avviso "app per versione precedente di Android" (Android 14) | atteso con targetSdk 25: OK |
| Server lento/bloccato con un altro programma al 100% di CPU | priorità dell'attività 7: il Setup v0.2.0 mette 4; su un'attività vecchia `$s=(Get-ScheduledTask -TaskName Tab2Monitor).Settings; $s.Priority=4; Set-ScheduledTask -TaskName Tab2Monitor -Settings $s` (admin) |

## Storico modifiche
| Data | Prima | Ora | Perché |
|---|---|---|---|
| 2026-11-19 (v0.2.0) | build APK `build_tab2monitor_v0.10.0_20261022-0230.sh` (con `API_CHECK=1` e firma `T2M_*`), pacchetto 0.1.0, Android 5.0+, ricerca UDP 8090, prove anche su emulatore API 21, Home con `--ez home true` | build APK v0.12.0 del branch principale, pacchetto 0.2.0, Android 7.1+, UDP 8091, solo emulatore API 34, Home con `pm enable` e avviso; nuovi comandi di controllo (firewall, priorità, ricerca); sensori solo da release ufficiale | allineamento al protocollo di ricerca del branch principale (richiesta sessione APK) |
| 2026-10-22 (v0.1.0) | — | prima stesura | pacchetto 0.1.0 |
