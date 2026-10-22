# RUNBOOK INSTALLER — Tab2Monitor portabile
Versione: v0.1.0 — 2026-10-22 05:45 (ora locale del PC) · scelte e motivi: `PIANO_INSTALLER_v0.2.0_20261022-0545.md`

## 1. Costruire il pacchetto (PC di sviluppo)
Ingredienti nella cache `%LOCALAPPDATA%\Tab2Monitor-build\cache` (già presenti su questo PC):
`python-3.13.15-embed-amd64.zip` (https://www.python.org/ftp/python/3.13.15/). psutil viene copiato
dal Python del PC (stessa versione 3.13). Tab2Sensors + LHM da `windows\lhm\LibreHardwareMonitor_v*`
(se il checkout non li ha, dal checkout principale `..\tab2revamp`, oppure `-LhmDir`).
```
bash android/tab2monitor_app/build_tab2monitor_v0.10.0_20261022-0230.sh
API_CHECK=1 bash android/tab2monitor_app/build_tab2monitor_v0.10.0_20261022-0230.sh   # controllo API 21
powershell -ExecutionPolicy Bypass -File windows\installer\build_portable_v0.1.0_20261022-0300.ps1
```
Risultato: `%LOCALAPPDATA%\Tab2Monitor-build\dist\Tab2Monitor_v0.1.0.zip` (~13 MB). Fuori da Google
Drive apposta (Drive blocca `python.exe` durante la ricostruzione).
Firma di release dell'APK (quando l'utente avrà deciso, chiave fuori dal repo):
`T2M_KEYSTORE=... T2M_KEY_ALIAS=... T2M_KS_PASS=... bash android/tab2monitor_app/build_tab2monitor_v0.10.0_*.sh`

## 2. Usare il pacchetto (altro PC)
1. Estrarre lo zip, doppio clic su `Tab2Monitor.cmd` (UAC: sì = tutti i sensori).
2. Tablet: browser → `http://<IP-PC>:8090/apk`, oppure cavo USB + `Install-Tablet.cmd`.
3. Facoltativo `Setup.cmd`: 1 installa in Program Files + avvio automatico (4 modi), 2 avvio all'accesso
   senza admin dalla cartella portabile, 3 firewall, 4 PawnIO, 5 tablet, 6 disinstalla.
Dati: copia portabile `data\`; copia installata `%ProgramData%\Tab2Monitor` (`config.json`,
`fan_config.json`, `logs\`). Chiavi di `config.json`: `port`, `bind`, `gpu`, `claude`, `claude_dir`,
`adb` (`auto`/`off`/percorso), `sensors`, `discovery`, `name`.

Script senza domande (prove, distribuzione):
```
powershell -ExecutionPolicy Bypass -File tools\setup.ps1 -Action Status
powershell -ExecutionPolicy Bypass -File tools\setup.ps1 -Action Install -Autostart Logon -Yes [-DryRun]
powershell -ExecutionPolicy Bypass -File tools\setup.ps1 -Action Uninstall -Yes [-KeepData]
powershell -ExecutionPolicy Bypass -File tools\install_tablet.ps1 -Serial <id> -Yes [-SetHome] [-Admin] [-Url http://..]
```

## 3. Provare senza toccare l'installazione reale (questo PC)
- **Porta diversa**: nel `data\config.json` della copia di prova `"port": 8190, "adb": "off"` — `adb`
  spento, altrimenti il server rifà `adb reverse` anche sul tablet reale (successo il 2026-10-22).
- **Attività di prova**: `-TaskName Tab2MonitorTest` (mai `Tab2Monitor`, che è quella in uso).
- **Emulatori** (`%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe`):
  ```
  emulator -avd t2m_api21 -port 5560 -no-snapshot -no-audio -gpu host      # Android 5.0, 1280x800
  emulator -avd t2m_api34 -port 5562 -no-snapshot -no-audio -gpu host      # Android 14, 2560x1600
  ```
  Comandi adb sempre con `-s emulator-5560` / `-s emulator-5562`. Dall'emulatore il PC è `10.0.2.2`;
  `127.0.0.1:8090` arriva al server reale perché il servizio fa `adb reverse` su ogni dispositivo.
  Screenshot: `cmd /c "adb -s emulator-5560 exec-out screencap -p > shot.png"`.

## 4. Problemi noti e soluzioni
| Sintomo | Rimedio |
|---|---|
| Il tablet non trova il PC | rete Windows "Pubblica" → Setup voce 3 (rete privata + regola); router con isolamento client/rete ospiti → indirizzo a mano |
| "Shell cannot change component state" (Android 11+) | non usare `pm enable` per la Home: `am start … --ez home true` e poi `cmd role add-role-holder android.app.role.HOME it.tab2revamp.monitor` |
| Dopo il riavvio la Home non è Tab2Monitor | riavviato troppo presto: aspettare 30 s dopo la scelta della Home |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | APK firmata con un'altra chiave: disinstallare (prima `dpm remove-active-admin`) o usare la stessa chiave |
| Script PowerShell bloccato su un comando adb | stderr di adb + `ErrorActionPreference=Stop` (PowerShell 5.1): usare le funzioni `A`/`AS` dello script |
| adb non risponde (`adb devices` fermo) | chiudere i client `adb.exe` appesi (non il server `fork-server`), poi riprovare |
| Emulatore API 21 fermo all'avvio | `-gpu host` invece di `swiftshader_indirect` |
| "accesso negato" ricostruendo il pacchetto | non costruire dentro Google Drive (default già in `%LOCALAPPDATA%`) |
| Avviso "app per versione precedente di Android" (Android 14) | atteso con targetSdk 25: OK |
